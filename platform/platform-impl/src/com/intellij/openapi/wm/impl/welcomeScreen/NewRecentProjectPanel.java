// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.filename.UniqueNameBuilder;
import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ListActions;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.IconUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class NewRecentProjectPanel extends RecentProjectPanel {

  public NewRecentProjectPanel(@NotNull Disposable parentDisposable) {
    this(parentDisposable, true);
  }

  public NewRecentProjectPanel(@NotNull Disposable parentDisposable, boolean withSpeedSearch) {
    super(parentDisposable, withSpeedSearch);
    setBorder(JBUI.Borders.empty());
    JScrollPane scrollPane = UIUtil.findComponentOfType(this, JScrollPane.class);
    if (scrollPane != null) {
      JBDimension size = JBUI.size(300, 460);
      scrollPane.setSize(size);
      scrollPane.setMinimumSize(size);
      scrollPane.setPreferredSize(size);
    }
    setBackground(WelcomeScreenUIManager.getProjectsBackground());
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    JScrollPane scrollPane = UIUtil.findComponentOfType(this, JScrollPane.class);
    if (scrollPane != null) {
      scrollPane.setBackground(bg);
    }
    ListWithFilter panel = UIUtil.findComponentOfType(this, ListWithFilter.class);
    if (panel != null) {
      panel.setBackground(bg);
    }
    if (myList != null) {
      myList.setBackground(bg);
    }
  }

  @Override
  protected Dimension getPreferredScrollableViewportSize() {
    return null;
  }

  @Override
  public void addNotify() {
    super.addNotify();

    JList list = UIUtil.findComponentOfType(this, JList.class);
    if (list != null) {
      list.updateUI();
    }
  }

  @Override
  protected JBList<AnAction> createList(AnAction[] recentProjectActions, Dimension size) {
    final JBList<AnAction> list = super.createList(recentProjectActions, size);

    list.setBackground(getBackground());
    list.getActionMap().put(ListActions.Right.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selected = list.getSelectedValue();
        ProjectGroup group = null;
        if (selected instanceof ProjectGroupActionGroup) {
          group = ((ProjectGroupActionGroup)selected).getGroup();
        }

        if (group != null) {
          if (!group.isExpanded()) {
            group.setExpanded(true);
            ListModel model = ((NameFilteringListModel)list.getModel()).getOriginalModel();
            int index = list.getSelectedIndex();
            RecentProjectsWelcomeScreenActionBase.rebuildRecentProjectDataModel(model);
            list.setSelectedIndex(group.getProjects().isEmpty() ? index : index + 1);
          }
        } else {
          FlatWelcomeFrame frame = ComponentUtil.getParentOfType((Class<? extends FlatWelcomeFrame>)FlatWelcomeFrame.class, list);
          if (frame != null) {
            FocusTraversalPolicy policy = frame.getFocusTraversalPolicy();
            if (policy != null) {
              Component next = policy.getComponentAfter(frame, list);
              if (next != null) {
                IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(next, true));
              }
            }
          }
        }
      }
    });
    list.getActionMap().put(ListActions.Left.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selected = list.getSelectedValue();
        ProjectGroup group = null;
        if (selected instanceof ProjectGroupActionGroup) {
          group = ((ProjectGroupActionGroup)selected).getGroup();
        }

        if (group != null && group.isExpanded()) {
          group.setExpanded(false);
          int index = list.getSelectedIndex();
          ListModel model = ((NameFilteringListModel)list.getModel()).getOriginalModel();
          RecentProjectsWelcomeScreenActionBase.rebuildRecentProjectDataModel(model);
          list.setSelectedIndex(index);
        }
      }
    });
    return list;
  }

  @Override
  protected boolean isUseGroups() {
    return true;
  }

  @Override
  protected ListCellRenderer<AnAction> createRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
    return new RecentProjectItemRenderer() {
       private GridBagConstraints nameCell;
       private GridBagConstraints pathCell;

      private void initConstraints () {
        nameCell = new GridBagConstraints();
        pathCell = new GridBagConstraints();

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
      }

      @Override
      protected Color getListBackground(boolean isSelected, boolean hasFocus) {
        return isSelected ? WelcomeScreenUIManager.getProjectsSelectionBackground(hasFocus) : NewRecentProjectPanel.this.getBackground();
      }

      @Override
      protected Color getListForeground(boolean isSelected, boolean hasFocus) {
        return  WelcomeScreenUIManager.getProjectsSelectionForeground(isSelected, hasFocus);
      }

      @Override
      protected void layoutComponents() {
        setLayout(new GridBagLayout());
        initConstraints();
        add(myName, nameCell);
        add(myPath, pathCell);
      }
      final JComponent spacer = new NonOpaquePanel() {
        @Override
        public Dimension getPreferredSize() {
          return new Dimension(JBUIScale.scale(22), super.getPreferredSize().height);
        }
      };

      @Override
      public Component getListCellRendererComponent(JList<? extends AnAction> list, AnAction value, int index, boolean selected, boolean focused) {
        final Color fore = getListForeground(selected, list.hasFocus());
        final Color back = getListBackground(selected, list.hasFocus());
        final JLabel name = new JLabel();
        final JLabel path = ComponentPanelBuilder.createNonWrappingCommentComponent("");
        name.setForeground(fore);
        path.setForeground(UIUtil.getInactiveTextColor());

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

            setBorder(JBUI.Borders.empty(8, 7));
            if (isInsideGroup) {
              add(spacer, BorderLayout.WEST);
            }
            if (isGroup) {
              final ProjectGroup group = ((ProjectGroupActionGroup)value).getGroup();
              name.setText(" " + group.getName());
              name.setIcon(IconUtil.toSize(group.isExpanded() ? UIUtil.getTreeExpandedIcon() : UIUtil.getTreeCollapsedIcon(),
                                           JBUIScale.scale(16), JBUIScale.scale(16)));
              name.setFont(name.getFont().deriveFont(Font.BOLD));
              add(name);
            } else if (value instanceof ReopenProjectAction) {
              final NonOpaquePanel p = new NonOpaquePanel(new BorderLayout());
              name.setText(((ReopenProjectAction)value).getProjectNameToDisplay());
              final String realPath = PathUtil.toSystemDependentName(((ReopenProjectAction)value).getProjectPath());
              int i = isInsideGroup ? 80 : 60;
              path.setText(getTitle2Text((ReopenProjectAction)value, path, JBUIScale.scale(i)));
              if (!realPath.equals(path.getText())) {
                projectsWithLongPaths.add((ReopenProjectAction)value);
              }
              boolean isValid = !isPathValid((((ReopenProjectAction)value).getProjectPath()));
              if (isValid) {
                name.setForeground(UIUtil.getInactiveTextColor());
              }
              p.add(name, BorderLayout.NORTH);
              p.add(path, BorderLayout.SOUTH);
              p.setBorder(JBUI.Borders.emptyRight(30));

              String projectPath = ((ReopenProjectAction)value).getProjectPath();
              RecentProjectsManagerBase recentProjectsManage = RecentProjectsManagerBase.getInstanceEx();
              Icon icon = recentProjectsManage.getProjectIcon(projectPath, true);
              final JLabel projectIcon = new JLabel("", icon, SwingConstants.LEFT) {
                @Override
                protected void paintComponent(Graphics g) {
                  Icon icon = isEnabled() ? getIcon() : getDisabledIcon();
                  icon.paintIcon(this, g, 0, 0);
                }
              };
              projectIcon.setDisabledIcon(IconUtil.desaturate(icon));
              if (isValid) {
                projectIcon.setEnabled(false);
              }
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

          private Icon getGray(Icon icon) {
            final int w = icon.getIconWidth();
            final int h = icon.getIconHeight();
            GraphicsEnvironment ge =
              GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            BufferedImage image = gc.createCompatibleImage(w, h);
            Graphics2D g2d = image.createGraphics();
            icon.paintIcon(null, g2d, 0, 0);
            Image gray = GrayFilter.createDisabledImage(image);
            return new ImageIcon(gray);
          }

          @Override
          public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, JBUIScale.scale(value instanceof ProjectGroupActionGroup ? 32 : 50));
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