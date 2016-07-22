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
import com.intellij.ide.ProjectGroup;
import com.intellij.ide.ProjectGroupActionGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class RecentProjectPanel extends JPanel {
  public static final String RECENT_PROJECTS_LABEL = "Recent Projects";
  protected final JBList myList;
  protected final UniqueNameBuilder<ReopenProjectAction> myPathShortener;
  protected AnAction removeRecentProjectAction;
  private int myHoverIndex = -1;
  private final int closeButtonInset = JBUI.scale(7);
  private Icon currentIcon = AllIcons.Welcome.Project.Remove;
  private static final Logger LOG = Logger.getInstance("#" + RecentProjectPanel.class.getName());

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

    int realCloseButtonInset = (UIUtil.isRetina((Graphics2D)myList.getGraphics())) ?
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

  public RecentProjectPanel(@Nullable Disposable parentDisposable) {
    super(new BorderLayout());

    final AnAction[] recentProjectActions = RecentProjectsManager.getInstance().getRecentProjectsActions(false, isUseGroups());

    myPathShortener = new UniqueNameBuilder<>(SystemProperties.getUserHome(), File.separator, 40);
    for (AnAction action : recentProjectActions) {
      if (action instanceof ReopenProjectAction) {
        final ReopenProjectAction item = (ReopenProjectAction)action;

        myPathShortener.addPath(item, item.getProjectPath());
      }
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
        final Object[] selectedValued = myList.getSelectedValues();
        if (selectedValued != null) {
          for (Object selection : selectedValued) {
            AnActionEvent event = AnActionEvent.createFromInputEvent((AnAction)selection, null, ActionPlaces.WELCOME_SCREEN);
            ((AnAction)selection).actionPerformed(event);
          }
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


    removeRecentProjectAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Object[] selection = myList.getSelectedValues();

        if (selection != null && selection.length > 0) {
          final int rc = Messages.showOkCancelDialog(RecentProjectPanel.this,
                                                     "Remove '" + StringUtil.join(selection, action -> ((AnAction)action).getTemplatePresentation().getText(), "'\n'") +
                                                     "' from recent projects list?",
                                                     "Remove Recent Project",
                                                     Messages.getQuestionIcon());
          if (rc == Messages.OK) {
            for (Object projectAction : selection) {
              removeRecentProjectElement(projectAction);
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
    removeRecentProjectAction.registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), myList, parentDisposable);

    addMouseMotionListener();

    myList.setSelectedIndex(0);

    JBScrollPane scroll = new JBScrollPane(myList);
    scroll.setBorder(null);

    JComponent list = recentProjectActions.length == 0
                      ? myList
                      : ListWithFilter.wrap(myList, scroll, o -> {
                        if (o instanceof ReopenProjectAction) {
                          ReopenProjectAction item = (ReopenProjectAction)o;
                          String home = SystemProperties.getUserHome();
                          String path = item.getProjectPath();
                          if (FileUtil.startsWith(path, home)) {
                            path = path.substring(home.length());
                          }
                          return item.getProjectName() + " " + path;
                        } else if (o instanceof ProjectGroupActionGroup) {
                          return ((ProjectGroupActionGroup)o).getGroup().getName();
                        }
                        return o.toString();
                      });
    add(list, BorderLayout.CENTER);

    JPanel title = createTitle();

    if (title != null) {
      add(title, BorderLayout.NORTH);
    }

    setBorder(new LineBorder(WelcomeScreenColors.BORDER_COLOR));
  }

  protected static void removeRecentProjectElement(Object element) {
    final RecentProjectsManager manager = RecentProjectsManager.getInstance();
    if (element instanceof ReopenProjectAction) {
      manager.removePath(((ReopenProjectAction)element).getProjectPath());
    } else if (element instanceof ProjectGroupActionGroup) {
      final ProjectGroup group = ((ProjectGroupActionGroup)element).getGroup();
      for (String path : group.getProjects()) {
        manager.removePath(path);
      }
      manager.removeGroup(group);
    }
  }

  protected boolean isUseGroups() {
    return false;
  }

  protected Dimension getPreferredScrollableViewportSize() {
    return JBUI.size(250, 400);
  }
  
  protected void addMouseMotionListener() {

    MouseAdapter mouseAdapter = new MouseAdapter() {
      boolean myIsEngaged = false;
      @Override
      public void mouseMoved(MouseEvent e) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
          myList.requestFocus();
        }
        if (myList.getSelectedIndices().length > 1) {
          return;
        }
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e) && !(focusOwner instanceof JRootPane)) {
          Point point = e.getPoint();
          int index = myList.locationToIndex(point);
          myList.setSelectedIndex(index);

          final Rectangle cellBounds = myList.getCellBounds(index, index);
          if (cellBounds != null && cellBounds.contains(point)) {
            myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (rectInListCoordinatesContains(cellBounds, point)) {
              currentIcon = AllIcons.Welcome.Project.Remove_hover;
            } else {
              currentIcon = AllIcons.Welcome.Project.Remove;
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
        currentIcon = AllIcons.Welcome.Project.Remove;
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

    JLabel titleLabel = new JLabel(RECENT_PROJECTS_LABEL);
    title.add(titleLabel);
    titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
    titleLabel.setForeground(WelcomeScreenColors.CAPTION_FOREGROUND);
    title.setBackground(WelcomeScreenColors.CAPTION_BACKGROUND);
    return title;
  }

  private static class MyList extends JBList {
    private final Dimension mySize;
    private Point myMousePoint;

    private MyList(Dimension size, @NotNull Object ... listData) {
      super(listData);
      mySize = size;
      setEmptyText("  No Project Open Yet  ");
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      getAccessibleContext().setAccessibleName(RECENT_PROJECTS_LABEL);
      final MouseHandler handler = new MouseHandler();
      addMouseListener(handler);
      addMouseMotionListener(handler);
    }

    public Rectangle getCloseIconRect(int index) {
      final Rectangle bounds = getCellBounds(index, index);
      Icon icon = AllIcons.Welcome.Project.Remove;
      return new Rectangle(bounds.width - icon.getIconWidth() - 10, bounds.y + 10, icon.getIconWidth(), icon.getIconHeight());
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (myMousePoint != null) {
        final int index = locationToIndex(myMousePoint);
        if (index != -1) {
          final Rectangle iconRect = getCloseIconRect(index);
          Icon icon = iconRect.contains(myMousePoint) ? AllIcons.Welcome.Project.Remove_hover : AllIcons.Welcome.Project.Remove;
          icon.paintIcon(this, g, iconRect.x, iconRect.y);
        }
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return mySize == null ? super.getPreferredScrollableViewportSize() : mySize;
    }

    class MouseHandler extends MouseAdapter {
      @Override
      public void mouseEntered(MouseEvent e) {
        myMousePoint = e.getPoint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myMousePoint = null;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        myMousePoint = e.getPoint();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        final Point point = e.getPoint();
        final MyList list = MyList.this;
        final int index = list.locationToIndex(point);
        if (index != -1) {
          if (getCloseIconRect(index).contains(point)) {
            e.consume();
            final Object element = getModel().getElementAt(index);
            removeRecentProjectElement(element);
            ListUtil.removeSelectedItems(MyList.this);
          }
        }
      }
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
      Color fore = getListForeground(isSelected, list.hasFocus());
      Color back = getListBackground(isSelected, list.hasFocus());

      myName.setForeground(fore);
      myPath.setForeground(isSelected ? fore : UIUtil.getInactiveTextColor());

      setBackground(back);

      if (value instanceof ReopenProjectAction) {
        ReopenProjectAction item = (ReopenProjectAction)value;
        myName.setText(item.getTemplatePresentation().getText());
        myPath.setText(getTitle2Text(item, myPath, JBUI.scale(40)));
      } else if (value instanceof ProjectGroupActionGroup) {
        final ProjectGroupActionGroup group = (ProjectGroupActionGroup)value;
        myName.setText(group.getGroup().getName());
        myPath.setText("");
      }
      AccessibleContextUtil.setCombinedName(this, myName, " - ", myPath);
      AccessibleContextUtil.setCombinedDescription(this, myName, " - ", myPath);
      return this;
    }

    protected String getTitle2Text(ReopenProjectAction action, JComponent pathLabel, int leftOffset) {
      String fullText = action.getProjectPath();
      if (fullText == null || fullText.length() == 0) return " ";

      fullText = FileUtil.getLocationRelativeToUserHome(fullText, false);

      try {
        FontMetrics fm = pathLabel.getFontMetrics(pathLabel.getFont());
        int maxWidth = RecentProjectPanel.this.getWidth() - leftOffset;
        if (maxWidth > 0 && fm.stringWidth(fullText) > maxWidth) {
          int left = 1; int right = 1;
          int center = fullText.length() / 2;
          String s = fullText.substring(0, center - left) + "..." + fullText.substring(center + right);
          while (fm.stringWidth(s) > maxWidth) {
            if (left == right) {
              left++;
            } else {
              right++;
            }

            if (center - left < 0 || center + right >= fullText.length()) {
              return "";
            }
            s = fullText.substring(0, center - left) + "..." + fullText.substring(center + right);
          }
          return s;
        }
      } catch (Exception e) {
        LOG.error("Path label font: " + pathLabel.getFont());
        LOG.error("Panel width: " + RecentProjectPanel.this.getWidth());
        LOG.error(e);
      }

      return fullText;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.min(size.width, JBUI.scale(245)), size.height);
    }

    @NotNull
    @Override
    public Dimension getSize() {
      return getPreferredSize();
    }
  }
}
