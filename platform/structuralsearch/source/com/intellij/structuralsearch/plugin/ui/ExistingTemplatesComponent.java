// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

public final class ExistingTemplatesComponent {
  private static final Pattern SPLIT = Pattern.compile("(?<!/)/(?!/)"); // slash not preceded or followed by another slash

  private final Tree patternTree;
  private final JBScrollPane myScrollPane;
  private final DefaultTreeModel patternTreeModel;
  private final JComponent panel;
  private Supplier<? extends Configuration> myConfigurationProducer;
  private final DefaultMutableTreeNode myDraftTemplateNode;
  private final DefaultMutableTreeNode myRecentNode;
  private final DefaultMutableTreeNode myUserTemplatesNode;
  private final DefaultMutableTreeNode myProjectTemplatesNode;
  private boolean myTemplateChanged = false;
  private boolean myDraftTemplateAutoselect = false;

  ExistingTemplatesComponent(Project project, JComponent keyboardShortcutRoot, AnAction importAction, AnAction exportAction) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    myDraftTemplateNode = new DefaultMutableTreeNode(SSRBundle.message("draft.template.node")); // 'New Template' node
    myRecentNode = new DefaultMutableTreeNode(SSRBundle.message("recent.category")); // 'Recent' node
    myUserTemplatesNode = new DefaultMutableTreeNode(SSRBundle.message("user.defined.category")); // 'Saved templates' node
    myProjectTemplatesNode = new DefaultMutableTreeNode(SSRBundle.message("project.templates.category"));

    root.add(myDraftTemplateNode);
    root.add(myRecentNode);
    root.add(myUserTemplatesNode);

    patternTreeModel = new DefaultTreeModel(root);
    patternTree = createTree(patternTreeModel);

    final ConfigurationManager configurationManager = ConfigurationManager.getInstance(project);
    for (Configuration config : configurationManager.getHistoryConfigurations()) {
      myRecentNode.add(new DefaultMutableTreeNode(config));
    }
    patternTree.expandPath(new TreePath(new Object[]{patternTreeModel.getRoot(), myRecentNode}));
    reloadUserTemplates(configurationManager);

    // Predefined templates
    for (Configuration info : StructuralSearchUtil.getPredefinedTemplates()) {
      getOrCreateCategoryNode(root, SPLIT.split(info.getCategory())).add(new DefaultMutableTreeNode(info, false));
    }

    patternTreeModel.reload();
    final TreeExpander treeExpander = new DefaultTreeExpander(patternTree);

    // Toolbar actions
    final DumbAwareAction saveTemplateAction = new DumbAwareAction(SSRBundle.message("save.template.action.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Configuration configuration = myConfigurationProducer.get();
        if (ConfigurationManager.getInstance(project).showSaveTemplateAsDialog(configuration)) {
          reloadUserTemplates(configurationManager);
          DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(myUserTemplatesNode, configuration);
          if (node != null) {
            TreeUtil.selectNode(patternTree, node);
          }
        }
      }
    };
    final DumbAwareAction saveInspectionAction = new DumbAwareAction(SSRBundle.message("save.inspection.action.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        StructuralSearchProfileActionProvider.createNewInspection(myConfigurationProducer.get(), project);
      }
    };

    final DefaultActionGroup saveGroup = new DefaultActionGroup(SSRBundle.messagePointer("save.template"),
                                                                SSRBundle.messagePointer("save.template.description.button"),
                                                                AllIcons.Actions.MenuSaveall);
    saveGroup.addAll(saveTemplateAction, saveInspectionAction);
    saveGroup.setPopup(true);

