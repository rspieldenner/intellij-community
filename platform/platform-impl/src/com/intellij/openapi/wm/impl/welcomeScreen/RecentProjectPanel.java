/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class RecentProjectPanel extends JPanel {
  protected final JBList myList;
  protected final UniqueNameBuilder<ReopenProjectAction> myPathShortener;
  protected AnAction removeRecentProjectAction;
  private int myHoverIndex = -1;
  private final int closeButtonInset = JBUI.scale(7);
  private Icon currentIcon = AllIcons.Welcome.RemoveRecentProject;

  private final JPanel myCloseButtonForEditor = new JPanel() {
    {
      setPreferredSize(new Dimension(currentIcon.getIconWidth(), currentIcon.getIconHeight()));
      setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
      currentIcon.paintIcon(this, g, 0, 0);
    }
  };


  private boolean rectInListCoordinatesContains(Rectangle listCellBounds,  Point p) {

    int realCloseButtonInset = (UIUtil.isRetina(myList.getGraphicsConfiguration().getDevice())) ?
                           closeButtonInset * 2 : closeButtonInset;

    Rectangle closeButtonRect = new Rectangle(myCloseButtonForEditor.getX() - realCloseButtonInset,
                                              myCloseButtonForEditor.getY() - realCloseButtonInset,
                                              myCloseButtonForEditor.getWidth() + realCloseButtonInset * 2,
                                              myCloseButtonForEditor.getHeight() + realCloseButtonInset * 2);

    Rectangle rectInListCoordinates = new Rectangle(new Point(closeButtonRect.x + listCellBounds.x,
                                                              closeButtonRect.y + listCellBounds.y),
                                                    closeButtonRect.getSize());
    return rectInListCoordinates.contains(p);
  }

  public RecentProjectPanel(WelcomeScreen screen) {
    super(new BorderLayout());

    final AnAction[] recentProjectActions = RecentProjectsManager.getInstance().getRecentProjectsActions(false);

    myPathShortener = new UniqueNameBuilder<ReopenProjectAction>(SystemProperties.getUserHome(), File.separator, 40);
    for (AnAction action : recentProjectActions) {
      ReopenProjectAction item = (ReopenProjectAction)action;
      myPathShortener.addPath(item, item.getProjectPath());
    }

    myList = createList(recentProjectActions, getPreferredScrollableViewportSize());
    myList.setCellRenderer(createRenderer(myPathShortener));

    new ClickListener(){
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          Rectangle cellBounds = myList.getCellBounds(selectedIndex, selectedIndex);
          if (cellBounds.contains(event.getPoint())) {
            Object selection = myList.getSelectedValue();
            if (Registry.is("removable.welcome.screen.projects") && rectInListCoordinatesContains(cellBounds, event.getPoint())) {
              removeRecentProjectAction.actionPerformed(null);
            } else if (selection != null) {
              ((AnAction)selection).actionPerformed(
                AnActionEvent.createFromInputEvent((AnAction)selection, event, ActionPlaces.WELCOME_SCREEN));
            }
          }
        }

        return true;
      }
    }.installOn(myList);

    myList.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selection = myList.getSelectedValue();

        if (selection != null) {
          ((AnAction)selection).actionPerformed(AnActionEvent.createFromInputEvent((AnAction)selection, null, ActionPlaces.WELCOME_SCREEN));
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


    removeRecentProjectAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Object[] selection = myList.getSelectedValues();

        if (selection != null && selection.length > 0) {
          final int rc = Messages.showOkCancelDialog(RecentProjectPanel.this,
                                                     "Remove '" + StringUtil.join(selection, new Function<Object, String>() {
                                                       @Override
                                                       public String fun(Object action) {
                                                         return ((ReopenProjectAction)action).getTemplatePresentation().getText();
                                                       }
                                                     }, "'\n'") +
                                                     "' from recent projects list?",
                                                     "Remove Recent Project",
                                                     Messages.getQuestionIcon());
          if (rc == Messages.OK) {
            RecentProjectsManager manager = RecentProjectsManagerBase.getInstance();
            for (Object projectAction : selection) {
              manager.removePath(((ReopenProjectAction)projectAction).getProjectPath());
            }
            ListUtil.removeSelectedItems(myList);
          }
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!ListWithFilter.isSearchActive(myList));
      }
    };
    removeRecentProjectAction.registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), myList, screen);

    addMouseMotionListener();

    myList.setSelectedIndex(0);

    JBScrollPane scroll = new JBScrollPane(myList);
    scroll.setBorder(null);

    JComponent list = recentProjectActions.length == 0
                      ? myList
                      : ListWithFilter.wrap(myList, scroll, new Function<Object, String>() {
                        @Override
                        public String fun(Object o) {
                          ReopenProjectAction item = (ReopenProjectAction)o;
                          String home = SystemProperties.getUserHome();
                          String path = item.getProjectPath();
                          if (FileUtil.startsWith(path, home)) {
                            path = path.substring(home.length());
                          }
                          return item.getProjectName() + " " + path;
                        }
                      });
    add(list, BorderLayout.CENTER);

    JPanel title = createTitle();

    if (title != null) {
      add(title, BorderLayout.NORTH);
    }

    setBorder(new LineBorder(WelcomeScreenColors.BORDER_COLOR));
  }

  protected Dimension getPreferredScrollableViewportSize() {
    return JBUI.size(250, 400);
  }
  
  protected void addMouseMotionListener() {

    MouseAdapter mouseAdapter = new MouseAdapter() {
      boolean myIsEngaged = false;
      @Override
      public void mouseMoved(MouseEvent e) {
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e)) {
          Point point = e.getPoint();
          int index = myList.locationToIndex(point);
          myList.setSelectedIndex(index);

          final Rectangle cellBounds = myList.getCellBounds(index, index);
          if (cellBounds != null && cellBounds.contains(point)) {
            myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (rectInListCoordinatesContains(cellBounds, point)) {
              currentIcon = AllIcons.Welcome.RemoveRecentProjectHover;
            } else {
              currentIcon = AllIcons.Welcome.RemoveRecentProject;
            }
            myHoverIndex = index;
            myList.repaint(cellBounds);
          }
          else {
            myList.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            myHoverIndex = -1;
            myList.repaint();
          }
        }
        else {
          myIsEngaged = true;
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myHoverIndex = -1;
        currentIcon = AllIcons.Welcome.RemoveRecentProject;
        myList.repaint();
      }
    };

    myList.addMouseMotionListener(mouseAdapter);
    myList.addMouseListener(mouseAdapter);

  }

  protected JBList createList(AnAction[] recentProjectActions, Dimension size) {
    return new MyList(size, recentProjectActions);
  }

  protected ListCellRenderer createRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
    return new RecentProjectItemRenderer(pathShortener);
  }

  @Nullable
  protected JPanel createTitle() {
    JPanel title = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, JBUI.scale(28));
      }
    };
    title.setBorder(new BottomLineBorder());

    JLabel titleLabel = new JLabel("Recent Projects");
    title.add(titleLabel);
    titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
    titleLabel.setForeground(WelcomeScreenColors.CAPTION_FOREGROUND);
    title.setBackground(WelcomeScreenColors.CAPTION_BACKGROUND);
    return title;
  }

  private static class MyList extends JBList {
    private final Dimension mySize;

    private MyList(Dimension size, @NotNull Object ... listData) {
      super(listData);
      mySize = size;
      setEmptyText("  No Project Open Yet  ");
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return mySize == null ? super.getPreferredScrollableViewportSize() : mySize;
    }
  }

  protected class RecentProjectItemRenderer extends JPanel implements ListCellRenderer {

    protected final JLabel myName = new JLabel();
    protected final JLabel myPath = new JLabel();
    protected boolean myHovered;
    protected JPanel myCloseThisItem = myCloseButtonForEditor;

    private final UniqueNameBuilder<ReopenProjectAction> myShortener;

    protected RecentProjectItemRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
      super(new VerticalFlowLayout());
      myShortener = pathShortener;
      myPath.setFont(JBUI.Fonts.label(SystemInfo.isMac ? 10f : 11f));
      setFocusable(true);
      layoutComponents();
    }

    protected void layoutComponents() {
      add(myName);
      add(myPath);
    }

    protected Color getListBackground(boolean isSelected, boolean hasFocus) {
      return UIUtil.getListBackground(isSelected);
    }

    protected Color getListForeground(boolean isSelected, boolean hasFocus) {
      return UIUtil.getListForeground(isSelected);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myHovered = myHoverIndex == index;

      ReopenProjectAction item = (ReopenProjectAction)value;

      Color fore = getListForeground(isSelected, list.hasFocus());
      Color back = getListBackground(isSelected, list.hasFocus());

      myName.setForeground(fore);
      myPath.setForeground(isSelected ? fore : UIUtil.getInactiveTextColor());

      setBackground(back);

      myName.setText(item.getProjectName());
      myPath.setText(getTitle2Text(item, myPath));

      return this;
    }

    private String getTitle2Text(ReopenProjectAction action, JComponent pathLabel) {
      String fullText = action.getProjectPath();
      if (fullText == null || fullText.length() == 0) return " ";

      fullText = FileUtil.getLocationRelativeToUserHome(fullText, false);

      int labelWidth = pathLabel.getWidth();
      if (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
        return myShortener.getShortPath(action);
      }

      return fullText;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.min(size.width, JBUI.scale(245)), size.height);
    }
  }
}
