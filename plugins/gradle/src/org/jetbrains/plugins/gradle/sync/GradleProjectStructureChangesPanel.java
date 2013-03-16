package org.jetbrains.plugins.gradle.sync;

import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleConfigNotifier;
import org.jetbrains.plugins.gradle.config.GradleLocalSettings;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.notification.GradleConfigNotificationManager;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;

/**
 * UI control for showing difference between the gradle and intellij project structure.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {
  
  private static final int COLLAPSE_STATE_PROCESSING_DELAY_MILLIS = 200;

  private static final Comparator<TreePath> PATH_COMPARATOR = new Comparator<TreePath>() {
    @Override
    public int compare(TreePath o1, TreePath o2) {
      return o2.getPathCount() - o1.getPathCount();
    }
  };

  private final Alarm            myCollapseStateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final List<JComponent> myToolbarControls    = new ArrayList<JComponent>();

  /** Holds list of paths which 'expand/collapse' state should be restored. */
  private final Set<TreePath> myPathsToProcessCollapseState = ContainerUtilRt.newHashSet();

  private final GradleLocalSettings mySettings;

  private                Tree                            myTree;
  private                GradleProjectStructureTreeModel myTreeModel;
  private GradleProjectStructureContext myContext;
  private Object  myNodeUnderMouse;
  private boolean mySuppressCollapseTracking;

  public GradleProjectStructureChangesPanel(@NotNull Project project,
                                            @NotNull GradleProjectStructureContext context)
  {
    super(project, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    myContext = context;
    myToolbarControls.add(new GradleProjectStructureFiltersPanel());
    mySettings = GradleLocalSettings.getInstance(project);
    initContent();

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(GradleConfigNotifier.TOPIC, new GradleConfigNotifier() {

      private boolean myRefresh;
      private boolean myInBulk;

      @Override
      public void onBulkChangeStart() {
        myInBulk = true;
      }

      @Override
      public void onBulkChangeEnd() {
        myInBulk = false;
        if (myRefresh) {
          myRefresh = false;
          refreshAll();
        }
      }

      @Override public void onLinkedProjectPathChange(@Nullable String oldPath, @Nullable String newPath) { refreshAll(); }
      @Override public void onPreferLocalGradleDistributionToWrapperChange(boolean preferLocalToWrapper) { refreshAll(); }
      @Override public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath) { refreshAll(); }
      @Override public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) { refreshAll(); }
      @Override public void onUseAutoImportChange(boolean oldValue, boolean newValue) {
        if (newValue) {
          update();
        }
      }

      private void refreshAll() {
        if (myInBulk) {
          myRefresh = true;
          return;
        }
        GradleUtil.refreshProject(getProject(), new Consumer<String>() {
          @Override
          public void consume(String s) {
            GradleConfigNotificationManager notificationManager
              = ServiceManager.getService(getProject(), GradleConfigNotificationManager.class);
            notificationManager.processRefreshError(s);
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              @Override
              public void run() {
                update();
              }
            });
          }
        });
        update();
      }
    });
  }

  @NotNull
  public GradleProjectStructureTreeModel getTreeModel() {
    return myTreeModel;
  }

  @NotNull
  @Override
  protected JComponent buildContent() {
    JPanel result = new JPanel(new GridBagLayout());
    myTreeModel = new GradleProjectStructureTreeModel(getProject(), myContext, false);
    myTree = new Tree(myTreeModel);
    myTree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        if (!mySuppressCollapseTracking) {
          mySettings.getWorkingExpandStates().put(getPath(event.getPath()), true);
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        if (!mySuppressCollapseTracking) {
          mySettings.getWorkingExpandStates().put(getPath(event.getPath()), false);
        }
      }
    });
    myTreeModel.addTreeModelListener(new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        scheduleCollapseStateAppliance(e.getTreePath());
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        scheduleCollapseStateAppliance(e.getTreePath());
      }
    });
    new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);
    TreeUtil.installActions(myTree);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    result.add(myTree, constraints);
    result.setBackground(myTree.getBackground());

    CustomizationUtil.installPopupHandler(myTree, GradleConstants.ACTION_GROUP_SYNC_TREE, GradleConstants.SYNC_TREE_CONTEXT_MENU_PLACE);
    myTree.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
          return;
        }
        myNodeUnderMouse = path.getLastPathComponent();
      }
    });
    return result;
  }

  @NotNull
  @Override
  protected List<JComponent> getToolbarControls() {
    return myToolbarControls;
  }

  @Override
  protected void updateContent() {
    myTreeModel.rebuild();
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
      return myNodeUnderMouse;
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return GradleConstants.HELP_TOPIC_TOOL_WINDOW;
    }
    else {
      return super.getData(dataId);
    }
  }

  /**
   * Schedules 'collapse/expand' state restoring for the given path. We can't do that immediately from the tree model listener
   * as there is a possible case that other listeners have not been notified about the model state change, hence, attempt to define
   * 'collapse/expand' state may bring us to the inconsistent state.
   * 
   * @param path  target path
   */
  private void scheduleCollapseStateAppliance(@NotNull TreePath path) {
    myPathsToProcessCollapseState.add(path);
    myCollapseStateAlarm.cancelAllRequests();
    myCollapseStateAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        // We assume that the paths collection is modified only from the EDT, so, ConcurrentModificationException doesn't have
        // a chance.
        // Another thing is that we sort the paths in order to process the longest first. That is related to the JTree specifics
        // that it automatically expands parent paths on child path expansion.
        List<TreePath> paths = new ArrayList<TreePath>(myPathsToProcessCollapseState);
        myPathsToProcessCollapseState.clear();
        Collections.sort(paths, PATH_COMPARATOR);
        for (TreePath treePath : paths) {
          applyCollapseState(treePath);
        }
        final TreePath rootPath = new TreePath(myTreeModel.getRoot());
        if (myTree.isCollapsed(rootPath)) {
          myTree.expandPath(rootPath);
        }
      }
    }, COLLAPSE_STATE_PROCESSING_DELAY_MILLIS);
  }

  /**
   * Applies stored 'collapse/expand' state to the node located at the given path.
   * 
   * @param path  target path
   */
  private void applyCollapseState(@NotNull TreePath path) {
    final String key = getPath(path);
    final Boolean expanded = mySettings.getWorkingExpandStates().get(key);
    if (expanded == null) {
      return;
    }
    boolean s = mySuppressCollapseTracking;
    mySuppressCollapseTracking = true;
    try {
      if (expanded) {
        myTree.expandPath(path);
      }
      else {
        myTree.collapsePath(path);
      }
    }
    finally {
      mySuppressCollapseTracking = s;
    }
  }

  @NotNull
  private static String getPath(@NotNull TreePath path) {
    StringBuilder buffer = new StringBuilder();
    for (TreePath current = path; current != null; current = current.getParentPath()) {
      buffer.append(current.getLastPathComponent().toString()).append(GradleUtil.PATH_SEPARATOR);
    }
    buffer.setLength(buffer.length() - 1);
    return buffer.toString();
  }
}
