// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class ExistingTemplatesComponent {
  private static final Pattern SPLIT = Pattern.compile("(?<!/)/(?!/)"); // slash not preceded or followed by another slash

  private final Tree patternTree;
  private final DefaultTreeModel patternTreeModel;
  private final JComponent panel;

  ExistingTemplatesComponent(Project project) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
    patternTreeModel = new DefaultTreeModel(root);
    patternTree = createTree(patternTreeModel);

    final ConfigurationManager configurationManager = ConfigurationManager.getInstance(project);

    // 'Recent' node
    DefaultMutableTreeNode recentNode;
    root.add(recentNode = new DefaultMutableTreeNode(SSRBundle.message("recent.category")));
    for (final Configuration config : configurationManager.getHistoryConfigurations()) {
      recentNode.add(new DefaultMutableTreeNode(config));
    }
    patternTree.expandPath(new TreePath(new Object[]{patternTreeModel.getRoot(), recentNode}));

    // 'Saved templates' node
    DefaultMutableTreeNode userTemplatesNode;
    root.add(userTemplatesNode = new DefaultMutableTreeNode(SSRBundle.message("user.defined.category")));
    for (final Configuration config : configurationManager.getConfigurations()) {
      userTemplatesNode.add(new DefaultMutableTreeNode(config));
    }
    patternTree.expandPath(new TreePath(new Object[]{patternTreeModel.getRoot(), userTemplatesNode}));

    // Predefined templates
    for (Configuration info : StructuralSearchUtil.getPredefinedTemplates()) {
      getOrCreateCategoryNode(root, SPLIT.split(info.getCategory())).add(new DefaultMutableTreeNode(info, false));
    }

    patternTreeModel.reload();
    TreeUtil.expandAll(patternTree);
    final TreeExpander treeExpander = new DefaultTreeExpander(patternTree);

    // Toolbar actions
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();
    panel = ToolbarDecorator.createDecorator(patternTree)
      .setRemoveAction(button -> {
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
              Messages.getCancelButton(),
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
        ConfigurationManager.getInstance(project).removeConfiguration(configuration);
      }).setRemoveActionUpdater(e -> {
        final Configuration configuration = getSelectedConfiguration();
        return configuration != null && !configuration.isPredefined();
      })
      .addExtraAction(AnActionButton.fromAction(actionManager.createExpandAllAction(treeExpander, patternTree)))
      .addExtraAction(AnActionButton.fromAction(actionManager.createCollapseAllAction(treeExpander, patternTree)))
      .createPanel();
  }

  @NotNull
  private static DefaultMutableTreeNode getOrCreateCategoryNode(@NotNull DefaultMutableTreeNode root, String[] path) {
    DefaultMutableTreeNode result = root;
    outer:
    for (String step : path) {
      step = StringUtil.replace(step, "//", "/");
      DefaultMutableTreeNode child = (result.getChildCount() == 0) ? null : (DefaultMutableTreeNode)result.getLastChild();
      while (child != null) {
        if (step.equals(child.getUserObject())) {
          result = child;
          continue outer;
        }
        else child = child.getPreviousSibling();
      }
      final DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(step);
      result.add(newNode);
      result = newNode;
    }
    return result;
  }

  public void selectConfiguration(String name) {
    final DefaultMutableTreeNode node = TreeUtil.findNode((DefaultMutableTreeNode)patternTreeModel.getRoot(), n -> {
      final Object object = n.getUserObject();
      return object instanceof Configuration && name.equals(((Configuration)object).getName());
    });
    TreeUtil.selectInTree(node, false, patternTree, false);
  }

  public Configuration getSelectedConfiguration() {
    final Object selection = patternTree.getLastSelectedPathComponent();
    if (!(selection instanceof DefaultMutableTreeNode)) {
      return null;
    }
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selection;
    if (!(node.getUserObject() instanceof Configuration)) {
      return null;
    }
    return (Configuration)node.getUserObject();
  }

  private static Tree createTree(TreeModel treeModel) {
    final Tree tree = new Tree(treeModel);

    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setDragEnabled(false);
    tree.setEditable(false);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setTransferHandler(new TransferHandler() {
      @Nullable
      @Override
      protected Transferable createTransferable(JComponent c) {
        final Object selection = tree.getLastSelectedPathComponent();
        if (!(selection instanceof DefaultMutableTreeNode)) {
          return null;
        }
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selection;
        if (!(node.getUserObject() instanceof Configuration)) {
          return null;
        }
        return new TextTransferable(ConfigurationUtil.toXml((Configuration)node.getUserObject()));
      }

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }
    });

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

  public JComponent getTemplatesPanel() {
    return panel;
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

      final Color background = UIUtil.getTreeBackground(selected, hasFocus);
      final Color foreground = UIUtil.getTreeForeground(selected, hasFocus);

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

  public void onConfigurationSelected(Consumer<Configuration> consumer) {
    patternTree.addTreeSelectionListener(event -> {
      final var selection = patternTree.getLastSelectedPathComponent();
      if (!(selection instanceof DefaultMutableTreeNode)) {
        return;
      }

      final var configuration = ((DefaultMutableTreeNode)selection).getUserObject();
      if (configuration instanceof Configuration) {
        consumer.accept((Configuration)configuration);
      }
    });
  }
}
