package org.jetbrains.plugins.gradle.sync;

import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
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

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventObject;
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
  private LightweightHint                 myHint;

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
          hideHind();
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
    toolbarComponent.setBorder(new ColoredSideBorder(foreground, foreground, foreground, foreground, 1));
    
    myTree.addMouseMotionListener(new MouseMotionAdapter() {
      
      Object activeNode;
      
      @Override
      public void mouseMoved(MouseEvent e) {
        final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
          return;
        }
        final Object node = path.getLastPathComponent();
        myNodeUnderMouse = node;
        LightweightHint hint = myHint;
        if (node == activeNode && hint.isVisible()) {
          return;
        }
        hideHind();
        if (!actionManager.isActionPopupStackEmpty()) {
          return;
        }
        toolbar.updateActionsImmediately();
        if (!toolbar.hasVisibleActions()) {
          activeNode = null;
          return;
        }
        activeNode = node;
        final LightweightHint lightweightHint = new LightweightHint(toolbarComponent);
        lightweightHint.addHintListener(new HintListener() {
          @Override
          public void hintHidden(EventObject event) {
            activeNode = null;
          }
        });
        final Rectangle bounds = myTree.getPathBounds(path);
        if (bounds == null) {
          assert false;
          return;
        }
        final Icon icon = ((GradleProjectStructureNode)node).getDescriptor().getOpenIcon();
        int xAdjustment = 0;
        if (icon != null) {
          xAdjustment = icon.getIconWidth();
        }
        lightweightHint.show(myTree, bounds.x + xAdjustment, bounds.y + bounds.height, myTree, new HintHint(e));
        myHint = lightweightHint;
        startToolbarTracking();
      }
    });
    myTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Hide hint on context menu opening.
        if (e.isPopupTrigger()) {
          hideHind();
        }
      }
    });
  }

  private void startToolbarTracking() {
    myToolbarAlarm.cancelAllRequests();
    final int delayMillis = 300;
    myToolbarAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myHint == null) {
          return;
        }
        final Point location = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(location, GradleProjectStructureChangesPanel.this);
        if (GradleProjectStructureChangesPanel.this.contains(location)) {
          myToolbarAlarm.addRequest(this, delayMillis);
        }
        else {
          hideHind();
        }
      }
    }, delayMillis);
  }
  
  private void hideHind() {
    final LightweightHint hint = myHint;
    if (hint != null && hint.isVisible()) {
      hint.hide();
      myToolbarAlarm.cancelAllRequests();
    }
  }
  
  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (GradleDataKeys.SYNC_TREE_SELECTED_NODE.is(dataId)) {
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
