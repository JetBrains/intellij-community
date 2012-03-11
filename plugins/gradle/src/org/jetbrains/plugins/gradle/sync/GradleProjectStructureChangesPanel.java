package org.jetbrains.plugins.gradle.sync;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleUiListener;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * UI control for showing difference between the gradle and intellij project structure.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {

  private static final int TOOLTIP_DELAY_MILLIS = 500;

  private final Alarm            myToolbarAppearanceAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Alarm            myToolbarTrackingAlarm   = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final List<JComponent> myToolbarControls        = new ArrayList<JComponent>();
  
  private Tree                            myTree;
  private GradleProjectStructureTreeModel myTreeModel;
  private GradleProjectStructureContext   myContext;
  private Object                          myNodeUnderMouse;
  private Object                          myNodeWithActiveToolbar;
  private Balloon                         myToolbar;
  private boolean                         mySuppressToolbar;

  public GradleProjectStructureChangesPanel(@NotNull Project project, @NotNull GradleProjectStructureContext context) {
    super(project, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    myContext = context;
    myToolbarControls.add(new GradleProjectStructureFiltersPanel());
    initContent();
  }

  @NotNull
  public GradleProjectStructureTreeModel getTreeModel() {
    return myTreeModel;
  }

  @NotNull
  @Override
  protected JComponent buildContent() {
    JPanel result = new JPanel(new GridBagLayout());
    myTreeModel = new GradleProjectStructureTreeModel(getProject(), myContext);
    myTree = new Tree(myTreeModel);
    applyInitialAppearance(myTree, (DefaultMutableTreeNode)myTreeModel.getRoot());

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    result.add(myTree, constraints);
    result.setBackground(myTree.getBackground());

    CustomizationUtil.installPopupHandler(myTree, GradleConstants.ACTION_GROUP_SYNC_TREE, GradleConstants.SYNC_TREE_CONTEXT_MENU_PLACE);
    setupToolbar();
    return result;
  }

  @NotNull
  @Override
  protected List<JComponent> getToolbarControls() {
    return myToolbarControls;
  }

  @Override
  protected void updateContent() {
  }

  private void setupToolbar() {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    actionManager.addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (event != null && GradleConstants.SYNC_TREE_FLOATING_TOOLBAR_PLACE.equals(event.getPlace())) {
          hideToolbar();
        }
      }
    }, getProject());
    
    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(GradleConstants.ACTION_GROUP_SYNC_TREE);
    final ActionToolbar toolbar = actionManager.createActionToolbar(GradleConstants.SYNC_TREE_FLOATING_TOOLBAR_PLACE, actionGroup, true);
    toolbar.setTargetComponent(this);
    final JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setOpaque(true);
    final Color foreground = myTree.getForeground();
    toolbarComponent.setForeground(foreground);
    toolbarComponent.setBackground(myTree.getBackground());
    
    myTree.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
          return;
        }
        final GradleProjectStructureNode<?> node = (GradleProjectStructureNode<?>)path.getLastPathComponent();
        myNodeUnderMouse = node;
        scheduleToolbar(node, toolbar, toolbarComponent);
      }
    });
    getProject().getMessageBus().connect(getProject()).subscribe(GradleUiListener.TOPIC, new GradleUiListener() {
      @Override
      public void beforeConflictUiShown() {
        mySuppressToolbar = true;
        hideToolbar();
      }

      @Override
      public void afterConflictUiShown() {
        mySuppressToolbar = false;
        hideToolbar();
      }
    });
  }

  /**
   * The general idea is to do the following:
   * <pre>
   * <ol>
   *   <li>Detected that the mouse is located over particular node;</li>
   *   <li>Show toolbar for the target node after particular delay;</li>
   * </ol>
   * </pre>
   * <p/>
   * This method schedules toolbar for the given node.
   *
   * @param node  target node for which a toolbar should be shown
   */
  private void scheduleToolbar(final @NotNull GradleProjectStructureNode<?> node,
                               final @NotNull ActionToolbar toolbar,
                               final @NotNull JComponent toolbarComponent)
  {
    if (mySuppressToolbar || node == myNodeWithActiveToolbar) {
      return;
    }
    myToolbarAppearanceAlarm.cancelAllRequests();
    myToolbarAppearanceAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myNodeUnderMouse != node) {
          return;
        }
        final Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(mouseLocation, myTree);
        final TreePath path = myTree.getPathForLocation(mouseLocation.x, mouseLocation.y);
        if (path == null) {
          hideToolbar();
          return;
        }
        Balloon activeToolbar = myToolbar;

        // Do nothing if the toolbar action selection is in progress.
        if (activeToolbar instanceof IdeTooltip.Ui && ((IdeTooltip.Ui)(activeToolbar)).isInside(new RelativePoint(myTree, mouseLocation))) {
          return;
        }
        hideToolbar();
        final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
        if (!actionManager.isActionPopupStackEmpty()) {
          hideToolbar();
          return;
        }
        toolbar.updateActionsImmediately();
        if (!toolbar.hasVisibleActions()) {
          hideToolbar();
          return;
        }
        final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(toolbarComponent)
          .setFillColor(myTree.getBackground())
          .createBalloon();
        Disposer.register(getProject(), balloon);
        Point hintPosition = GradleUtil.getHintPosition(node, myTree);
        myToolbar = balloon;
        myNodeWithActiveToolbar = node;
        balloon.show(new RelativePoint(myTree, hintPosition), Balloon.Position.below);
        startToolbarTracking();
      }
    }, TOOLTIP_DELAY_MILLIS);
  }
  
  private void startToolbarTracking() {
    myToolbarTrackingAlarm.cancelAllRequests();
    final int delayMillis = 300;
    myToolbarTrackingAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myToolbar == null) {
          return;
        }
        final Point location = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(location, GradleProjectStructureChangesPanel.this);
        if (GradleProjectStructureChangesPanel.this.contains(location)) {
          myToolbarTrackingAlarm.addRequest(this, delayMillis);
        }
        else {
          hideToolbar();
        }
      }
    }, delayMillis);
  }
  
  private void hideToolbar() {
    final Balloon toolbar = myToolbar;
    myNodeWithActiveToolbar = null;
    if (toolbar != null && !toolbar.isDisposed()) {
      toolbar.hide();
      myToolbar = null;
      myToolbarAppearanceAlarm.cancelAllRequests();
      myToolbarTrackingAlarm.cancelAllRequests();
    }
  }
  
  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (GradleDataKeys.SYNC_TREE.is(dataId)) {
      return myTree;
    }
    else if (GradleDataKeys.SYNC_TREE_MODEL.is(dataId)) {
      return myTreeModel;
    }
    else if (GradleDataKeys.SYNC_TREE_SELECTED_NODE.is(dataId)) {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        return null;
      }
      List<GradleProjectStructureNode<?>> result = new ArrayList<GradleProjectStructureNode<?>>();
      for (TreePath path : paths) {
        result.add((GradleProjectStructureNode<?>)path.getLastPathComponent());
      }
      return result;
    }
    else if (GradleDataKeys.SYNC_TREE_NODE_UNDER_MOUSE.is(dataId)) {
      Object result = myNodeWithActiveToolbar;
      if (result == null) {
        result = myNodeUnderMouse;
      }
      return result;
    }
    else {
      return super.getData(dataId);
    }
  }

  private static void applyInitialAppearance(@NotNull Tree tree, @NotNull DefaultMutableTreeNode node) {
    if (node.getUserObject() == GradleConstants.DEPENDENCIES_NODE_DESCRIPTOR) {
      tree.expandPath(new TreePath(node.getPath()));
      return;
    }

    for (int i = 0; i < node.getChildCount(); i++) {
      applyInitialAppearance(tree, (DefaultMutableTreeNode)node.getChildAt(i));
    }
  }
}
