/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.icons.AllIcons;
import com.intellij.ide.favoritesTreeView.AbstractFavoritesListProvider;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.SingleAlarm;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointItemsTreeController;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointsSimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.*;
import java.util.List;

public class BreakpointsFavoriteListProvider extends AbstractFavoritesListProvider<Object>
  implements BreakpointPanelProvider.BreakpointsListener {

  private final List<BreakpointPanelProvider> myBreakpointPanelProviders;
  private final BreakpointItemsTreeController myTreeController;
  private final List<XBreakpointGroupingRule> myRulesAvailable = new ArrayList<>();

  private final Set<XBreakpointGroupingRule> myRulesEnabled = new TreeSet<>(XBreakpointGroupingRule.PRIORITY_COMPARATOR);

  private final SingleAlarm myRebuildAlarm = new SingleAlarm(this::updateChildren, 100);
  private final FavoritesManager myFavoritesManager;

  public BreakpointsFavoriteListProvider(Project project, FavoritesManager favoritesManager) {
    super(project, "Breakpoints");
    myBreakpointPanelProviders = XBreakpointUtil.collectPanelProviders();
    myFavoritesManager = favoritesManager;
    myTreeController = new BreakpointItemsTreeController(myRulesAvailable);
    myTreeController.setTreeView(new BreakpointsSimpleTree(myProject, myTreeController));
    for (final BreakpointPanelProvider provider : myBreakpointPanelProviders) {
      provider.addListener(this, myProject, myProject);
      provider.createBreakpointsGroupingRules(myRulesAvailable);
    }
    updateChildren();
  }

  @Override
  public void breakpointsChanged() {
    myRebuildAlarm.cancelAndRequest();
  }

  private void getEnabledGroupingRules(Collection<XBreakpointGroupingRule> rules) {
    rules.clear();
    XBreakpointsDialogState settings = ((XBreakpointManagerImpl)XDebuggerManager.getInstance(myProject).getBreakpointManager()).getBreakpointsDialogSettings();

    for (XBreakpointGroupingRule rule : myRulesAvailable) {
      if (rule.isAlwaysEnabled() || (settings != null && settings.getSelectedGroupingRules().contains(rule.getId()) ) ) {
        rules.add(rule);
      }
    }
  }

  private void updateChildren() {
    if (myProject.isDisposed()) return;
    myChildren.clear();
    List<BreakpointItem> items = new ArrayList<>();
    for (final BreakpointPanelProvider provider : myBreakpointPanelProviders) {
      provider.provideBreakpointItems(myProject, items);
    }
    getEnabledGroupingRules(myRulesEnabled);
    myTreeController.setGroupingRules(myRulesEnabled);
    myTreeController.rebuildTree(items);


    CheckedTreeNode root = myTreeController.getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      TreeNode child = root.getChildAt(i);
      if (child instanceof DefaultMutableTreeNode) {
        replicate((DefaultMutableTreeNode)child, myNode, myChildren);
      }
    }
    myFavoritesManager.fireListeners(getListName(myProject));
  }

  private void replicate(DefaultMutableTreeNode source, AbstractTreeNode destination, final List<AbstractTreeNode<Object>> destinationChildren) {
    final ArrayList<AbstractTreeNode<Object>> copyChildren = new ArrayList<>();
    AbstractTreeNode<Object> copy = new AbstractTreeNode<Object>(myProject, source.getUserObject()) {
      @NotNull
      @Override
      public Collection<? extends AbstractTreeNode> getChildren() {
        return copyChildren;
      }

      @Override
      protected void update(PresentationData presentation) {
      }
    };

    for (int i = 0; i < source.getChildCount(); i++) {
      final TreeNode treeNode = source.getChildAt(i);
      if (treeNode instanceof DefaultMutableTreeNode) {
        final DefaultMutableTreeNode sourceChild = (DefaultMutableTreeNode)treeNode;
        replicate(sourceChild, copy, copyChildren);
      }
    }
    if (checkNavigatable(copy)) {
      destinationChildren.add(copy);
      copy.setParent(destination);
    }
  }

  private static boolean checkNavigatable(AbstractTreeNode<?> node) {
    if (node.getValue() instanceof Navigatable && ((Navigatable)node.getValue()).canNavigate()) {
      return true;
    }
    return node.getChildren().stream().anyMatch(BreakpointsFavoriteListProvider::checkNavigatable);
  }

  @Nullable
  @Override
  public String getCustomName(@NotNull CommonActionsPanel.Buttons type) {
    switch (type) {
      case EDIT:
        return "Edit breakpoint";
      case REMOVE:
        return "Remove breakpoint";
      default:
        return null;
    }
  }

  @Override
  public boolean willHandle(@NotNull CommonActionsPanel.Buttons type, Project project, @NotNull Set<Object> selectedObjects) {
    if (selectedObjects.size() >= 1 && type == CommonActionsPanel.Buttons.REMOVE) {
      for (Object selectedObject : selectedObjects) {
        if (!(((AbstractTreeNode)selectedObject).getValue() instanceof BreakpointItem)) {
          return false; // Not all selected items are breakpoints
        }
      }
      return true;
    }
    return selectedObjects.size() == 1 && type == CommonActionsPanel.Buttons.EDIT &&
           ((AbstractTreeNode)selectedObjects.iterator().next()).getValue() instanceof BreakpointItem;
  }

  @Override
  public void handle(@NotNull CommonActionsPanel.Buttons type, Project project, @NotNull Set<Object> selectedObjects, JComponent component) {
    Rectangle bounds = component.getBounds();
    if (component instanceof JTree) {
      JTree tree = (JTree)component;
      bounds = tree.getRowBounds(tree.getLeadSelectionRow());
      bounds.y += bounds.height/2;
      bounds = tree.getVisibleRect().intersection(bounds);
    }
    Point whereToShow = new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
    switch (type) {
      case EDIT:
        BreakpointItem breakpointItem = (BreakpointItem)((AbstractTreeNode)selectedObjects.iterator().next()).getValue();
        DebuggerSupport debuggerSupport = XBreakpointUtil.getDebuggerSupport(myProject, breakpointItem);
        if (debuggerSupport == null || breakpointItem == null) return;
        debuggerSupport.getEditBreakpointAction().editBreakpoint(myProject, component, whereToShow, breakpointItem);
        break;
      case REMOVE:
        for (Object selectedObject : selectedObjects) {
          BreakpointItem removeBreakpointItem = (BreakpointItem)((AbstractTreeNode)selectedObject).getValue();
          removeBreakpointItem.removed(myProject);
        }
        break;
      default: break;
    }
  }

  @Override
  public int getWeight() {
    return BREAKPOINTS_WEIGHT;
  }

  @Override
  public void customizeRenderer(ColoredTreeCellRenderer renderer,
                                JTree tree,
                                @NotNull Object value,
                                boolean selected,
                                boolean expanded,
                                boolean leaf,
                                int row,
                                boolean hasFocus) {
    renderer.clear();
    renderer.setIcon(AllIcons.Debugger.Db_set_breakpoint);
    if (value instanceof BreakpointItem) {
      BreakpointItem breakpointItem = (BreakpointItem)value;
      breakpointItem.setupGenericRenderer(renderer, true);
    }
    else if (value instanceof XBreakpointGroup) {
      renderer.append(((XBreakpointGroup)value).getName());
      renderer.setIcon(((XBreakpointGroup)value).getIcon(expanded));
    }
    else if (value instanceof XBreakpointGroupingRule) {
      renderer.append(((XBreakpointGroupingRule)value).getPresentableName());
    }
    else {
      renderer.append(String.valueOf(value));
    }
  }
}