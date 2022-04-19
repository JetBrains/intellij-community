// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.GridBagConstraintBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ExistingTemplatesComponent {
  private static final Pattern SPLIT = Pattern.compile("(?<!/)/(?!/)"); // slash not preceded or followed by another slash

  private final Tree patternTree;
  private final DefaultTreeModel patternTreeModel;
  private final JComponent panel;
  private final JComponent myToolbar;
  private Supplier<? extends Configuration> myConfigurationProducer;
  private Supplier<? extends EditorTextField> mySearchEditorProducer;
  private Runnable myExportRunnable;
  private Runnable myImportRunnable;
  private final DefaultMutableTreeNode myDraftTemplateNode;
  private final DefaultMutableTreeNode myRecentNode;
  private final DefaultMutableTreeNode myUserTemplatesNode;
  private boolean myTemplateChanged = false;
  private boolean myDraftTemplateAutoselect = false;

  ExistingTemplatesComponent(Project project) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
    patternTreeModel = new DefaultTreeModel(root);
    patternTree = createTree(patternTreeModel);

    final ConfigurationManager configurationManager = ConfigurationManager.getInstance(project);

    // 'New Template' node
    root.add(myDraftTemplateNode = new DraftTemplateNode());

    // 'Recent' node
    root.add(myRecentNode = new DefaultMutableTreeNode(SSRBundle.message("recent.category")));
    for (final Configuration config : configurationManager.getHistoryConfigurations()) {
      myRecentNode.add(new DefaultMutableTreeNode(config));
    }
    patternTree.expandPath(new TreePath(new Object[]{patternTreeModel.getRoot(), myRecentNode}));

    // 'Saved templates' node
    root.add(myUserTemplatesNode = new DefaultMutableTreeNode(SSRBundle.message("user.defined.category")));
    reloadUserTemplates(configurationManager);

    // Predefined templates
    for (Configuration info : StructuralSearchUtil.getPredefinedTemplates()) {
      getOrCreateCategoryNode(root, SPLIT.split(info.getCategory())).add(new DefaultMutableTreeNode(info, false));
    }

    patternTreeModel.reload();
    TreeUtil.expandAll(patternTree);
    final TreeExpander treeExpander = new DefaultTreeExpander(patternTree);

    // Toolbar actions
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final DefaultActionGroup saveGroup = new DefaultActionGroup(SSRBundle.messagePointer("save.template"),
                                                                SSRBundle.messagePointer("save.template.description.button"),
                                                                AllIcons.Actions.MenuSaveall);

    final DumbAwareAction saveTemplateAction = new DumbAwareAction(SSRBundle.message("save.template.action.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ConfigurationManager.getInstance(project).showSaveTemplateAsDialog(myConfigurationProducer.get());
        reloadUserTemplates(configurationManager);
      }
    };
    final DumbAwareAction saveInspectionAction = new DumbAwareAction(SSRBundle.message("save.inspection.action.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        StructuralSearchProfileActionProvider.createNewInspection(myConfigurationProducer.get(), project);
      }
    };

    saveGroup.addAll(saveTemplateAction, saveInspectionAction);
    saveGroup.setPopup(true);

    final DumbAwareAction removeAction = new DumbAwareAction(SSRBundle.messagePointer("remove.template"),
                                                             AllIcons.General.Remove) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        final DefaultMutableTreeNode selectedNode = getSelectedNode();
        e.getPresentation().setEnabled(selectedNode != null && selectedNode.isNodeAncestor(myUserTemplatesNode));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeTemplate(project);
      }
    };

    final DumbAwareAction exportAction = new DumbAwareAction(SSRBundle.messagePointer("export.template.action"), AllIcons.ToolbarDecorator.Export) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        if (mySearchEditorProducer != null) {
          e.getPresentation().setEnabled(!StringUtil.isEmptyOrSpaces(mySearchEditorProducer.get().getText()));
        }
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myExportRunnable != null) {
          myExportRunnable.run();
        }
      }
    };
    final DumbAwareAction importAction = new DumbAwareAction(SSRBundle.messagePointer("import.template.action"), AllIcons.ToolbarDecorator.Import) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myImportRunnable != null) {
          myImportRunnable.run();
        }
      }
    };

    actionGroup.add(saveGroup);
    actionGroup.add(removeAction);
    actionGroup.add(Separator.getInstance());
    actionGroup.addAll(actionManager.createExpandAllAction(treeExpander, patternTree),
                       actionManager.createCollapseAllAction(treeExpander, patternTree));
    actionGroup.add(Separator.getInstance());
    actionGroup.add(exportAction);
    actionGroup.add(importAction);

    final var optionsToolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar("ExistingTemplatesComponent", actionGroup, true);
    optionsToolbar.setTargetComponent(patternTree);
    optionsToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    optionsToolbar.setForceMinimumSize(true);
    myToolbar = optionsToolbar.getComponent();

    panel = new JPanel(new GridBagLayout());
    final var constraints = new GridBagConstraintBuilder();
    panel.add(myToolbar, constraints.growX().fillX().get());
    final var scrollPane = new JBScrollPane(patternTree);
    scrollPane.setBorder(JBUI.Borders.empty());
    panel.add(scrollPane, constraints.newLine().growXY().fillXY().get());
    panel.setBorder(JBUI.Borders.empty());
  }

  private void reloadUserTemplates(ConfigurationManager configurationManager) {
    myUserTemplatesNode.removeAllChildren();
    for (final Configuration config : configurationManager.getConfigurations()) {
      myUserTemplatesNode.add(new DefaultMutableTreeNode(config));
    }
    patternTree.expandPath(new TreePath(new Object[]{patternTreeModel.getRoot(), myUserTemplatesNode}));
    patternTreeModel.reload(myUserTemplatesNode);
  }

  private void removeTemplate(Project project) {
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
    if (((DefaultMutableTreeNode)selection).isNodeAncestor(myRecentNode)) {
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
  }

  public void selectFileType(String name) {
    final var root = (DefaultMutableTreeNode) patternTreeModel.getRoot();
    final Enumeration<TreeNode> children = root.children();
    while (children.hasMoreElements()) {
      final var node = (DefaultMutableTreeNode) children.nextElement();
      for (String lang : node.toString().split("/")) {
        if (lang.equals(name)) {
          TreeUtil.selectInTree(node, false, patternTree, true);
          return;
        }
      }
    }
  }

  public void templateChanged() {
    if (!myTemplateChanged) {
      myTemplateChanged = true;
      if (!myDraftTemplateNode.equals(getSelectedNode())) {
        myDraftTemplateAutoselect = true;
        TreeUtil.selectInTree(myDraftTemplateNode, false, patternTree, true);
        myDraftTemplateAutoselect = false;
      }
    }
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

  public DefaultMutableTreeNode getSelectedNode() {
    final Object selection = patternTree.getLastSelectedPathComponent();
    if (!(selection instanceof DefaultMutableTreeNode)) {
      return null;
    }
    return (DefaultMutableTreeNode)selection;
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

  private static class DraftTemplateNode extends DefaultMutableTreeNode {}

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
      if (userObject == null && !(treeNode instanceof DraftTemplateNode)) return;

      final Color background = UIUtil.getTreeBackground(selected, hasFocus);
      final Color foreground = UIUtil.getTreeForeground(selected, hasFocus);

      final String text;
      final int style;
      if (treeNode instanceof DraftTemplateNode) {
        text = SSRBundle.message("draft.template.node");
        style = SimpleTextAttributes.STYLE_BOLD;
      }
      else if (userObject instanceof Configuration) {
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
      if (!(selection instanceof DefaultMutableTreeNode) || myDraftTemplateAutoselect) {
        return;
      }

      if (myTemplateChanged) {
        myDraftTemplateNode.setUserObject(myConfigurationProducer.get());
        myTemplateChanged = false;
      }

      final var configuration = ((DefaultMutableTreeNode)selection).getUserObject();
      if (configuration instanceof Configuration) {
        consumer.accept((Configuration)configuration);
      }
    });
  }

  public void setConfigurationProducer(Supplier<? extends Configuration> configurationProducer) {
    myConfigurationProducer = configurationProducer;
  }

  public void setSearchEditorProducer(Supplier<? extends EditorTextField> editorProducer) {
    mySearchEditorProducer = editorProducer;
  }

  public void setExportRunnable(Runnable exportRunnable) {
    myExportRunnable = exportRunnable;
  }

  public void setImportRunnable(Runnable importRunnable) {
    myImportRunnable = importRunnable;
  }

  public void updateColors() {
    myToolbar.setBorder(JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Editor.BORDER_COLOR),
                                              JBUI.Borders.empty(3)));
  }
}