    final DumbAwareAction removeAction = new DumbAwareAction(SSRBundle.messagePointer("remove.template"), AllIcons.General.Remove) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        final DefaultMutableTreeNode selectedNode = getSelectedNode();
        boolean enabled = selectedNode != null &&
                          selectedNode.getUserObject() instanceof Configuration &&
                          selectedNode.isNodeAncestor(myUserTemplatesNode);
        e.getPresentation().setEnabled(enabled);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeTemplate(project);
      }
    };

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    actionGroup.addAll(saveGroup, removeAction, Separator.getInstance(),
                       commonActionsManager.createExpandAllAction(treeExpander, patternTree),
                       commonActionsManager.createCollapseAllAction(treeExpander, patternTree),
                       Separator.getInstance(), exportAction, importAction);

    ActionManager actionManager = ActionManager.getInstance();
    final ActionToolbarImpl toolbar =
      (ActionToolbarImpl)actionManager.createActionToolbar("ExistingTemplatesComponent", actionGroup, true);
    toolbar.setTargetComponent(patternTree);
    toolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    toolbar.setForceMinimumSize(true);
    toolbar.setBorder(JBUI.Borders.empty(DEFAULT_VGAP, 0));

    panel = new JPanel(new GridBagLayout());
    final var constraints = new GridBag().setDefaultWeightX(1.0);
    panel.add(toolbar, constraints.nextLine().fillCellHorizontally().insets(JBInsets.create(0, DEFAULT_HGAP)));
    myScrollPane = new JBScrollPane(patternTree);
    panel.add(myScrollPane, constraints.nextLine().weighty(1.0).fillCell());
    panel.setBorder(JBUI.Borders.empty());

    saveGroup.registerCustomShortcutSet(actionManager.getAction("SaveAll").getShortcutSet(), keyboardShortcutRoot);
    removeAction.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_DELETE).getShortcutSet(), panel);
    importAction.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_PASTE).getShortcutSet(), panel);
    exportAction.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_COPY).getShortcutSet(), panel);
  }

  public TreeState getTreeState() {
    return TreeState.createOn(patternTree, true, true);
  }

  public void setTreeState(TreeState treeState) {
    if (treeState == null) {
      TreeUtil.expandAll(patternTree);
    }
    else {
      treeState.applyTo(patternTree);
    }
  }

  private void reloadUserTemplates(ConfigurationManager configurationManager) {
    myProjectTemplatesNode.removeAllChildren();
    myUserTemplatesNode.removeAllChildren();
    for (Configuration config : configurationManager.getIdeConfigurations()) {
      myUserTemplatesNode.add(new DefaultMutableTreeNode(config));
    }
    List<Configuration> projectConfigurations = configurationManager.getProjectConfigurations();
    if (!projectConfigurations.isEmpty()) {
      myUserTemplatesNode.insert(myProjectTemplatesNode, 0);
      for (Configuration config : projectConfigurations) {
        myProjectTemplatesNode.add(new DefaultMutableTreeNode(config));
      }
    }
    patternTreeModel.reload(myUserTemplatesNode);
  }

  private void removeTemplate(Project project) {
    final Object selection = patternTree.getLastSelectedPathComponent();
    if (!(selection instanceof DefaultMutableTreeNode node)) {
      return;
    }
    if (!(node.getUserObject() instanceof Configuration configuration)) {
      return;
    }
    if (configuration.isPredefined()) {
      return;
    }
    if (node.isNodeAncestor(myRecentNode)) {
      return;
    }
    final String configurationName = configuration.getName();
    outer:
    for (Configuration otherConfiguration : ConfigurationManager.getInstance(project).getAllConfigurations()) {
      MatchOptions matchOptions = otherConfiguration.getMatchOptions();
      for (String name : matchOptions.getVariableConstraintNames()) {
        MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
        if (constraint == null) {
          continue;
        }
        if (configurationName.equals(constraint.getWithinConstraint()) ||
            configurationName.equals(constraint.getContainsConstraint()) ||
            configurationName.equals(constraint.getReferenceConstraint())) {
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
          break outer;
        }
      }
    }
    DefaultMutableTreeNode sibling = node.getNextSibling();
    if (sibling == null) {
      sibling = node.getPreviousSibling();
    }
    if (sibling == null) {
      sibling = myUserTemplatesNode;
    }
    TreeUtil.selectNode(patternTree, sibling);
    TreeNode parent = node.getParent();
    patternTreeModel.removeNodeFromParent(node);
    if (parent == myProjectTemplatesNode && parent.getChildCount() == 0) {
      // hide project-templates node when there are no project templates anymore
      patternTreeModel.removeNodeFromParent((MutableTreeNode)parent);
    }
    ConfigurationManager.getInstance(project).removeConfiguration(configuration, parent == myUserTemplatesNode);
  }

  public void selectFileType(LanguageFileType fileType) {
    final var root = (DefaultMutableTreeNode) patternTreeModel.getRoot();
    final Enumeration<TreeNode> children = root.children();
    while (children.hasMoreElements()) {
      final var node = (DefaultMutableTreeNode) children.nextElement();
      for (String lang : node.toString().split("/")) {
        if (lang.equalsIgnoreCase(fileType.getName())) {
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

  private static @NotNull DefaultMutableTreeNode getOrCreateCategoryNode(@NotNull DefaultMutableTreeNode root, String[] path) {
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

  public DefaultMutableTreeNode getSelectedNode() {
    final Object selection = patternTree.getLastSelectedPathComponent();
    if (!(selection instanceof DefaultMutableTreeNode)) {
      return null;
    }
    return (DefaultMutableTreeNode)selection;
  }

  private Tree createTree(TreeModel treeModel) {
    final Tree tree = new Tree(treeModel);

    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setDragEnabled(false);
    tree.setEditable(false);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setTransferHandler(new TransferHandler() {
      @Override
      protected @Nullable Transferable createTransferable(JComponent c) {
        final Object selection = tree.getLastSelectedPathComponent();
        if (!(selection instanceof DefaultMutableTreeNode node)) {
          return null;
        }
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

    final TreeSpeedSearch speedSearch = TreeSpeedSearch.installOn(
      tree,
      false,
      treePath -> {
        final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        if (treeNode == myDraftTemplateNode) return SSRBundle.message("draft.template.node");
        final Object userObject = treeNode.getUserObject();
        return (userObject instanceof Configuration) ? ((Configuration)userObject).getName() : userObject.toString();
      }
    );
    tree.setCellRenderer(new ExistingTemplatesTreeCellRenderer(speedSearch, myDraftTemplateNode));

    return tree;
  }

  public JComponent getTemplatesPanel() {
    return panel;
  }

  private static class ExistingTemplatesTreeCellRenderer extends ColoredTreeCellRenderer {

    private final TreeSpeedSearch mySpeedSearch;
    private final TreeNode myDraftNode;

    ExistingTemplatesTreeCellRenderer(@NotNull TreeSpeedSearch speedSearch, @NotNull TreeNode draftNode) {
      mySpeedSearch = speedSearch;
      myDraftNode = draftNode;
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
      if (userObject == null && treeNode != myDraftNode) return;

      final Color background = UIUtil.getTreeBackground(selected, hasFocus);
      final Color foreground = UIUtil.getTreeForeground(selected, hasFocus);

      final String text;
      final int style;
      if (treeNode == myDraftNode) {
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

  public void onConfigurationSelected(Consumer<? super Configuration> consumer) {
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

  public void updateColors() {
    myScrollPane.setBorder(JBUI.Borders.customLineTop(JBUI.CurrentTheme.Editor.BORDER_COLOR));
  }
}