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

/*
 * @author max
 */
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.FilterComponent;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

public class ChooseActionsDialog extends DialogWrapper {
  private final ActionsTree myActionsTree;
  private FilterComponent myFilterComponent;
  private TreeExpansionMonitor myTreeExpansionMonitor;
  private final ShortcutFilteringPanel myFilteringPanel = new ShortcutFilteringPanel();
  private Keymap myKeymap;
  private QuickList[] myQuicklists;

  public ChooseActionsDialog(Component parent, Keymap keymap, QuickList[] quicklists) {
    super(parent, true);
    myKeymap = keymap;
    myQuicklists = quicklists;

    myActionsTree = new ActionsTree();
    myActionsTree.reset(keymap, quicklists);
    myActionsTree.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myActionsTree.getTree());


    myTreeExpansionMonitor = TreeExpansionMonitor.install(myActionsTree.getTree());
    myFilteringPanel.addPropertyChangeListener("shortcut", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        filterTreeByShortcut(myFilteringPanel.getShortcut());
      }
    });

    setTitle("Add Actions to Quick List");
    init();
  }

  @Override
  protected JComponent createNorthPanel() {
    return createToolbarPanel();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFilterComponent.getTextEditor();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    panel.add(myActionsTree.getComponent());
    panel.setPreferredSize(JBUI.size(400, 500));

    return panel;
  }

  public String[] getTreeSelectedActionIds() {
    TreePath[] paths = myActionsTree.getTree().getSelectionPaths();
    if (paths == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    ArrayList<String> actions = new ArrayList<>();
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode defNode = (DefaultMutableTreeNode)node;
        Object userObject = defNode.getUserObject();
        if (userObject instanceof String) {
          actions.add((String)userObject);
        }
        else if (userObject instanceof QuickList) {
          actions.add(((QuickList)userObject).getActionId());
        }
      }
    }
    return ArrayUtil.toStringArray(actions);
  }

  private JPanel createToolbarPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    DefaultActionGroup group = new DefaultActionGroup();
    final JComponent toolbar = ActionManager.getInstance().createActionToolbar("ChooseActionsDialog", group, true).getComponent();
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    final TreeExpander treeExpander = KeymapPanel.createTreeExpander(myActionsTree);
    group.add(commonActionsManager.createExpandAllAction(treeExpander, myActionsTree.getTree()));
    group.add(commonActionsManager.createCollapseAllAction(treeExpander, myActionsTree.getTree()));

    panel.add(toolbar, BorderLayout.WEST);
    group = new DefaultActionGroup();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("ChooseActionsDialog", group, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent searchToolbar = actionToolbar.getComponent();
    final Alarm alarm = new Alarm();
    myFilterComponent = new FilterComponent("KEYMAP_IN_QUICK_LISTS", 5) {
      public void filter() {
        alarm.cancelAllRequests();
        alarm.addRequest(() -> {
          if (!myFilterComponent.isShowing()) return;
          if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
          myFilteringPanel.setShortcut(null);
          final String filter = getFilter();
          myActionsTree.filter(filter, myQuicklists);
          final JTree tree = myActionsTree.getTree();
          TreeUtil.expandAll(tree);
          if (filter == null || filter.length() == 0) {
            TreeUtil.collapseAll(tree, 0);
            myTreeExpansionMonitor.restore();
          }
        }, 300);
      }
    };
    myFilterComponent.reset();

    panel.add(myFilterComponent, BorderLayout.CENTER);

    group.add(new AnAction(KeyMapBundle.message("filter.shortcut.action.text"),
                           KeyMapBundle.message("filter.shortcut.action.text"),
                           AllIcons.Actions.ShortcutFilter) {
      public void actionPerformed(AnActionEvent e) {
        myFilterComponent.reset();
        myActionsTree.reset(myKeymap, myQuicklists);
        myFilteringPanel.showPopup(searchToolbar);
      }
    });
    group.add(new AnAction(KeyMapBundle.message("filter.clear.action.text"),
                           KeyMapBundle.message("filter.clear.action.text"), AllIcons.Actions.GC) {
      @Override
      public void update(AnActionEvent event) {
        boolean enabled = null != myFilteringPanel.getShortcut();
        Presentation presentation = event.getPresentation();
        presentation.setEnabled(enabled);
        presentation.setIcon(enabled ? AllIcons.Actions.Cancel : EmptyIcon.ICON_16);
      }

      public void actionPerformed(AnActionEvent e) {
        myFilteringPanel.setShortcut(null);
        myActionsTree.filter(null, myQuicklists); //clear filtering
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
        myTreeExpansionMonitor.restore();
      }
    });

    panel.add(searchToolbar, BorderLayout.EAST);
    return panel;
  }

  private void filterTreeByShortcut(Shortcut shortcut) {
    if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
    myActionsTree.reset(myKeymap, myQuicklists);
    myActionsTree.filterTree(shortcut, myQuicklists);
    final JTree tree = myActionsTree.getTree();
    TreeUtil.expandAll(tree);
  }

  public void dispose() {
    super.dispose();
    myFilteringPanel.hidePopup();
    if (myFilterComponent != null) {
      myFilterComponent.dispose();
    }
  }
}
