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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * UI control for showing difference between the gradle and intellij project structure.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {
  
  private final Alarm myToolbarAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  
  private Tree                            myTree;
  private GradleProjectStructureTreeModel myTreeModel;
  private GradleProjectStructureContext   myContext;
  private Object                          myNodeUnderMouse;
  private Balloon                         myToolbar;

  public GradleProjectStructureChangesPanel(@NotNull Project project, @NotNull GradleProjectStructureContext context) {
    super(project, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    myContext = context;
    context.getChangesModel().addListener(new GradleProjectStructureChangeListener() {
      @Override
      public void onChanges(@NotNull final Collection<GradleProjectStructureChange> oldChanges,
                            @NotNull final Collection<GradleProjectStructureChange> currentChanges)
      {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myTreeModel.processObsoleteChanges(ContainerUtil.subtract(oldChanges, currentChanges));
            myTreeModel.processCurrentChanges(currentChanges);
          }
        });
      }
    });
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
    myTreeModel.processCurrentChanges(myContext.getChangesModel().getChanges());
    myTree = new Tree(myTreeModel);
    applyInitialAppearance(myTree, (DefaultMutableTreeNode)myTreeModel.getRoot());

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    result.add(myTree, constraints);
    result.setBackground(myTree.getBackground());

    CustomizationUtil.installPopupHandler(myTree, GradleConstants.ACTION_GROUP_SYNC_TREE, GradleConstants.SYNC_TREE_CONTEXT_MENU_PLACE);
    setupActionHint();
    return result;
  }

  @Override
  protected void updateContent() {
    // TODO den implement
    int i = 1;
  }

  private void setupActionHint() {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    actionManager.addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (event != null && GradleConstants.SYNC_TREE_FLOATING_TOOLBAR_PLACE.equals(event.getPlace())) {
          hideHint();
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
      
      Object activeNode;
      
      @Override
      public void mouseMoved(MouseEvent e) {
        final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
          hideHint();
          return;
        }
        Balloon activeToolbar = myToolbar;
        
        // Do nothing if the toolbar action selection is in progress.
        if (activeToolbar instanceof IdeTooltip.Ui && ((IdeTooltip.Ui)(activeToolbar)).isInside(new RelativePoint(e))) {
          return;
        }
        final Object node = path.getLastPathComponent();
        myNodeUnderMouse = node;
        if (node == activeNode && activeToolbar != null && !activeToolbar.isDisposed()) {
          return;
        }
        hideHint();
        if (!actionManager.isActionPopupStackEmpty()) {
          return;
        }
        toolbar.updateActionsImmediately();
        if (!toolbar.hasVisibleActions()) {
          activeNode = null;
          return;
        }
        activeNode = node;
        final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(toolbarComponent)
          .setFillColor(myTree.getBackground())
          .createBalloon();
        Disposer.register(getProject(), balloon);
        Point hintPosition = GradleUtil.getHintPosition((GradleProjectStructureNode<?>)node, myTree);
        myToolbar = balloon;
        balloon.show(new RelativePoint(myTree, hintPosition), Balloon.Position.below);
        startToolbarTracking();
      }
    });
    myTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        hideHint();
      }
    });
  }

  private void startToolbarTracking() {
    myToolbarAlarm.cancelAllRequests();
    final int delayMillis = 300;
    myToolbarAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myToolbar == null) {
          return;
        }
        final Point location = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(location, GradleProjectStructureChangesPanel.this);
        if (GradleProjectStructureChangesPanel.this.contains(location)) {
          myToolbarAlarm.addRequest(this, delayMillis);
        }
        else {
          hideHint();
        }
      }
    }, delayMillis);
  }
  
  private void hideHint() {
    final Balloon toolbar = myToolbar;
    if (toolbar != null && !toolbar.isDisposed()) {
      toolbar.hide();
      myToolbar = null;
      myToolbarAlarm.cancelAllRequests();
    }
  }
  
  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (GradleDataKeys.SYNC_TREE.is(dataId)) {
      return myTree;
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
      return myNodeUnderMouse;
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
