/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.IconUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class NewRecentProjectPanel extends RecentProjectPanel {
  public NewRecentProjectPanel(@NotNull Disposable parentDisposable) {
    super(parentDisposable);
    setBorder(null);
    setBackground(FlatWelcomeFrame.getProjectsBackground());
    JScrollPane scrollPane = UIUtil.findComponentOfType(this, JScrollPane.class);
    if (scrollPane != null) {
      scrollPane.setBackground(FlatWelcomeFrame.getProjectsBackground());
      JBDimension size = JBUI.size(300, 460);
      scrollPane.setSize(size);
      scrollPane.setMinimumSize(size);
      scrollPane.setPreferredSize(size);
    }
    ListWithFilter panel = UIUtil.findComponentOfType(this, ListWithFilter.class);
    if (panel != null) {
      panel.setBackground(FlatWelcomeFrame.getProjectsBackground());
    }
  }

  protected Dimension getPreferredScrollableViewportSize() {
    return null;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    final JList list = UIUtil.findComponentOfType(this, JList.class);
    if (list != null) {
      list.updateUI();
    }
  }

  @Override
  protected JBList createList(AnAction[] recentProjectActions, Dimension size) {
    final JBList list = super.createList(recentProjectActions, size);

    list.setBackground(FlatWelcomeFrame.getProjectsBackground());
    list.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        Object selected = list.getSelectedValue();
        final ProjectGroup group;
        if (selected instanceof ProjectGroupActionGroup) {
          group = ((ProjectGroupActionGroup)selected).getGroup();
        } else {
          group = null;
        }

        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_RIGHT) {
          if (group != null) {
            if (!group.isExpanded()) {
              group.setExpanded(true);
              ListModel model = ((NameFilteringListModel)list.getModel()).getOriginalModel();
              int index = list.getSelectedIndex();
              RecentProjectsWelcomeScreenActionBase.rebuildRecentProjectDataModel((DefaultListModel)model);
              list.setSelectedIndex(group.getProjects().isEmpty() ? index : index + 1);
            }
          } else {
            FlatWelcomeFrame frame = UIUtil.getParentOfType(FlatWelcomeFrame.class, list);
            if (frame != null) {
              FocusTraversalPolicy policy = frame.getFocusTraversalPolicy();
              if (policy != null) {
                Component next = policy.getComponentAfter(frame, list);
                if (next != null) {
                  IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
                    IdeFocusManager.getGlobalInstance().requestFocus(next, true);
                  });
                }
              }
            }
          }
        } else if (keyCode == KeyEvent.VK_LEFT ) {
          if (group != null && group.isExpanded()) {
            group.setExpanded(false);
            int index = list.getSelectedIndex();
            ListModel model = ((NameFilteringListModel)list.getModel()).getOriginalModel();
            RecentProjectsWelcomeScreenActionBase.rebuildRecentProjectDataModel((DefaultListModel)model);
            list.setSelectedIndex(index);
          }
        }
      }
    });
    list.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final int index = list.locationToIndex(new Point(x, y));
        if (index != -1 && Arrays.binarySearch(list.getSelectedIndices(), index) < 0) {
          list.setSelectedIndex(index);
        }
        final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("WelcomeScreenRecentProjectActionGroup");
        if (group != null) {
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, group).getComponent().show(comp, x, y);
        }
      }
    });
    return list;
  }

  @Override
  protected boolean isUseGroups() {
    return FlatWelcomeFrame.isUseProjectGroups();
  }

  @Override
  protected ListCellRenderer createRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
    return new RecentProjectItemRenderer(myPathShortener) {
       private GridBagConstraints nameCell;
       private GridBagConstraints pathCell;
       private GridBagConstraints closeButtonCell;

      private void initConstraints () {
        nameCell = new GridBagConstraints();
        pathCell = new GridBagConstraints();
        closeButtonCell = new GridBagConstraints();

        nameCell.gridx = 0;
        nameCell.gridy = 0;
        nameCell.weightx = 1.0;
        nameCell.weighty = 1.0;
        nameCell.anchor = GridBagConstraints.FIRST_LINE_START;
        nameCell.insets = JBUI.insets(6, 5, 1, 5);



        pathCell.gridx = 0;
        pathCell.gridy = 1;

        pathCell.insets = JBUI.insets(1, 5, 6, 5);
        pathCell.anchor = GridBagConstraints.LAST_LINE_START;


        closeButtonCell.gridx = 1;
        closeButtonCell.gridy = 0;
        closeButtonCell.anchor = GridBagConstraints.FIRST_LINE_END;
        closeButtonCell.insets = JBUI.insets(7, 7, 7, 7);
        closeButtonCell.gridheight = 2;

        //closeButtonCell.anchor = GridBagConstraints.WEST;
      }

      @Override
      protected Color getListBackground(boolean isSelected, boolean hasFocus) {
        return isSelected ? FlatWelcomeFrame.getListSelectionColor(hasFocus) : FlatWelcomeFrame.getProjectsBackground();
      }

      @Override
      protected Color getListForeground(boolean isSelected, boolean hasFocus) {
        return UIUtil.getListForeground(isSelected && hasFocus);
      }

      @Override
      protected void layoutComponents() {
        setLayout(new GridBagLayout());
        initConstraints();
        add(myName, nameCell);
        add(myPath, pathCell);
      }
      JComponent spacer = new NonOpaquePanel() {
        @Override
        public Dimension getPreferredSize() {
          return new Dimension(JBUI.scale(22), super.getPreferredSize().height);
        }
      };

      @Override
      public Component getListCellRendererComponent(JList list, final Object value, int index, final boolean isSelected, boolean cellHasFocus) {
        final Color fore = getListForeground(isSelected, list.hasFocus());
        final Color back = getListBackground(isSelected, list.hasFocus());
        final JLabel name = new JLabel();
        final JLabel path = new JLabel();
        name.setForeground(fore);
        path.setForeground(isSelected ? fore : UIUtil.getInactiveTextColor());

        setBackground(back);

        return new JPanel() {
          {
            setLayout(new BorderLayout());
            setBackground(back);

            boolean isGroup = value instanceof ProjectGroupActionGroup;
            boolean isInsideGroup = false;
            if (value instanceof ReopenProjectAction) {
              final String path = ((ReopenProjectAction)value).getProjectPath();
              for (ProjectGroup group : RecentProjectsManager.getInstance().getGroups()) {
                final List<String> projects = group.getProjects();
                if (projects.contains(path)) {
                  isInsideGroup = true;
                  break;
                }
              }
            }

            setBorder(JBUI.Borders.empty(5, 7));
            if (isInsideGroup) {
              add(spacer, BorderLayout.WEST);
            }
            if (isGroup) {
              final ProjectGroup group = ((ProjectGroupActionGroup)value).getGroup();
              name.setText(" " + group.getName());
              name.setIcon(IconUtil.toSize(group.isExpanded() ? UIUtil.getTreeExpandedIcon() : UIUtil.getTreeCollapsedIcon(), JBUI.scale(16), JBUI.scale(16)));
              name.setFont(name.getFont().deriveFont(Font.BOLD));
              add(name);
            } else if (value instanceof ReopenProjectAction) {
              final NonOpaquePanel p = new NonOpaquePanel(new BorderLayout());
              name.setText(((ReopenProjectAction)value).getProjectName());
              if (!isSelected && !isPathValid((((ReopenProjectAction)value).getProjectPath()))) {
                name.setForeground(UIUtil.getInactiveTextColor());
              }
              final String realPath = PathUtil.toSystemDependentName(((ReopenProjectAction)value).getProjectPath());
              path.setText(getTitle2Text((ReopenProjectAction)value, path, JBUI.scale(isInsideGroup ? 80 : 60)));
              if (!realPath.equals(path.getText())) {
                projectsWithLongPathes.add((ReopenProjectAction)value);
              }
              p.add(name, BorderLayout.NORTH);
              p.add(path, BorderLayout.SOUTH);

              String projectPath = ((ReopenProjectAction)value).getProjectPath();
              Icon icon = RecentProjectsManagerBase.getProjectIcon(projectPath, UIUtil.isUnderDarcula());
              if (icon == null) {
                if (UIUtil.isUnderDarcula()) {
                  //No dark icon for this project
                  icon = RecentProjectsManagerBase.getProjectIcon(projectPath, false);
                }
              }
              if (icon == null) {
                icon = EmptyIcon.ICON_16;
              }

              final JLabel projectIcon = new JLabel("", icon, SwingConstants.LEFT) {
                @Override
                protected void paintComponent(Graphics g) {
                  getIcon().paintIcon(this, g, 0, (getHeight() - getIcon().getIconHeight()) / 2);
                }
              };
              projectIcon.setBorder(JBUI.Borders.emptyRight(8));
              projectIcon.setVerticalAlignment(SwingConstants.CENTER);
              final NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
              panel.add(p);
              panel.add(projectIcon, BorderLayout.WEST);
              add(panel);
            }
            AccessibleContextUtil.setCombinedName(this, name, " - ", path);
            AccessibleContextUtil.setCombinedDescription(this, name, " - ", path);
          }

          @Override
          public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, JBUI.scale(44));
          }
        };
      }
    };
  }
  
  

  @Nullable
  @Override
  protected JPanel createTitle() {
    return null;
  }

  
}
