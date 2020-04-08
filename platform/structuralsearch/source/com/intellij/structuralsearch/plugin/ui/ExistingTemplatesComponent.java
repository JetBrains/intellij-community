// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

public class ExistingTemplatesComponent {
  private static final Pattern SPLIT = Pattern.compile("(?<!/)/(?!/)"); // slash not preceded or followed by another slash

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

    root.add(userTemplatesNode = new DefaultMutableTreeNode(SSRBundle.message("user.defined.category")));
    for (Configuration info : StructuralSearchUtil.getPredefinedTemplates()) {
      getOrCreateCategoryNode(root, SPLIT.split(info.getCategory())).add(new DefaultMutableTreeNode(info, false));
    }

    TreeUtil.expandAll(patternTree);
    final TreeExpander treeExpander = new DefaultTreeExpander(patternTree);
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
        queuedActions.add(() -> ConfigurationManager.getInstance(project).removeConfiguration(configuration));
      }).setRemoveActionUpdater(e -> {
        final Configuration configuration = getSelectedConfiguration();
        return configuration != null && !configuration.isPredefined();
      })
      .addExtraAction(AnActionButton.fromAction(actionManager.createExpandAllAction(treeExpander, patternTree)))
      .addExtraAction(AnActionButton.fromAction(actionManager.createCollapseAllAction(treeExpander, patternTree)))
      .createPanel();

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
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER && patternTree.isVisible() && getSelectedConfiguration() != null) {
            owner.close(DialogWrapper.OK_EXIT_CODE);
          }
        }
      }
    );

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        if (patternTree.isVisible() && getSelectedConfiguration() != null) {
          owner.close(DialogWrapper.OK_EXIT_CODE);
        }
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

    ExistingTemplatesListCellRenderer(ListSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Configuration value, int index, boolean selected, boolean focus) {
      final Color background = UIUtil.getListBackground(selected, focus);
      final Color foreground = UIUtil.getListForeground(selected, focus);
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
      queuedActions.forEach(Runnable::run);
    }
    queuedActions.clear();
  }
}
