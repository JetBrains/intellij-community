/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 2, 2004
 * Time: 1:27:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExistingTemplatesComponent {
  private final Tree patternTree;
  private final DefaultTreeModel patternTreeModel;
  private final DefaultMutableTreeNode userTemplatesNode;
  private final JComponent panel;
  private final CollectionListModel<Configuration> historyModel;
  private final JList historyList;
  private final JComponent historyPanel;
  private DialogWrapper owner;
  private final Project project;

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

    final ConfigurationManager configurationManager = ConfigurationManager.getInstance(project);
    userTemplatesNode = new DefaultMutableTreeNode(SSRBundle.message("user.defined.category"));
    root.add(userTemplatesNode);
    setUserTemplates(configurationManager);

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
          final int[] rows = patternTree.getSelectionRows();
          if (rows != null && rows.length > 0) {
            patternTree.addSelectionRow(rows[0] - 1);
          }
          patternTreeModel.removeNodeFromParent(node);
          configurationManager.removeConfiguration(configuration);
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

    historyModel = new CollectionListModel<>(configurationManager.getHistoryConfigurations());
    historyPanel = new JPanel(new BorderLayout());
    historyPanel.add(BorderLayout.NORTH, new JLabel(SSRBundle.message("used.templates")));

    historyList = new JBList(historyModel);
    historyPanel.add(BorderLayout.CENTER, ScrollPaneFactory.createScrollPane(historyList));
    historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    historyList.setSelectedIndex(0);

    final ListSpeedSearch speedSearch = new ListSpeedSearch(historyList, new Convertor<Object, String>() {
      @Override
      public String convert(Object o) {
        return o instanceof Configuration ? ((Configuration)o).getName() : o.toString();
      }
    });
    historyList.setCellRenderer(new ExistingTemplatesListCellRenderer(speedSearch));
    configureSelectTemplateAction(historyList);
  }

  public void setUserTemplates(ConfigurationManager configurationManager) {
    userTemplatesNode.removeAllChildren();
    if (configurationManager.getConfigurations() != null) {
      for (final Configuration config : configurationManager.getConfigurations()) {
        userTemplatesNode.add(new DefaultMutableTreeNode(config));
      }
    }
    patternTreeModel.reload(userTemplatesNode);

    patternTree.expandPath(new TreePath(new Object[]{patternTreeModel.getRoot(), userTemplatesNode}));
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
      new Convertor<TreePath, String>() {
        public String convert(TreePath object) {
          final Object userObject = ((DefaultMutableTreeNode)object.getLastPathComponent()).getUserObject();
          return (userObject instanceof Configuration) ? ((Configuration)userObject).getName() : userObject.toString();
        }
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
    return ServiceManager.getService(project, ExistingTemplatesComponent.class);
  }

  private static class ExistingTemplatesListCellRenderer extends ColoredListCellRenderer {

    private final ListSpeedSearch mySpeedSearch;

    public ExistingTemplatesListCellRenderer(ListSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean focus) {
      if (!(value instanceof Configuration)) {
        return;
      }
      final Configuration configuration = (Configuration)value;
      final Color background = (selected && !focus) ?
                               UIUtil.getListUnfocusedSelectionBackground() : UIUtil.getListBackground(selected);
      final Color foreground = UIUtil.getListForeground(selected);
      setPaintFocusBorder(false);
      SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), configuration.getName(), SimpleTextAttributes.STYLE_PLAIN,
                                 foreground, background, this);
      final long created = configuration.getCreated();
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

  void addConfigurationToHistory(Configuration configuration) {
    historyModel.remove(configuration);
    historyModel.add(0, configuration);
    final ConfigurationManager configurationManager = ConfigurationManager.getInstance(project);
    configurationManager.addHistoryConfigurationToFront(configuration);
    historyList.setSelectedIndex(0);

    if (historyModel.getSize() > 25) {
      configurationManager.removeHistoryConfiguration(historyModel.getElementAt(25));
      // we add by one!
      historyModel.remove(25);
    }
  }

  public JList getHistoryList() {
    return historyList;
  }

  public JComponent getHistoryPanel() {
    return historyPanel;
  }

  public void setOwner(DialogWrapper owner) {
    this.owner = owner;
  }
}
