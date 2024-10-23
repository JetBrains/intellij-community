// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.tree;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.TreePopup;
import com.intellij.openapi.ui.popup.TreePopupStep;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.NextStepHandler;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.util.PopupImplUtil;
import com.intellij.ui.tree.FilteringTreeModel;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;

public class TreePopupImpl extends WizardPopup implements TreePopup, NextStepHandler {
  private MyTree myWizardTree;

  private MouseMotionListener myMouseMotionListener;
  private MouseListener myMouseListener;

  private TreePath myShowingChildPath;
  private TreePath myPendingChildPath;
  private FilteringTreeModel myModel;

  public TreePopupImpl(@Nullable Project project,
                       @Nullable JBPopup parent,
                       @NotNull TreePopupStep<Object> aStep,
                       @Nullable Object parentValue) {
    super(project, parent, aStep);
    setParentValue(parentValue);
  }

  @Override
  protected JComponent createContent() {
    myWizardTree = new MyTree();
    myWizardTree.getAccessibleContext().setAccessibleName("WizardTree");
    myModel = FilteringTreeModel.createModel(getTreeStep().getStructure(), this, Invoker.forEventDispatchThread(this), this);
    myWizardTree.setModel(myModel);
    myModel.updateTree(myWizardTree, false, null);
    myWizardTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    Action action = myWizardTree.getActionMap().get("toggleSelectionPreserveAnchor");
    if (action != null) {
      action.setEnabled(false);
    }

    myWizardTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          toggleExpansion(myWizardTree.getAnchorSelectionPath());
        }
      }
    });

    myWizardTree.setRootVisible(getTreeStep().isRootVisible());
    myWizardTree.setShowsRootHandles(true);

    ToolTipManager.sharedInstance().registerComponent(myWizardTree);
    myWizardTree.setCellRenderer(new MyRenderer());

    myMouseMotionListener = new MyMouseMotionListener();
    myMouseListener = new MyMouseListener();

    registerAction("select", KeyEvent.VK_ENTER, 0, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleSelect(true, null);
      }
    });

    registerAction("toggleExpansion", KeyEvent.VK_SPACE, 0, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        toggleExpansion(myWizardTree.getSelectionPath());
      }
    });

    final Action oldExpandAction = getActionMap().get("selectChild");
    getActionMap().put("selectChild", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final TreePath path = myWizardTree.getSelectionPath();
        if (path != null && 0 == myWizardTree.getModel().getChildCount(path.getLastPathComponent())) {
          handleSelect(false, null);
          return;
        }
        oldExpandAction.actionPerformed(e);
      }
    });

    final Action oldCollapseAction = getActionMap().get("selectParent");
    getActionMap().put("selectParent", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final TreePath path = myWizardTree.getSelectionPath();
        if (shouldHidePopup(path)) {
          goBack();
          return;
        }
        oldCollapseAction.actionPerformed(e);
      }
    });

    PopupUtil.applyNewUIBackground(myWizardTree);

    return myWizardTree;
  }

  private boolean shouldHidePopup(TreePath path) {
    if (getParent() == null) return false;
    if (path == null) return false;
    if (!myWizardTree.isCollapsed(path)) return false;
    if (myWizardTree.isRootVisible()) {
      return path.getPathCount() == 1;
    }
    return path.getPathCount() == 2;
  }

  @Override
  protected ActionMap getActionMap() {
    return myWizardTree.getActionMap();
  }

  @Override
  protected InputMap getInputMap() {
    return myWizardTree.getInputMap();
  }

  private void addListeners() {
    myWizardTree.addMouseMotionListener(myMouseMotionListener);
    myWizardTree.addMouseListener(myMouseListener);
  }

  @Override
  public void dispose() {
    myWizardTree.removeMouseMotionListener(myMouseMotionListener);
    myWizardTree.removeMouseListener(myMouseListener);
    super.dispose();
  }

  @Override
  protected boolean beforeShow() {
    addListeners();
    expandAll();
    return super.beforeShow();
  }

  @Override
  protected void afterShow() {
    selectFirstSelectableItem();
  }

  // TODO: not-tested code:
  protected void selectFirstSelectableItem() {
    for (int i = 0; i < myWizardTree.getRowCount(); i++) {
      TreePath path = myWizardTree.getPathForRow(i);
      if (getTreeStep().isSelectable(path.getLastPathComponent(), extractUserObject(path.getLastPathComponent()))) {
        myWizardTree.setSelectionPath(path);
        break;
      }
    }
  }

  public void expandAll() {
    for (int i = 0; i < myWizardTree.getRowCount(); i++) {
      myWizardTree.expandRow(i);
    }
  }

  public void collapseAll() {
    int row = myWizardTree.getRowCount() - 1;
    while (row > 0) {
      myWizardTree.collapseRow(row);
      row--;
    }
  }

  public void scrollToSelection() {
    myWizardTree.scrollPathToVisible(myWizardTree.getSelectionPath());
  }

  private TreePopupStep<Object> getTreeStep() {
    return (TreePopupStep<Object>)myStep;
  }

  private final class MyMouseMotionListener extends MouseMotionAdapter {
    private Point myLastMouseLocation;

    /**
     * this method should be changed only in par with
     * {@link com.intellij.ui.popup.list.ListPopupImpl.MyMouseMotionListener#isMouseMoved(Point)}
     */
    private boolean isMouseMoved(Point location) {
      if (myLastMouseLocation == null) {
        myLastMouseLocation = location;
        return false;
      }

      Point prev = myLastMouseLocation;
      myLastMouseLocation = location;
      return !prev.equals(location);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (!isMouseMoved(e.getLocationOnScreen())) return;

      final TreePath path = getPath(e);
      if (path != null) {
        myWizardTree.setSelectionPath(path);
        notifyParentOnChildSelection();
        if (getTreeStep().isSelectable(path.getLastPathComponent(), extractUserObject(path.getLastPathComponent()))) {
          myWizardTree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          if (myPendingChildPath == null || !myPendingChildPath.equals(path)) {
            myPendingChildPath = path;
            restartTimer();
          }
          return;
        }
      }
      myWizardTree.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

  }

  private TreePath getPath(MouseEvent e) {
    return myWizardTree.getClosestPathForLocation(e.getPoint().x, e.getPoint().y);
  }

  private final class MyMouseListener extends MouseAdapter {

    @Override
    public void mousePressed(MouseEvent e) {
      final TreePath path = getPath(e);
      if (path == null) {
        return;
      }

      if (e.getButton() != MouseEvent.BUTTON1) {
        return;
      }

      final Object selected = path.getLastPathComponent();

      if (getTreeStep().isSelectable(selected, extractUserObject(selected))) {
        handleSelect(true, e);
      }
      else {
        if (!TreeUtil.isLocationInExpandControl(myWizardTree, path, e.getX(), e.getY())) {
          toggleExpansion(path);
        }
      }
    }
  }

  private void toggleExpansion(TreePath path) {
    if (path == null) {
      return;
    }

    if (getTreeStep().isSelectable(path.getLastPathComponent(), extractUserObject(path.getLastPathComponent()))) {
      if (myWizardTree.isExpanded(path)) {
        myWizardTree.collapsePath(path);
      }
      else {
        myWizardTree.expandPath(path);
      }
    }
  }

  private void handleSelect(boolean handleFinalChoices, MouseEvent e) {
    final boolean pathIsAlreadySelected = myShowingChildPath != null && myShowingChildPath.equals(myWizardTree.getSelectionPath());
    if (pathIsAlreadySelected) return;

    myPendingChildPath = null;

    Object selected = myWizardTree.getLastSelectedPathComponent();
    if (selected != null) {
      final Object userObject = extractUserObject(selected);
      if (getTreeStep().isSelectable(selected, userObject)) {
        disposeChildren();

        final boolean hasNextStep = myStep.hasSubstep(userObject);
        if (!hasNextStep && !handleFinalChoices) {
          myShowingChildPath = null;
          return;
        }

        PopupStep<?> queriedStep;
        try (AccessToken ignore = PopupImplUtil.prohibitFocusEventsInHandleSelect();
             AccessToken ignore2 = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
          queriedStep = myStep.onChosen(userObject, handleFinalChoices);
        }
        if (queriedStep == PopupStep.FINAL_CHOICE || !hasNextStep) {
          setFinalRunnable(myStep.getFinalRunnable());
          setOk(true);
          disposeAllParents(e);
        }
        else {
          myShowingChildPath = myWizardTree.getSelectionPath();
          handleNextStep(queriedStep, myShowingChildPath);
          myShowingChildPath = null;
        }
      }
    }
  }

  @Override
  public void handleNextStep(PopupStep nextStep, Object parentValue) {
    final Rectangle pathBounds = myWizardTree.getPathBounds(myWizardTree.getSelectionPath());
    final Point point = new RelativePoint(myWizardTree, new Point(getContent().getWidth() + 2, (int) pathBounds.getY())).getScreenPoint();
    myChild = createPopup(this, nextStep, parentValue);
    myChild.show(getContent(), point.x - STEP_X_PADDING, point.y, true);
  }

  private final class MyRenderer extends NodeRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final boolean shouldPaintSelected = (getTreeStep().isSelectable(value, extractUserObject(value)) && selected) || (getTreeStep().isSelectable(value, extractUserObject(value)) && hasFocus);
      final boolean shouldPaintFocus =
        !getTreeStep().isSelectable(value, extractUserObject(value)) && selected || shouldPaintSelected || hasFocus;

      super.customizeCellRenderer(tree, value, shouldPaintSelected, expanded, leaf, row, shouldPaintFocus);
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (ExperimentalUI.isNewUI()) {
        size.height = JBUI.CurrentTheme.Tree.rowHeight();
      }
      return size;
    }
  }

  @Override
  protected void process(KeyEvent aEvent) {
    myWizardTree.processKeyEvent(aEvent);
  }

  private Object extractUserObject(Object aNode) {
    Object object = ((DefaultMutableTreeNode) aNode).getUserObject();
    if (object instanceof FilteringTreeStructure.FilteringNode) {
      return ((FilteringTreeStructure.FilteringNode) object).getDelegate();
    }
    return object;
  }

  private final class MyTree extends SimpleTree {
    @Override
    public void processKeyEvent(KeyEvent e) {
      e.setSource(this);
      super.processKeyEvent(e);
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension pref = super.getPreferredSize();
      return new Dimension(pref.width + 10, pref.height);
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);

      Rectangle visibleRect = getVisibleRect();
      int rowForLocation = getClosestRowForLocation(0, visibleRect.y);
      int limit = rowForLocation + TreeUtil.getVisibleRowCount(this) + 1;
      for (int i = rowForLocation; i < limit; i++) {
        final TreePath eachPath = getPathForRow(i);
        if (eachPath == null) continue;

        final Object lastPathComponent = eachPath.getLastPathComponent();
        final boolean hasNextStep = getTreeStep().hasSubstep(extractUserObject(lastPathComponent));
        if (!hasNextStep) continue;

        Icon icon = AllIcons.Icons.Ide.NextStep;
        final Rectangle rec = getPathBounds(eachPath);
        int x = getSize().width - icon.getIconWidth() - 1;
        int y = rec.y + (rec.height - icon.getIconWidth()) / 2;
        icon.paintIcon(this, g, x, y);
      }
    }

    @Override
    protected void configureUiHelper(final TreeUIHelper helper) {
      if (mySpeedSearch != null) {
        mySpeedSearch.installSupplyTo(this, false);
      }
    }
  }

  @Override
  protected void onAutoSelectionTimer() {
    handleSelect(false, null);
  }

  @Override
  protected JComponent getPreferredFocusableComponent() {
    return myWizardTree;
  }

  @Override
  protected void onSpeedSearchPatternChanged() {
    myModel.updateTree(myWizardTree, mySpeedSearch.isHoldingFilter(), null);
    selectFirstSelectableItem();
  }

  @Override
  protected void onChildSelectedFor(Object value) {
    TreePath path = (TreePath) value;
    if (myWizardTree.getSelectionPath() != path) {
      myWizardTree.setSelectionPath(path);
    }
  }

  @Override
  public boolean isModalContext() {
    return true;
  }

}
