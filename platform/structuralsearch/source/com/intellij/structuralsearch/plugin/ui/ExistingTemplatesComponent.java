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
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ExistingTemplatesComponent {
  private final Tree patternTree;
  private final DefaultTreeModel patternTreeModel;
  private final DefaultMutableTreeNode userTemplatesNode;
  private final JComponent panel;
  private final CollectionListModel<Configuration> historyModel;
  private final JList<Configuration> historyList;
  private final JComponent historyPanel;
  private DialogWrapper owner;
  private final Project project;
  private final SmartList<Runnable> queuedActions = new SmartList<>();

  private ExistingTemplatesComponent(Project project) {
    this.project = project;
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
    patternTreeModel = new DefaultTreeModel(root);
    patternTree = createTree(patternTreeModel);

    DefaultMutableTreeNode parent = null;
    String lastCategory = null;
    final List<DefaultMutableTreeNode> nodesToExpand = new ArrayList<>();

    final List<Configuration> predefined = StructuralSearchUtil.getPredefinedTemplates();
    for (final Configuration info : predefined) {
      final DefaultMutableTreeNode node = new DefaultMutableTreeNode(info);

      if (lastCategory == null || !lastCategory.equals(info.getCategory())) {
        if (info.getCategory().length() > 0) {
          root.add(parent = new DefaultMutableTreeNode(info.getCategory()));
          nodesToExpand.add(parent);
          lastCategory = info.getCategory();
        }
        else {
          root.add(node);
          continue;
        }
      }

      parent.add(node);
    }

    userTemplatesNode = new DefaultMutableTreeNode(SSRBundle.message("user.defined.category"));
    root.add(userTemplatesNode);

    for (final DefaultMutableTreeNode nodeToExpand : nodesToExpand) {
      patternTree.expandPath(new TreePath(new Object[]{root, nodeToExpand}));
    }

    final TreeExpander treeExpander = new DefaultTreeExpander(patternTree);
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();
    panel = ToolbarDecorator.createDecorator(patternTree)
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final Object selection = patternTree.getLastSelectedPathComponent();
          if (!(selection instanceof DefaultMutableTreeNode)) {
            return;
          }
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selection;
          if (!(node.getUserObject() instanceof Configuration)) {
            return;
          }
          final Configuration configuration = (Configuration)node.getUserObject();
          if (configuration.isPredefined()) {
            return;
          }
          final String configurationName = configuration.getName();
          for (Configuration otherConfiguration : ConfigurationManager.getInstance(project).getConfigurations()) {
            final MatchVariableConstraint constraint =
              otherConfiguration.getMatchOptions().getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
            if (constraint == null) {
              continue;
            }
            final String within = constraint.getWithinConstraint();
            if (configurationName.equals(within)) {
              if (Messages.CANCEL == Messages.showOkCancelDialog(
                project,
                SSRBundle.message("template.in.use.message", configurationName, otherConfiguration.getName()),
                SSRBundle.message("template.in.use.title", configurationName),
                CommonBundle.message("button.remove"),
                Messages.CANCEL_BUTTON,
                AllIcons.General.WarningDialog
              )) {
                return;
              }
              break;
            }
          }
          final int[] rows = patternTree.getSelectionRows();
          if (rows != null && rows.length > 0) {
            patternTree.addSelectionRow(rows[0] - 1);
          }
          patternTreeModel.removeNodeFromParent(node);
          queuedActions.add(() -> ConfigurationManager.getInstance(project).removeConfiguration(configuration));
        }
      }).setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          final Object selection = patternTree.getLastSelectedPathComponent();
          if (selection instanceof DefaultMutableTreeNode) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selection;
            final Object userObject = node.getUserObject();
            if (userObject instanceof Configuration) {
              final Configuration configuration = (Configuration)userObject;
              return !configuration.isPredefined();
            }
          }
          return false;
        }
      })
      .addExtraAction(AnActionButton.fromAction(actionManager.createExpandAllAction(treeExpander, patternTree)))
      .addExtraAction(AnActionButton.fromAction(actionManager.createCollapseAllAction(treeExpander, patternTree)))
      .createPanel();

    new JPanel(new BorderLayout());

    configureSelectTemplateAction(patternTree);

    historyModel = new CollectionListModel<>();
    historyPanel = new JPanel(new BorderLayout());
    historyPanel.add(BorderLayout.NORTH, new JLabel(SSRBundle.message("used.templates")));

    historyList = new JBList<>(historyModel);
    historyPanel.add(BorderLayout.CENTER, ScrollPaneFactory.createScrollPane(historyList));
    historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    ListSpeedSearch<Configuration> speedSearch = new ListSpeedSearch<>(historyList, (Function<Configuration, String>)Configuration::getName);
    historyList.setCellRenderer(new ExistingTemplatesListCellRenderer(speedSearch));
    configureSelectTemplateAction(historyList);
  }

  public void selectConfiguration(String name) {
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)patternTreeModel.getRoot();
    final int count = root.getChildCount();
    for (int i = 0; i < count; i++) {
      final DefaultMutableTreeNode category = (DefaultMutableTreeNode)root.getChildAt(i);
      final int count1 = category.getChildCount();
      for (int j = 0; j < count1; j++ ) {
        final DefaultMutableTreeNode leaf = (DefaultMutableTreeNode)category.getChildAt(j);
        final Configuration configuration = (Configuration)leaf.getUserObject();
        if (name.equals(configuration.getName())) {
          TreeUtil.selectInTree(leaf, false, patternTree, false);
          return;
        }
      }
    }
  }

  private void initialize() {
    final ConfigurationManager configurationManager = ConfigurationManager.getInstance(project);
    userTemplatesNode.removeAllChildren();
    for (final Configuration config : configurationManager.getConfigurations()) {
      userTemplatesNode.add(new DefaultMutableTreeNode(config));
    }
    patternTreeModel.reload(userTemplatesNode);

    patternTree.expandPath(new TreePath(new Object[]{patternTreeModel.getRoot(), userTemplatesNode}));
    historyModel.replaceAll(configurationManager.getHistoryConfigurations());
    historyList.setSelectedIndex(0);
  }

  private void configureSelectTemplateAction(JComponent component) {
    component.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            owner.close(DialogWrapper.OK_EXIT_CODE);
          }
        }
      }
    );

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        owner.close(DialogWrapper.OK_EXIT_CODE);
        return true;
      }
    }.installOn(component);
  }

  private static Tree createTree(TreeModel treeModel) {
    final Tree tree = new Tree(treeModel);

    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setDragEnabled(false);
    tree.setEditable(false);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);


    final TreeSpeedSearch speedSearch = new TreeSpeedSearch(
      tree,
      object -> {
        final Object userObject = ((DefaultMutableTreeNode)object.getLastPathComponent()).getUserObject();
        return (userObject instanceof Configuration) ? ((Configuration)userObject).getName() : userObject.toString();
      }
    );
    tree.setCellRenderer(new ExistingTemplatesTreeCellRenderer(speedSearch));

    return tree;
  }

  public JTree getPatternTree() {
    return patternTree;
  }

  public JComponent getTemplatesPanel() {
    return panel;
  }

  public static ExistingTemplatesComponent getInstance(Project project) {
    final ExistingTemplatesComponent existingTemplatesComponent = ServiceManager.getService(project, ExistingTemplatesComponent.class);
    existingTemplatesComponent.initialize();
    return existingTemplatesComponent;
  }

  private static class ExistingTemplatesListCellRenderer extends ColoredListCellRenderer<Configuration> {

    private final ListSpeedSearch mySpeedSearch;

    public ExistingTemplatesListCellRenderer(ListSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Configuration value, int index, boolean selected, boolean focus) {
      final Color background = (selected && !focus) ?
                               UIUtil.getListUnfocusedSelectionBackground() : UIUtil.getListBackground(selected);
      final Color foreground = UIUtil.getListForeground(selected);
      setPaintFocusBorder(false);
      SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), value.getName(), SimpleTextAttributes.STYLE_PLAIN,
                                 foreground, background, this);
      final long created = value.getCreated();
      if (created > 0) {
        final String createdString = DateFormatUtil.formatPrettyDateTime(created);
        append(" (" + createdString + ')',
               selected ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground) : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
  }

  private static class ExistingTemplatesTreeCellRenderer extends ColoredTreeCellRenderer {

    private final TreeSpeedSearch mySpeedSearch;

    ExistingTemplatesTreeCellRenderer(TreeSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      final Object userObject = treeNode.getUserObject();
      if (userObject == null) return;

      final Color background = selected ? UIUtil.getTreeSelectionBackground(hasFocus) : UIUtil.getTreeTextBackground();
      final Color foreground = selected && hasFocus ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeTextForeground();

      final String text;
      final int style;
      if (userObject instanceof Configuration) {
        text = ((Configuration)userObject).getName();
        style = SimpleTextAttributes.STYLE_PLAIN;
      }
      else {
        text = userObject.toString();
        style = SimpleTextAttributes.STYLE_BOLD;
      }
      SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), text, style, foreground, background, this);
    }
  }

  public JList<Configuration> getHistoryList() {
    return historyList;
  }

  public JComponent getHistoryPanel() {
    return historyPanel;
  }

  public void setOwner(DialogWrapper owner) {
    this.owner = owner;
  }

  public void finish(boolean performQueuedActions) {
    if (performQueuedActions) {
      queuedActions.forEach(a -> a.run());
    }
    queuedActions.clear();
  }
}
