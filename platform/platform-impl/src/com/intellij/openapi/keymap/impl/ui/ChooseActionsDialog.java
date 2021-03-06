// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.IdeBundle;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
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
  private final TreeExpansionMonitor<?> myTreeExpansionMonitor;
  private final ShortcutFilteringPanel myFilteringPanel = new ShortcutFilteringPanel();
  private final Keymap myKeymap;
  private final QuickList[] myQuicklists;

  public ChooseActionsDialog(Component parent, Keymap keymap, QuickList[] quicklists) {
    super(parent, true);
    myKeymap = keymap;
    myQuicklists = quicklists;

    myActionsTree = new ActionsTree();
    myActionsTree.reset(keymap, quicklists);
    myActionsTree.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
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

    setTitle(IdeBundle.message("dialog.title.add.actions.to.quick.list"));
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
    return JBUI.Panels.simplePanel(myActionsTree.getComponent())
      .withPreferredSize(400, 500);
  }

  public String[] getTreeSelectedActionIds() {
    TreePath[] paths = myActionsTree.getTree().getSelectionPaths();
    if (paths == null) return ArrayUtilRt.EMPTY_STRING_ARRAY;

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
    return ArrayUtilRt.toStringArray(actions);
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
      @Override
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
                           KeyMapBundle.message("filter.shortcut.action.description"),
                           AllIcons.Actions.ShortcutFilter) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        myFilterComponent.reset();
        myActionsTree.reset(myKeymap, myQuicklists);
        myFilteringPanel.showPopup(searchToolbar, e.getInputEvent().getComponent());
      }
    });
    group.add(new AnAction(KeyMapBundle.message("filter.clear.action.text"),
                           KeyMapBundle.message("filter.shortcut.action.description"), AllIcons.Actions.GC) {
      @Override
      public void update(@NotNull AnActionEvent event) {
        boolean enabled = null != myFilteringPanel.getShortcut();
        Presentation presentation = event.getPresentation();
        presentation.setEnabled(enabled);
        presentation.setIcon(enabled ? AllIcons.Actions.Cancel : EmptyIcon.ICON_16);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
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

  @Override
  public void dispose() {
    super.dispose();
    myFilteringPanel.hidePopup();
    if (myFilterComponent != null) {
      myFilterComponent.dispose();
    }
  }
}
