// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.find.FindManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.lang.Language;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.impl.actions.MergeSameLineUsagesAction;
import com.intellij.usages.impl.rules.UsageFilteringRules;
import com.intellij.usages.rules.*;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppJavaExecutorUtil;
import com.intellij.util.concurrency.CoroutineDispatcherBackedExecutor;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.JobKt;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.actionSystem.impl.Utils.createAsyncDataContext;
import static com.intellij.usages.impl.UsageFilteringRuleActions.usageFilteringRuleActions;

public class UsageViewImpl implements UsageViewEx {
  private final int myUniqueIdentifier;
  private static final GroupNode.NodeComparator COMPARATOR = new GroupNode.NodeComparator();
  private static final Logger LOG = Logger.getInstance(UsageViewImpl.class);
  public static final @NonNls String SHOW_RECENT_FIND_USAGES_ACTION_ID = "UsageView.ShowRecentFindUsages";

  private final UsageNodeTreeBuilder myBuilder;
  private final @NotNull CoroutineScope coroutineScope;
  private MyPanel myRootPanel; // accessed in EDT only
  private JTree myTree; // accessed in EDT only
  private final ScheduledFuture<?> myFireEventsFuture;
  private Content myContent;

  private final UsageViewPresentation myPresentation;
  private final UsageTarget[] myTargets;
  private UsageGroupingRule[] myGroupingRules;
  private final UsageFilteringRuleState myFilteringRulesState = UsageFilteringRuleStateService.createFilteringRuleState();
  private final Supplier<? extends UsageSearcher> myUsageSearcherFactory;
  private final @NotNull Project myProject;

  private volatile boolean mySearchInProgress = true;
  private final ExporterToTextFile myTextFileExporter = new ExporterToTextFile(this, getUsageViewSettings());
  private final SingleAlarm updateAlarm;

  private final ExclusionHandlerEx<DefaultMutableTreeNode> myExclusionHandler;
  private final Map<Usage, UsageNode> myUsageNodes = new ConcurrentHashMap<>();
  public static final UsageNode NULL_NODE = new UsageNode(null, NullUsage.INSTANCE);
  private final ButtonPanel myButtonPanel;
  private boolean myNeedUpdateButtons;
  private final JComponent myAdditionalComponent = new JPanel(new BorderLayout());
  private volatile boolean isDisposed;
  private volatile boolean myChangesDetected;
  private @Nullable GroupNode myAutoSelectedGroupNode;

  public static final Comparator<Usage> USAGE_COMPARATOR_BY_FILE_AND_OFFSET = (o1, o2) -> {
    if (o1 == o2) return 0;
    if (o1 == NullUsage.INSTANCE || o1 == null) return -1;
    if (o2 == NullUsage.INSTANCE || o2 == null) return 1;
    int c = compareByFileAndOffset(o1, o2);
    if (c != 0) return c;
    return o1.toString().compareTo(o2.toString());
  };

  @ApiStatus.Internal
  public int getFilteredOutNodeCount() {
    return myBuilder.getFilteredUsagesCount();
  }

  private static int compareByFileAndOffset(@NotNull Usage o1, @NotNull Usage o2) {
    VirtualFile file1 = o1 instanceof UsageInFile ? ((UsageInFile)o1).getFile() : null;
    VirtualFile file2 = o2 instanceof UsageInFile ? ((UsageInFile)o2).getFile() : null;
    if (file1 == null && file2 == null) return 0;
    if (file1 == null) return -1;
    if (file2 == null) return 1;
    if (file1.equals(file2)) {
      return Integer.compare(o1.getNavigationOffset(), o2.getNavigationOffset());
    }
    return VfsUtilCore.compareByPath(file1, file2);
  }

  public static final @NonNls String HELP_ID = "ideaInterface.find";
  private UsageContextPanel myCurrentUsageContextPanel; // accessed in EDT only
  private final List<UsageContextPanel> myAllUsageContextPanels = new ArrayList<>(); // accessed in EDT only
  private UsageContextPanel.Provider myCurrentUsageContextProvider; // accessed in EDT only

  private JPanel myCentralPanel; // accessed in EDT only

  private final @NotNull GroupNode myRoot;
  private final UsageViewTreeModelBuilder myModel;
  private Splitter myPreviewSplitter; // accessed in EDT only
  private volatile ProgressIndicator associatedProgress; // the progress that current find usages is running under

  // true if usages tree is currently expanding or collapsing,
  // either at the end of find usages thanks to the 'expand usages after find' setting, or
  // because the user pressed 'expand all' or 'collapse all' button. During this, some ugly hacks applied
  // to speed up the expanding (see getExpandedDescendants() here and UsageViewTreeCellRenderer.customizeCellRenderer()
  private boolean myExpandingCollapsing;
  private final UsageViewTreeCellRenderer myUsageViewTreeCellRenderer;
  private @Nullable Action myRerunAction;
  private final CoroutineDispatcherBackedExecutor updateRequests;
  private final List<ExcludeListener> myExcludeListeners = ContainerUtil.createConcurrentList();
  private final Set<Pair<Class<? extends PsiReference>, Language>> myReportedReferenceClasses
    = ConcurrentCollectionFactory.createConcurrentSet();

  private Runnable fusRunnable = () -> {
    if (myTree == null) return;
    DataContext dc = DataManager.getInstance().getDataContext(myTree);
    Navigatable[] navigatables = CommonDataKeys.NAVIGATABLE_ARRAY.getData(dc);
    if (navigatables != null) {
      ContainerUtil.filter(navigatables, n -> n.canNavigateToSource() && n instanceof PsiElementUsage).
        forEach(n -> {
          PsiElement psiElement = ((PsiElementUsage)n).getElement();
          if (psiElement != null) {
            UsageViewStatisticsCollector.logItemChosen(getProject(), this, CodeNavigateSource.FindToolWindow, psiElement.getLanguage(),
                                                       n instanceof SimilarUsage);
          }
      });
    }
  };

  @ApiStatus.Internal
  public UsageViewImpl(@NotNull Project project,
                       @NotNull CoroutineScope coroutineScope,
                       @NotNull UsageViewPresentation presentation,
                       UsageTarget @NotNull [] targets,
                       @Nullable Supplier<? extends UsageSearcher> usageSearcherFactory) {
    this.coroutineScope = coroutineScope;
    // fire events every 50 ms, not more often to batch requests
    myUniqueIdentifier = COUNTER.getAndIncrement();
    myFireEventsFuture =
      EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(this::fireEvents, 50, 50, TimeUnit.MILLISECONDS);
    Disposer.register(this, () -> myFireEventsFuture.cancel(false));

    myPresentation = presentation;
    myTargets = targets;
    myUsageSearcherFactory = usageSearcherFactory;
    myProject = project;

    myButtonPanel = new ButtonPanel();

    myModel = new UsageViewTreeModelBuilder(myPresentation, targets);
    myRoot = (GroupNode)myModel.getRoot();

    myGroupingRules = getActiveGroupingRules(project, getUsageViewSettings(), getPresentation());
    myBuilder = new UsageNodeTreeBuilder(myTargets, myGroupingRules, getActiveFilteringRules(myProject), myRoot, myProject);
    myProject.getMessageBus().connect(this).subscribe(UsageFilteringRuleProvider.RULES_CHANGED, this::rulesChanged);

    myUsageViewTreeCellRenderer = new UsageViewTreeCellRenderer(this);
    if (!myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(() -> WriteIntentReadAction.run((Runnable)this::initInEDT));
    }
    myExclusionHandler = new ExclusionHandlerEx<>() {
      @Override
      public boolean isNodeExclusionAvailable(@NotNull DefaultMutableTreeNode node) {
        return node instanceof UsageNode;
      }

      @Override
      public boolean isNodeExcluded(@NotNull DefaultMutableTreeNode node) {
        return ((UsageNode)node).isDataExcluded();
      }

      @Override
      public void excludeNode(@NotNull DefaultMutableTreeNode node) {
        Set<Node> nodes = new HashSet<>();
        TreeUtil.treeNodeTraverser(node).traverse().filter(Node.class).addAllTo(nodes);
        collectParentNodes(node, true, nodes);
        setExcludeNodes(nodes, true, true);
      }

      @Override
      public void excludeNodeSilently(@NotNull DefaultMutableTreeNode node) {
        Set<Node> nodes = new HashSet<>();
        TreeUtil.treeNodeTraverser(node).traverse().filter(Node.class).addAllTo(nodes);
        collectParentNodes(node, true, nodes);
        setExcludeNodes(nodes, true, false);
      }

      // include the parent if all its children (except the "node" itself) excluded flags are "almostAllChildrenExcluded"
      private void collectParentNodes(@NotNull DefaultMutableTreeNode node,
                                      boolean almostAllChildrenExcluded,
                                      @NotNull Set<? super Node> nodes) {
        TreeNode parent = node.getParent();
        if (parent == myRoot || !(parent instanceof GroupNode parentNode)) return;
        List<Node> otherNodes;
        synchronized (parentNode) {
          otherNodes = ContainerUtil.filter(parentNode.getChildren(), n -> n.isExcluded() != almostAllChildrenExcluded);
        }
        if (otherNodes.size() == 1 && otherNodes.get(0) == node) {
          nodes.add(parentNode);
          collectParentNodes(parentNode, almostAllChildrenExcluded, nodes);
        }
      }

      private void setExcludeNodes(@NotNull Set<? extends Node> nodes, boolean excluded, boolean updateImmediately) {
        Set<Usage> affectedUsages = new LinkedHashSet<>();
        for (Node node : nodes) {
          Object userObject = node.getUserObject();
          if (userObject instanceof Usage) {
            affectedUsages.add((Usage)userObject);
          }
          node.setExcluded(excluded, edtFireTreeNodesChangedQueue);
        }

        if (updateImmediately) {
          updateImmediatelyNodesUpToRoot(nodes);

          for (ExcludeListener listener : myExcludeListeners) {
            listener.fireExcluded(affectedUsages, excluded);
          }
        }
      }

      @Override
      public void includeNode(@NotNull DefaultMutableTreeNode node) {
        Set<Node> nodes = new HashSet<>();
        TreeUtil.treeNodeTraverser(node).traverse().filter(Node.class).addAllTo(nodes);
        collectParentNodes(node, false, nodes);
        setExcludeNodes(nodes, false, true);
      }

      @Override
      public boolean isActionEnabled(boolean isExcludeAction) {
        return getPresentation().isExcludeAvailable();
      }

      @Override
      public void onDone(boolean isExcludeAction) {
        ThreadingAssertions.assertEventDispatchThread();
        if (myRootPanel.hasNextOccurence()) {
          myRootPanel.goNextOccurence();
        }
      }
    };
    updateRequests = AppJavaExecutorUtil
      .createBoundedTaskExecutor("Usage View Update Requests", coroutineScope, JobSchedulerImpl.getJobPoolParallelism());
    updateAlarm = SingleAlarm.singleEdtAlarm(300, coroutineScope, () -> {
      if (!isDisposed()) {
        updateImmediately();
      }
    });
  }

  @Override
  @ApiStatus.Internal
  @IntellijInternalApi
  public int getId() {
    return myUniqueIdentifier;
  }
  
  @ApiStatus.Internal
  public JTree getTree() {
    return myTree;
  }

  private void initInEDT() {
    ThreadingAssertions.assertEventDispatchThread();
    if (isDisposed()) return;
    myTree = new Tree(myModel) {
      {
        ToolTipManager.sharedInstance().registerComponent(this);
      }

      @Override
      public boolean isRootVisible() {
        return false;  // to avoid re-building model when it calls setRootVisible(true)
      }

      @Override
      public String getToolTipText(MouseEvent e) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          if (getCellRenderer() instanceof UsageViewTreeCellRenderer) {
            return UsageViewTreeCellRenderer.getTooltipFromPresentation(path.getLastPathComponent());
          }
        }
        return null;
      }

      @Override
      public boolean isPathEditable(@NotNull TreePath path) {
        return path.getLastPathComponent() instanceof UsageViewTreeModelBuilder.TargetsRootNode;
      }

      // hack to avoid quadratic expandAll()
      @Override
      public Enumeration<TreePath> getExpandedDescendants(TreePath parent) {
        return myExpandingCollapsing ? Collections.emptyEnumeration() : super.getExpandedDescendants(parent);
      }
    };
    myTree.setName("UsageViewTree");

    myRootPanel = new MyPanel(myTree);
    Disposer.register(this, myRootPanel);
    myTree.setModel(myModel);

    myRootPanel.setLayout(new BorderLayout());

    SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(false, true);
    myRootPanel.add(toolWindowPanel, BorderLayout.CENTER);

    toolWindowPanel.setToolbar(createActionsToolbar());

    myCentralPanel = new JPanel(new BorderLayout());
    setupCentralPanel();

    initTree();
    toolWindowPanel.setContent(myCentralPanel);

    myTree.setCellRenderer(myUsageViewTreeCellRenderer);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (!isDisposed()) {
        collapseAll();
      }
    });

    UsageModelTracker myModelTracker = new UsageModelTracker(getProject());
    Disposer.register(this, myModelTracker);

    myModelTracker.addListener(isPropertyChange -> {
      if (!isPropertyChange) {
        myChangesDetected = true;
      }
      updateLater();
    }, this);

    if (myPresentation.isShowCancelButton()) {
      addButtonToLowerPane(this::close, UsageViewBundle.message("usage.view.cancel.button"));
    }

    myTree.getSelectionModel().addTreeSelectionListener(__ -> {
      //noinspection SSBasedInspection
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!isDisposed()) {
          updateOnSelectionChanged(myProject);
          myNeedUpdateButtons = true;
        }
      });
    });
    myModel.addTreeModelListener(new TreeModelAdapter() {
      @Override
      protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
        myNeedUpdateButtons = true;
      }
    });

    myTree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (rulesChanged) {
          rulesChanged = false;
          rulesChanged();
        }
      }
    });
  }

  @ApiStatus.Internal
  public @NotNull UsageViewSettings getUsageViewSettings() {
    return UsageViewSettings.getInstance();
  }

  // nodes just changed: parent node -> changed child
  // this collection is needed for firing javax.swing.tree.DefaultTreeModel.nodesChanged() events in batch
  // has to be linked because events for child nodes should be fired after events for parent nodes
  private final MultiMap<Node, Node> fireTreeNodesChangedMap = MultiMap.createLinked(); // guarded by fireTreeNodesChangedMap

  private final Consumer<Node> edtFireTreeNodesChangedQueue = node -> {
    if (!getPresentation().isDetachedMode()) {
      synchronized (fireTreeNodesChangedMap) {
        Node parent = (Node)node.getParent();
        if (parent != null) {
          fireTreeNodesChangedMap.putValue(parent, node);
        }
      }
    }
  };

  /**
   * Type of change that occurs in the GroupNode.myChildren
   * and has to be applied to the swing children list
   */
  enum NodeChangeType {
    ADDED, REMOVED, REPLACED
  }

  /**
   * Collection of info about a change that occurs in the GroupNode.myChildren
   * and has to be applied to the swing children list including affected nodes and the type of the change
   */
  static class NodeChange {
    private final @NotNull NodeChangeType nodeChangeType;
    /**
     * The one that was replaced or removed, or a parent for the added node
     */
    private final @NotNull Node parentNode;

    /**
     * The one that was added or the one that replaced the first
     */
    private final @Nullable Node childNode;

    NodeChange(@NotNull NodeChangeType nodeChangeType, @NotNull Node parentNode, @Nullable Node childNode) {
      this.nodeChangeType = nodeChangeType;
      this.parentNode = parentNode;
      this.childNode = childNode;
    }

    @NotNull Node getParentNode() {
      return parentNode;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      NodeChange that = (NodeChange)o;
      return nodeChangeType == that.nodeChangeType &&
             Objects.equals(parentNode, that.parentNode) &&
             Objects.equals(childNode, that.childNode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeChangeType, parentNode, childNode);
    }
  }


  /**
   * Set of node changes coming from the model to be applied to the Swing elements
   */
  private final Set<NodeChange> modelToSwingNodeChanges = new LinkedHashSet<>(); //guarded by modelToSwingNodeChanges

  private final Consumer<NodeChange> edtModelToSwingNodeChangesQueue = (@NotNull NodeChange parent) -> {
    if (!getPresentation().isDetachedMode()) {
      synchronized (modelToSwingNodeChanges) {
        modelToSwingNodeChanges.add(parent);
      }
    }
  };


  /**
   * for each node synchronize its model children (com.intellij.usages.impl.GroupNode.getChildren())
   * and its Swing children (javax.swing.tree.DefaultMutableTreeNode.children)
   * by applying correspondent changes from one to another and by issuing corresponding DefaultMutableTreeNode event notifications
   * (fireTreeNodesInserted/nodesWereRemoved/fireTreeStructureChanged)
   * this method is called regularly every 50ms to fire events in batch
   */
  private void fireEvents() {
    ThreadingAssertions.assertEventDispatchThread();

    syncModelWithSwingNodes();
    fireEventsForChangedNodes();
  }

  /**
   * group nodes from changedNodesToFire by their parents
   * and issue corresponding javax.swing.tree.DefaultTreeModel.fireTreeNodesChanged()
   */
  private void fireEventsForChangedNodes() {
    IntList indicesToFire = new IntArrayList();
    List<Node> nodesToFire = new ArrayList<>();

    List<Map.Entry<Node, Collection<Node>>> changed;
    synchronized (fireTreeNodesChangedMap) {
      changed = new ArrayList<>(fireTreeNodesChangedMap.entrySet());
      fireTreeNodesChangedMap.clear();
    }
    for (Map.Entry<Node, Collection<Node>> entry : changed) {
      Node parentNode = entry.getKey();
      Collection<Node> childrenToUpdate = entry.getValue();
      for (int i = 0; i < parentNode.getChildCount(); i++) {
        Node childNode = (Node)parentNode.getChildAt(i);
        if (childrenToUpdate.contains(childNode)) {
          nodesToFire.add(childNode);
          indicesToFire.add(i);
        }
      }

      myModel.fireTreeNodesChanged(parentNode, myModel.getPathToRoot(parentNode), indicesToFire.toIntArray(),
                                   nodesToFire.toArray(new Node[0]));
      indicesToFire.clear();
      nodesToFire.clear();
    }
  }

  /**
   * Iterating over all changes that come from the model children list in a GroupNode
   * and applying all those changes to the swing list of children to synchronize those
   */
  private void syncModelWithSwingNodes() {
    List<NodeChange> nodeChanges;
    synchronized (modelToSwingNodeChanges) {
      nodeChanges = new ArrayList<>(modelToSwingNodeChanges);
      modelToSwingNodeChanges.clear();
    }

    IntList indicesToFire = new IntArrayList();
    List<Node> nodesToFire = new ArrayList<>();

    //first grouping changes by parent node
    Map<Node, List<NodeChange>> groupByParent = nodeChanges.stream().collect(Collectors.groupingBy(NodeChange::getParentNode));
    for (Map.Entry<Node, List<NodeChange>> entry : groupByParent.entrySet()) {
      Node parentNode = entry.getKey();
      synchronized (parentNode) {
        List<NodeChange> changes = entry.getValue();
        List<NodeChange> addedToThisNode = new ArrayList<>();
        //removing node
        for (NodeChange change : changes) {
          if (change.nodeChangeType.equals(NodeChangeType.REMOVED) || change.nodeChangeType.equals(NodeChangeType.REPLACED)) {
            GroupNode grandParent = (GroupNode)parentNode.getParent();
            int index = grandParent.getSwingChildren().indexOf(parentNode);
            if (index >= 0) {
              grandParent.getSwingChildren().remove(parentNode);
              myModel.nodesWereRemoved(grandParent, new int[]{index}, new Object[]{parentNode});
              myModel.fireTreeStructureChanged(grandParent, myModel.getPathToRoot(parentNode), new int[]{index}, new Object[]{parentNode});
              if (parentNode instanceof UsageNode) {
                grandParent.incrementUsageCount(-1);
              }
              //if this node was removed than we can skip all the other changes related to it
              break;
            }
          }
          else {
            addedToThisNode.add(change);
          }
        }

        //adding children nodes in batch
        if (!addedToThisNode.isEmpty()) {
          for (NodeChange change : addedToThisNode) {
            Node childNode = change.childNode;
            if (childNode == null) {
              continue;
            }
            synchronized (childNode) {
              List<Node> swingChildren = ((GroupNode)parentNode).getSwingChildren();
              boolean contains = swingChildren.contains(childNode);
              if (!contains) {
                nodesToFire.add(childNode);

                parentNode.insertNewNode(childNode, 0);
                swingChildren.sort(COMPARATOR);
                indicesToFire.add(swingChildren.indexOf(change.childNode));

                if (childNode instanceof UsageNode) {
                  ((GroupNode)parentNode).incrementUsageCount(1);
                }
              }
            }
            if (!indicesToFire.isEmpty()) {
              myModel.fireTreeNodesInserted(parentNode, myModel.getPathToRoot(parentNode), indicesToFire.toIntArray(),
                                            nodesToFire.toArray(new Node[0]));
              indicesToFire.clear();
              nodesToFire.clear();
            }
          }
        }
      }
    }
  }

  @Override
  public void searchFinished() {
    drainQueuedUsageNodes();
    setSearchInProgress(false);
  }

  @Override
  public boolean searchHasBeenCancelled() {
    ProgressIndicator progress = associatedProgress;
    return progress != null && progress.isCanceled();
  }

  @Override
  public void cancelCurrentSearch() {
    ProgressIndicator progress = associatedProgress;
    if (progress != null) {
      ProgressWrapper.unwrapAll(progress).cancel();
    }
  }

  private int getVisibleRowCount() {
    ThreadingAssertions.assertEventDispatchThread();
    return TreeUtil.getVisibleRowCount(myTree);
  }

  private void setupCentralPanel() {
    ThreadingAssertions.assertEventDispatchThread();

    JScrollPane treePane = ScrollPaneFactory.createScrollPane(myTree);
    myPreviewSplitter = new OnePixelSplitter(false, 0.5f, 0.1f, 0.9f);
    myPreviewSplitter.setFirstComponent(treePane);

    myCentralPanel.add(myPreviewSplitter, BorderLayout.CENTER);

    updateUsagesContextPanels();

    myCentralPanel.add(myAdditionalComponent, BorderLayout.SOUTH);
    myAdditionalComponent.add(myButtonPanel, BorderLayout.SOUTH);
  }

  private void updateUsagesContextPanels() {
    ThreadingAssertions.assertEventDispatchThread();
    disposeUsageContextPanels();
    if (isPreviewUsages()) {
      myPreviewSplitter.setProportion(getUsageViewSettings().getPreviewUsagesSplitterProportion());
      JBTabbedPane tabbedPane = new JBTabbedPane(SwingConstants.BOTTOM);
      tabbedPane.setTabComponentInsets(null);

      UsageContextPanel.Provider[] extensions = UsageContextPanel.Provider.EP_NAME.getExtensions(myProject);
      List<UsageContextPanel.Provider> myUsageContextPanelProviders = ContainerUtil.filter(extensions, provider -> provider.isAvailableFor(this));
      Map<@NlsContexts.TabTitle String, JComponent> components = new LinkedHashMap<>();
      for (UsageContextPanel.Provider provider : myUsageContextPanelProviders) {
        JComponent component;
        if (myCurrentUsageContextProvider == null || myCurrentUsageContextProvider == provider) {
          myCurrentUsageContextProvider = provider;
          UsageContextPanel panel = provider.create(this);
          myAllUsageContextPanels.add(panel);
          myCurrentUsageContextPanel = panel;
          component = myCurrentUsageContextPanel.createComponent();
        }
        else {
          component = new JLabel();
        }
        components.put(provider.getTabTitle(), component);
      }
      JBPanelWithEmptyText panel = new JBPanelWithEmptyText(new BorderLayout());
      if (components.size() == 1) {
        panel.add(components.values().iterator().next(), BorderLayout.CENTER);
      }
      else {
        for (Map.Entry<@NlsContexts.TabTitle String, JComponent> entry : components.entrySet()) {
          tabbedPane.addTab(entry.getKey(), entry.getValue());
        }
        int index = myUsageContextPanelProviders.indexOf(myCurrentUsageContextProvider);
        tabbedPane.setSelectedIndex(index);
        tabbedPane.addChangeListener(e -> {
          int currentIndex = tabbedPane.getSelectedIndex();
          UsageContextPanel.Provider selectedProvider = myUsageContextPanelProviders.get(currentIndex);
          if (selectedProvider != myCurrentUsageContextProvider) {
            tabSelected(selectedProvider);
            UsageViewStatisticsCollector.logTabSwitched(myProject, this);
          }
        });
        panel.add(tabbedPane, BorderLayout.CENTER);
      }
      myPreviewSplitter.setSecondComponent(panel);
    }
    else {
      myPreviewSplitter.setSecondComponent(null);
      myPreviewSplitter.setProportion(1);
    }

    myRootPanel.revalidate();
    myRootPanel.repaint();
  }

  private void tabSelected(@NotNull UsageContextPanel.Provider provider) {
    ThreadingAssertions.assertEventDispatchThread();
    myCurrentUsageContextProvider = provider;
    updateUsagesContextPanels();
    updateOnSelectionChanged(myProject);
  }

  private void disposeUsageContextPanels() {
    ThreadingAssertions.assertEventDispatchThread();
    if (!myAllUsageContextPanels.isEmpty()) {
      saveSplitterProportions();
      for (UsageContextPanel panel : myAllUsageContextPanels) {
        Disposer.dispose(panel);
      }
      myCurrentUsageContextPanel = null;
      myAllUsageContextPanels.clear();
    }
  }

  boolean isPreviewUsages() {
    return myPresentation.isReplaceMode() ? getUsageViewSettings().isReplacePreviewUsages() : getUsageViewSettings().isPreviewUsages();
  }

  void setPreviewUsages(boolean state) {
    if (myPresentation.isReplaceMode()) {
      getUsageViewSettings().setReplacePreviewUsages(state);
    }
    else {
      getUsageViewSettings().setPreviewUsages(state);
    }
  }

  protected UsageFilteringRule @NotNull [] getActiveFilteringRules(Project project) {
    List<UsageFilteringRuleProvider> providers = UsageFilteringRuleProvider.EP_NAME.getExtensionList();
    List<UsageFilteringRule> list = new ArrayList<>(providers.size());
    for (UsageFilteringRule rule : UsageFilteringRules.usageFilteringRules(project)) {
      if (myFilteringRulesState.isActive(rule.getRuleId())) {
        list.add(rule);
      }
    }
    for (UsageFilteringRuleProvider provider : providers) {
      //noinspection deprecation
      ContainerUtil.addAll(list, provider.getActiveRules(project));
    }
    return list.toArray(UsageFilteringRule.EMPTY_ARRAY);
  }

  protected static UsageGroupingRule @NotNull [] getActiveGroupingRules(@NotNull Project project,
                                                                        @NotNull UsageViewSettings usageViewSettings,
                                                                        @Nullable UsageViewPresentation presentation) {
    List<UsageGroupingRuleProvider> providers = UsageGroupingRuleProvider.EP_NAME.getExtensionList();
    List<UsageGroupingRule> list = new ArrayList<>(providers.size());
    for (UsageGroupingRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.getActiveRules(project, usageViewSettings, presentation));
    }

    list.sort(Comparator.comparingInt(UsageGroupingRule::getRank));
    return list.toArray(UsageGroupingRule.EMPTY_ARRAY);
  }

  private void initTree() {
    ThreadingAssertions.assertEventDispatchThread();
    myTree.setShowsRootHandles(true);
    SmartExpander.installOn(myTree);
    TreeUtil.installActions(myTree);
    EditSourceOnDoubleClickHandler.install(myTree, fusRunnable);
    EditSourceOnEnterKeyHandler.install(myTree, fusRunnable);

    TreeUtil.promiseSelectFirst(myTree);
    PopupHandler.installPopupMenu(myTree, IdeActions.GROUP_USAGE_VIEW_POPUP, ActionPlaces.USAGE_VIEW_POPUP);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();
        Object component = path.getLastPathComponent();
        if (component instanceof Node node) {
          if (!myExpandingCollapsing && node.needsUpdate()) {
            List<Node> toUpdate = new ArrayList<>();
            checkNodeValidity(node, path, toUpdate);
            queueUpdateBulk(toUpdate, EmptyRunnable.getInstance());
          }
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree, o -> {
      Object value = o.getLastPathComponent();
      TreeCellRenderer renderer = myTree.getCellRenderer();
      if (renderer instanceof UsageViewTreeCellRenderer coloredRenderer) {
        return coloredRenderer.getPlainTextForNode(value);
      }
      return value == null ? null : value.toString();
    }, true);
  }

  private @NotNull JComponent createActionsToolbar() {
    ThreadingAssertions.assertEventDispatchThread();

    DefaultActionGroup group = new DefaultActionGroup() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        myButtonPanel.update();
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };

    AnAction[] actions = createActions();
    for (AnAction action : actions) {
      if (action != null) {
        group.add(action);
      }
    }
    return toUsageViewToolbar(group);
  }

  private @NotNull JComponent toUsageViewToolbar(@NotNull DefaultActionGroup group) {
    ThreadingAssertions.assertEventDispatchThread();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, group, false);
    actionToolbar.setTargetComponent(myRootPanel);
    return actionToolbar.getComponent();
  }

  @SuppressWarnings("WeakerAccess") // used in rider
  protected boolean isPreviewUsageActionEnabled() {
    return true;
  }

  public void addFilteringActions(@NotNull DefaultActionGroup group) {
    ThreadingAssertions.assertEventDispatchThread();
    addFilteringActions(group, true);
  }

  protected void addFilteringActions(@NotNull DefaultActionGroup group, boolean includeExtensionPoints) {
    if (getPresentation().isMergeDupLinesAvailable()) {
      MergeSameLineUsagesAction mergeDupLines = new MergeSameLineUsagesAction();
      JComponent component = myRootPanel;
      if (component != null) {
        mergeDupLines.registerCustomShortcutSet(mergeDupLines.getShortcutSet(), component, this);
      }
      group.add(mergeDupLines);
    }
    if (includeExtensionPoints) {
      addFilteringFromExtensionPoints(group);
    }
  }

  /**
   * Creates filtering actions for the toolbar
   */
  protected void addFilteringFromExtensionPoints(@NotNull DefaultActionGroup group) {
    if (getPresentation().isCodeUsages()) {
      JComponent component = getComponent();
      List<AnAction> actions = usageFilteringRuleActions(myProject, myFilteringRulesState);
      for (AnAction action : actions) {
        action.registerCustomShortcutSet(component, this);
        group.add(action);
      }
    }
    for (UsageFilteringRuleProvider provider : UsageFilteringRuleProvider.EP_NAME.getExtensionList()) {
      //noinspection deprecation
      AnAction[] providerActions = provider.createFilteringActions(this);
      for (AnAction action : providerActions) {
        group.add(action);
      }
    }
  }

  protected final TreeExpander treeExpander = new TreeExpander() {
    @Override
    public void expandAll() {
      UsageViewImpl.this.expandAll();
      getUsageViewSettings().setExpanded(true);
    }

    @Override
    public boolean canExpand() {
      return true;
    }

    @Override
    public void collapseAll() {
      UsageViewImpl.this.collapseAll();
      getUsageViewSettings().setExpanded(false);
    }

    @Override
    public boolean canCollapse() {
      return true;
    }
  };

  protected AnAction @NotNull [] createActions() {
    ThreadingAssertions.assertEventDispatchThread();

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();

    JComponent component = getComponent();

    AnAction expandAllAction = actionsManager.createExpandAllAction(treeExpander, component);
    AnAction collapseAllAction = actionsManager.createCollapseAllAction(treeExpander, component);

    Disposer.register(this, () -> {
      expandAllAction.unregisterCustomShortcutSet(component);
      collapseAllAction.unregisterCustomShortcutSet(component);
    });

    DefaultActionGroup group = new DefaultActionGroup();
    group.setPopup(true);
    group.getTemplatePresentation().setIcon(AllIcons.Actions.GroupBy);
    group.getTemplatePresentation().setText(UsageViewBundle.messagePointer("action.group.by.title"));
    group.getTemplatePresentation().setDescription(UsageViewBundle.messagePointer("action.group.by.title"));
    AnAction[] groupingActions = createGroupingActions();
    if (groupingActions.length > 0) {
      group.add(new Separator(UsageViewBundle.message("action.group.by.title")));
      group.addAll(groupingActions);
      group.add(new Separator());
    }

    addFilteringActions(group, false);
    DefaultActionGroup filteringSubgroup = new DefaultActionGroup();
    addFilteringFromExtensionPoints(filteringSubgroup);

    return new AnAction[]{
      ActionManager.getInstance().getAction("UsageView.Rerun"),
      actionsManager.createPrevOccurenceAction(myRootPanel),
      actionsManager.createNextOccurenceAction(myRootPanel),
      new Separator(),
      canShowSettings() ? new ShowSettings() : null,
      canShowSettings() ? new Separator() : null,
      group,
      filteringSubgroup,
      expandAllAction,
      collapseAllAction,
      new Separator(),
      isPreviewUsageActionEnabled() ? new PreviewUsageAction() : null,
    };
  }

  protected boolean canShowSettings() {
    if (myTargets.length == 0) return false;
    NavigationItem target = myTargets[0];
    return target instanceof ConfigurableUsageTarget;
  }

  private static ConfigurableUsageTarget getConfigurableTarget(UsageTarget @NotNull [] targets) {
    ConfigurableUsageTarget configurableUsageTarget = null;
    if (targets.length != 0) {
      NavigationItem target = targets[0];
      if (target instanceof ConfigurableUsageTarget) {
        configurableUsageTarget = (ConfigurableUsageTarget)target;
      }
    }
    return configurableUsageTarget;
  }

  /**
   * Creates grouping actions for the toolbar
   */
  protected AnAction @NotNull [] createGroupingActions() {
    List<UsageGroupingRuleProvider> providers = UsageGroupingRuleProvider.EP_NAME.getExtensionList();
    List<AnAction> list = new ArrayList<>(providers.size());
    for (UsageGroupingRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.createGroupingActions(this));
    }
    sortGroupingActions(list);
    moveActionTo(list, UsageViewBundle.message("action.group.by.module"),
                 UsageViewBundle.message("action.flatten.modules"), true);
    return list.toArray(AnAction.EMPTY_ARRAY);
  }

  protected static void moveActionTo(@NotNull List<AnAction> list,
                                     @NotNull String actionText,
                                     @NotNull String targetActionText,
                                     boolean before) {
    if (Objects.equals(actionText, targetActionText)) {
      return;
    }

    int actionIndex = -1;
    int targetIndex = -1;
    for (int i = 0; i < list.size(); i++) {
      AnAction action = list.get(i);
      if (actionIndex == -1 && Objects.equals(actionText, action.getTemplateText())) actionIndex = i;
      if (targetIndex == -1 && Objects.equals(targetActionText, action.getTemplateText())) targetIndex = i;
      if (actionIndex != -1 && targetIndex != -1) {
        if (actionIndex < targetIndex) targetIndex--;
        AnAction anAction = list.remove(actionIndex);
        list.add(before ? targetIndex : targetIndex + 1, anAction);
        return;
      }
    }
  }

  /**
   * create*Action() methods can be used in createActions() method in subclasses to create a toolbar
   */
  protected @NotNull AnAction createPreviewAction() {
    return new PreviewUsageAction();
  }

  protected @NotNull AnAction createSettingsAction() {
    return new ShowSettings();
  }

  protected @NotNull AnAction createPreviousOccurrenceAction() {
    return CommonActionsManager.getInstance().createPrevOccurenceAction(myRootPanel);
  }

  protected @NotNull AnAction createNextOccurrenceAction() {
    return CommonActionsManager.getInstance().createNextOccurenceAction(myRootPanel);
  }

  /**
   * Sorting AnActions in the Grouping Actions view, default implementation is alphabetical sort
   *
   * @param actions to sort
   */
  protected void sortGroupingActions(@NotNull List<? extends AnAction> actions) {
    actions.sort((o1, o2) -> Comparing.compare(o1.getTemplateText(), o2.getTemplateText()));
  }

  private boolean shouldTreeReactNowToRuleChanges() {
    ThreadingAssertions.assertEventDispatchThread();
    return myPresentation.isDetachedMode() || myTree.isShowing();
  }

  private boolean rulesChanged; // accessed in EDT only

  private void rulesChanged() {
    ThreadingAssertions.assertEventDispatchThread();
    if (!shouldTreeReactNowToRuleChanges()) {
      rulesChanged = true;
      return;
    }

    List<UsageState> states = new ArrayList<>();
    if (myTree != null) {
      captureUsagesExpandState(new TreePath(myTree.getModel().getRoot()), states);
    }
    List<Usage> allUsages = new ArrayList<>(myUsageNodes.keySet());
    allUsages.sort(USAGE_COMPARATOR_BY_FILE_AND_OFFSET);
    Set<Usage> excludedUsages = getExcludedUsages();
    reset();
    myGroupingRules = getActiveGroupingRules(myProject, getUsageViewSettings(), getPresentation());

    myBuilder.setGroupingRules(myGroupingRules);
    myBuilder.setFilteringRules(getActiveFilteringRules(myProject));

    for (int i = allUsages.size() - 1; i >= 0; i--) {
      Usage usage = allUsages.get(i);
      if (!usage.isValid()) {
        allUsages.remove(i);
        continue;
      }
      if (usage instanceof MergeableUsage) {
        ((MergeableUsage)usage).reset();
      }
    }
    appendUsagesInBulk(allUsages).thenRun(() -> SwingUtilities.invokeLater(() -> {
      if (isDisposed()) return;
      if (myTree != null) {
        excludeUsages(excludedUsages.toArray(Usage.EMPTY_ARRAY));
        restoreUsageExpandState(states);
        updateImmediately();
        if (myCentralPanel != null) {
          updateUsagesContextPanels();
        }
      }
    }));
  }

  private void captureUsagesExpandState(@NotNull TreePath pathFrom, @NotNull Collection<? super UsageState> states) {
    ThreadingAssertions.assertEventDispatchThread();
    if (!myTree.isExpanded(pathFrom)) {
      return;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)pathFrom.getLastPathComponent();
    int childCount = node.getChildCount();
    for (int idx = 0; idx < childCount; idx++) {
      TreeNode child = node.getChildAt(idx);
      if (child instanceof UsageNode) {
        Usage usage = ((UsageNode)child).getUsage();
        states.add(new UsageState(usage, myTree.getSelectionModel().isPathSelected(pathFrom.pathByAddingChild(child))));
      }
      else {
        captureUsagesExpandState(pathFrom.pathByAddingChild(child), states);
      }
    }
  }

  private void restoreUsageExpandState(@NotNull Collection<? extends UsageState> states) {
    ThreadingAssertions.assertEventDispatchThread();
    //always expand the last level group
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    for (int i = root.getChildCount() - 1; i >= 0; i--) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)root.getChildAt(i);
      if (child instanceof GroupNode) {
        TreePath treePath = new TreePath(child.getPath());
        myTree.expandPath(treePath);
      }
    }
    myTree.getSelectionModel().clearSelection();
    for (UsageState usageState : states) {
      usageState.restore();
    }
  }

  public void expandAll() {
    doExpandingCollapsing(() -> TreeUtil.expandAll(myTree));
  }

  private void expandTree(int levels) {
    doExpandingCollapsing(() -> TreeUtil.expand(myTree, levels));
  }

  private void doExpandingCollapsing(@NotNull Runnable task) {
    if (isDisposed()) return;
    ThreadingAssertions.assertEventDispatchThread();
    fireEvents();  // drain all remaining insertion events in the queue

    myExpandingCollapsing = true;
    try {
      task.run();
    }
    finally {
      myExpandingCollapsing = false;
    }
  }

  private void collapseAll() {
    doExpandingCollapsing(() -> {
      TreeUtil.collapseAll(myTree, 3);
      myTree.expandRow(0);
    });
  }

  public void expandRoot() {
    expandTree(1);
  }

  @NotNull
  DefaultMutableTreeNode getModelRoot() {
    ThreadingAssertions.assertEventDispatchThread();
    return (DefaultMutableTreeNode)myTree.getModel().getRoot();
  }

  public void select() {
    ThreadingAssertions.assertEventDispatchThread();
    // can be null during ctr execution
    if (myTree != null) {
      myTree.requestFocusInWindow();
    }
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  static KeyboardShortcut getShowUsagesWithSettingsShortcut(UsageTarget @NotNull [] targets) {
    ConfigurableUsageTarget configurableTarget = getConfigurableTarget(targets);
    return configurableTarget == null ? UsageViewUtil.getShowUsagesWithSettingsShortcut() : configurableTarget.getShortcut();
  }

  @Override
  public void associateProgress(@NotNull ProgressIndicator indicator) {
    associatedProgress = indicator;
  }

  private final class ShowSettings extends AnAction {
    ShowSettings() {
      super(UsageViewBundle.message("action.text.usage.view.settings"), null, AllIcons.General.GearPlain);
      ConfigurableUsageTarget target = getConfigurableTarget(myTargets);
      KeyboardShortcut shortcut = target == null ? UsageViewUtil.getShowUsagesWithSettingsShortcut() : target.getShortcut();
      if (shortcut != null) {
        registerCustomShortcutSet(new CustomShortcutSet(shortcut), getComponent());
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
      if (getTemplatePresentation().getDescription() == null) {
        ConfigurableUsageTarget target = getConfigurableTarget(myTargets);
        Supplier<String> description = null;
        if (target != null) {
          try {
            description = UsageViewBundle.messagePointer(
              "action.ShowSettings.show.settings.for.description", target.getLongDescriptiveName());
          }
          catch (IndexNotReadyException ignored) { }
        }
        if (description == null) {
          description = UsageViewBundle.messagePointer("action.ShowSettings.show.find.usages.settings.dialog.description");
        }
        getTemplatePresentation().setDescription(description);
        e.getPresentation().setDescription(description);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FindManager.getInstance(getProject()).showSettingsAndFindUsages(myTargets);
    }
  }

  public void refreshUsages() {
    reset();
    doReRun();
  }

  /**
   * @return usage view which will be shown after re-run (either {@code this} if it knows how to re-run itself, or the new created one otherwise)
   */
  protected UsageView doReRun() {
    myChangesDetected = false;
    if (myRerunAction == null) {
      UsageViewPresentation rerunPresentation = myPresentation.copy();
      rerunPresentation.setRerunHash(System.identityHashCode(myContent));
      return UsageViewManager.getInstance(getProject()).
        searchAndShowUsages(myTargets, myUsageSearcherFactory, true, false, rerunPresentation, null);
    }
    myRerunAction.actionPerformed(null);
    return this;
  }

  private void reset() {
    ThreadingAssertions.assertEventDispatchThread();
    myUsageNodes.clear();
    myModel.reset();
    myBuilder.reset();
    synchronized (modelToSwingNodeChanges) {
      modelToSwingNodeChanges.clear();
    }

    if (!myPresentation.isDetachedMode()) {
      SwingUtilities.invokeLater(() -> expandTree(2));
    }
  }

  void drainQueuedUsageNodes() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    UIUtil.invokeAndWaitIfNeeded(this::fireEvents);
  }

  @Override
  public void appendUsage(@NotNull Usage usage) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      addUpdateRequest(() -> ReadAction.run(() -> doAppendUsage(usage)));
    }
    else {
      doAppendUsage(usage);
    }
  }

  private void addUpdateRequest(@NotNull Runnable request) {
    updateRequests.execute(request);
  }

  @Override
  public void waitForUpdateRequestsCompletion() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    try {
      updateRequests.waitAllTasksExecuted(10, TimeUnit.MINUTES);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public @NotNull CompletableFuture<?> appendUsagesInBulk(@NotNull Collection<? extends Usage> usages) {
    CompletableFuture<Object> result = new CompletableFuture<>();
    addUpdateRequest(() -> ReadAction.run(() -> {
      try {
        for (Usage usage : usages) {
          doAppendUsage(usage);
        }
        result.complete(null);
      }
      catch (Exception e) {
        result.completeExceptionally(e);
        throw e;
      }
    }));
    return result;
  }

  public UsageNode doAppendUsage(@NotNull Usage usage) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    // invoke in ReadAction to be sure that usages are not invalidated while the tree is being built
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!usage.isValid()) {
      // because the view is built incrementally, the usage may be already invalid, so need to filter such cases
      return null;
    }

    for (UsageViewElementsListener listener : UsageViewElementsListener.EP_NAME.getExtensionList()) {
      if (listener.skipUsage(this, usage)) {
        return null;
      }
      listener.beforeUsageAdded(this, usage);
    }

    if (usage instanceof PsiElementUsage) {
      reportToFUS((PsiElementUsage)usage);
    }

    UsageNode recentNode = myBuilder.appendOrGet(usage, isFilterDuplicateLines(), edtModelToSwingNodeChangesQueue);
    myUsageNodes.put(usage, recentNode == null ? NULL_NODE : recentNode);
    if (myAutoSelectedGroupNode == null && usage instanceof SimilarUsage && recentNode != null && !myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(() -> {
        GroupNode groupNode = ObjectUtils.tryCast(TreeUtil.getPath(myRoot, recentNode).getPath()[1], GroupNode.class);
        if (groupNode != null && getSelectedNode() instanceof UsageViewTreeModelBuilder.TargetsRootNode) {
          myAutoSelectedGroupNode = groupNode;
          showNode(groupNode);
        }
      });
    }
    if (recentNode != null && getPresentation().isExcludeAvailable()) {
      for (UsageViewElementsListener listener : UsageViewElementsListener.EP_NAME.getExtensionList()) {
        if (listener.isExcludedByDefault(this, usage)) {
          myExclusionHandler.excludeNodeSilently(recentNode);
        }
      }
    }

    for (Node node = recentNode; node != myRoot && node != null; node = (Node)node.getParent()) {
      node.update(edtFireTreeNodesChangedQueue);
    }

    return recentNode;
  }

  private void reportToFUS(@NotNull PsiElementUsage usage) {
    Class<? extends PsiReference> referenceClass = UsageReferenceClassProvider.Companion.getReferenceClass(usage);
    PsiElement element = usage.getElement();
    if (element != null && referenceClass != null) {
      Language language = element.getLanguage();
      if (myReportedReferenceClasses.add(Pair.create(referenceClass, language))) {
        UsageViewStatisticsCollector.logUsageShown(myProject, referenceClass, language, this);
      }
    }
  }

  @Override
  public void removeUsage(@NotNull Usage usage) {
    removeUsagesBulk(Collections.singleton(usage));
  }

  @Override
  public void removeUsagesBulk(@NotNull Collection<? extends Usage> usages) {
    Usage toSelect = getNextToSelect(usages);
    UsageNode nodeToSelect = toSelect != null ? myUsageNodes.get(toSelect) : null;

    Set<UsageNode> nodes = usagesToNodes(usages.stream()).collect(Collectors.toSet());
    usages.forEach(myUsageNodes::remove);
    if (!myUsageNodes.isEmpty()) {
      Set<UsageInfo> mergedInfos = usages.stream()
        .filter(usage -> usage instanceof UsageInfo2UsageAdapter && ((UsageInfo2UsageAdapter)usage).getMergedInfos().length > 1)
        .flatMap(usage -> Arrays.stream(((UsageInfo2UsageAdapter)usage).getMergedInfos()))
        .collect(Collectors.toSet());
      if (!mergedInfos.isEmpty()) {
        myUsageNodes.keySet().removeIf(
          usage -> usage instanceof UsageInfo2UsageAdapter && mergedInfos.contains(((UsageInfo2UsageAdapter)usage).getUsageInfo()));
      }
    }

    if (!nodes.isEmpty() && !myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (isDisposed()) return;
        DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
        ((GroupNode)treeModel.getRoot()).removeUsagesBulk(nodes, treeModel);
        if (nodeToSelect != null) {
          TreePath path = new TreePath(nodeToSelect.getPath());
          myTree.addSelectionPath(path);
        }
      });
    }
  }

  @Override
  public void includeUsages(Usage @NotNull [] usages) {
    usagesToNodes(Arrays.stream(usages))
      .forEach(myExclusionHandler::includeNode);
  }

  @Override
  public void excludeUsages(Usage @NotNull [] usages) {
    usagesToNodes(Arrays.stream(usages))
      .forEach(myExclusionHandler::excludeNode);
  }

  private @NotNull Stream<UsageNode> usagesToNodes(@NotNull Stream<? extends Usage> usages) {
    return usages
      .map(myUsageNodes::get)
      .filter(node -> node != NULL_NODE && node != null);
  }

  @Override
  public void selectUsages(Usage @NotNull [] usages) {
    ThreadingAssertions.assertEventDispatchThread();
    TreePath[] paths = usagesToNodes(Arrays.stream(usages))
      .map(node -> new TreePath(node.getPath()))
      .toArray(TreePath[]::new);

    myTree.setSelectionPaths(paths);
    if (paths.length != 0) myTree.scrollPathToVisible(paths[0]);
  }

  @Override
  public @NotNull JComponent getPreferredFocusableComponent() {
    ThreadingAssertions.assertEventDispatchThread();
    return myTree != null ? myTree : getComponent();
  }

  @Override
  public @NotNull JComponent getComponent() {
    ThreadingAssertions.assertEventDispatchThread();
    return myRootPanel == null ? new JLabel() : myRootPanel;
  }

  @Override
  public int getUsagesCount() {
    return myUsageNodes.size();
  }

  @Override
  public void addExcludeListener(@NotNull Disposable disposable, @NotNull ExcludeListener listener) {
    myExcludeListeners.add(listener);
    Disposer.register(disposable, () -> myExcludeListeners.remove(listener));
  }

  void setContent(@NotNull Content content) {
    myContent = content;
    content.setDisposer(this);
  }

  private void updateImmediately() {
    ThreadingAssertions.assertEventDispatchThread();
    if (isDisposed()) return;
    TreeNode root = (TreeNode)myTree.getModel().getRoot();
    List<Node> toUpdate = new ArrayList<>();
    checkNodeValidity(root, new TreePath(root), toUpdate);
    queueUpdateBulk(toUpdate, EmptyRunnable.getInstance());
    updateOnSelectionChanged(myProject);
  }

  private void queueUpdateBulk(@NotNull List<? extends Node> toUpdate, @NotNull Runnable onCompletedInEdt) {
    if (toUpdate.isEmpty() || isDisposed()) return;
    ReadAction.nonBlocking((Callable<?>)() -> {
        for (Node node : toUpdate) {
          try {
            node.update(edtFireTreeNodesChangedQueue);
          }
          catch (IndexNotReadyException ignore) {
          }
        }
        return null;
      })
      .expireWith(this)
      .finishOnUiThread(ModalityState.defaultModalityState(), __ -> onCompletedInEdt.run())
      .submit(updateRequests);
  }

  private void updateImmediatelyNodesUpToRoot(@NotNull Collection<? extends Node> nodes) {
    ThreadingAssertions.assertEventDispatchThread();
    if (isDisposed()) return;
    TreeNode root = (TreeNode)myTree.getModel().getRoot();
    Set<Node> queued = new HashSet<>();
    List<Node> toUpdate = new ArrayList<>();
    while (true) {
      Set<Node> parents = new HashSet<>();
      for (Node node : nodes) {
        toUpdate.add(node);
        TreeNode parent = node.getParent();
        if (parent != root && parent instanceof Node && queued.add((Node)parent)) {
          parents.add((Node)parent);
        }
      }
      if (parents.isEmpty()) break;
      nodes = parents;
    }
    queueUpdateBulk(toUpdate, EmptyRunnable.getInstance());
    updateImmediately();
  }


  private void updateOnSelectionChanged(@NotNull Project project) {
    ThreadingAssertions.assertEventDispatchThread();
    if (myCurrentUsageContextPanel != null) {
      var dataContext = createAsyncDataContext(DataManager.getInstance().getDataContext(myRootPanel));
      ReadAction.nonBlocking(() -> {
          List<UsageInfo> result;
          try {
            result = ContainerUtil.notNullize(USAGE_INFO_LIST_KEY.getData(dataContext));
          }
          catch (IndexNotReadyException ignore) {
            result = Collections.emptyList();
          }
          return result;
        })
        .expireWith(this)
        .finishOnUiThread(ModalityState.current(), usageInfos -> {
          myCurrentUsageContextPanel.updateLayout(project, usageInfos, this);
        })
        .submit(updateRequests);
    }
  }

  private void checkNodeValidity(@NotNull TreeNode node, @NotNull TreePath path, @NotNull List<? super Node> result) {
    ThreadingAssertions.assertEventDispatchThread();
    boolean shouldCheckChildren = true;
    if (myTree.isCollapsed(path)) {
      if (node instanceof Node) {
        ((Node)node).markNeedUpdate();
      }
      shouldCheckChildren = false;
      // optimization: do not call expensive update() on invisible node
    }
    UsageViewTreeCellRenderer.RowLocation isVisible =
      myUsageViewTreeCellRenderer.isRowVisible(myTree.getRowForPath(new TreePath(((DefaultMutableTreeNode)node).getPath())),
                                               myTree.getVisibleRect());

    // if row is below visible rectangle, no sense to update it or any children
    if (shouldCheckChildren && isVisible != UsageViewTreeCellRenderer.RowLocation.AFTER_VISIBLE_RECT) {
      for (int i = 0; i < node.getChildCount(); i++) {
        TreeNode child = node.getChildAt(i);
        checkNodeValidity(child, path.pathByAddingChild(child), result);
      }
    }

    // call update last, to let children a chance to update their cache first
    if (node instanceof Node && node != getModelRoot() && isVisible == UsageViewTreeCellRenderer.RowLocation.INSIDE_VISIBLE_RECT) {
      result.add((Node)node);
    }
  }

  void updateLater() {
    updateAlarm.cancelAndRequest();
  }

  @Override
  public void close() {
    cancelCurrentSearch();
    if (myContent != null) {
      UsageViewContentManager.getInstance(myProject).closeContent(myContent);
    }
  }

  private void saveSplitterProportions() {
    ThreadingAssertions.assertEventDispatchThread();
    getUsageViewSettings().setPreviewUsagesSplitterProportion(myPreviewSplitter.getProportion());
  }

  @Override
  public void dispose() {
    try {
      ThreadingAssertions.assertEventDispatchThread();
      disposeUsageContextPanels();
      isDisposed = true;
      updateAlarm.cancelAllRequests();
      fusRunnable = null; // Release reference to this

      cancelCurrentSearch();
      myRerunAction = null;
      if (myTree != null) {
        ToolTipManager.sharedInstance().unregisterComponent(myTree);
      }
      disposeSmartPointers();
    }
    finally {
      JobKt.getJob(coroutineScope.getCoroutineContext()).cancel(null);
    }
  }

  private void disposeSmartPointers() {
    List<SmartPsiElementPointer<?>> smartPointers = new ArrayList<>();
    for (Usage usage : myUsageNodes.keySet()) {
      if (usage instanceof UsageInfo2UsageAdapter) {
        SmartPsiElementPointer<?> pointer = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getSmartPointer();
        smartPointers.add(pointer);
      }
    }

    if (!smartPointers.isEmpty()) {
      for (SmartPsiElementPointer<?> pointer : smartPointers) {
        Project project = pointer.getProject();
        if (!project.isDisposed()) {
          SmartPointerManager.getInstance(project).removePointer(pointer);
        }
      }
    }
    myUsageNodes.clear();
  }

  @Override
  public boolean isSearchInProgress() {
    return mySearchInProgress;
  }

  @Override
  public void setSearchInProgress(boolean searchInProgress) {
    mySearchInProgress = searchInProgress;
    if (!myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (isDisposed()) return;
        if (getUsageViewSettings().isExpanded() && myUsageNodes.size() < 10000) {
          expandAll();
        }
        if (userHasSelectedNode()) return;
        Node nodeToSelect = ObjectUtils.coalesce(myAutoSelectedGroupNode, myModel.getFirstUsageNode());
        if (nodeToSelect == null) return;
        showNode(nodeToSelect);
      });
    }
  }

  private boolean userHasSelectedNode() {
    GroupNode autoSelectedGroupNode = myAutoSelectedGroupNode;
    Node selectedNode = getSelectedNode();
    if (selectedNode != null) {
      TreePath expectedPathToBeSelected = autoSelectedGroupNode != null ? TreeUtil.getPathFromRoot(autoSelectedGroupNode) : TreeUtil.getFirstNodePath(myTree);
      if (!Comparing.equal(TreeUtil.getPathFromRoot(selectedNode), expectedPathToBeSelected)) {
        return true;
      }
    }
    return false;
  }

  @ApiStatus.Internal
  public boolean isDisposed() {
    return isDisposed || myProject.isDisposed();
  }

  private void showNode(@NotNull Node node) {
    ThreadingAssertions.assertEventDispatchThread();
    if (!isDisposed() && !myPresentation.isDetachedMode()) {
      fireEvents();
      TreePath usagePath = new TreePath(node.getPath());
      myTree.expandPath(usagePath.getParentPath());
      TreeUtil.selectPath(myTree, usagePath);
    }
  }

  @Override
  public void setRerunAction(@NotNull Action rerunAction) {
    myRerunAction = rerunAction;
  }

  @Override
  public void addButtonToLowerPane(@NotNull Action action) {
    ThreadingAssertions.assertEventDispatchThread();
    int index = myButtonPanel.getComponentCount();
    if (!SystemInfo.isMac && index > 0 && myPresentation.isShowCancelButton()) index--;
    myButtonPanel.addButtonAction(index, action);
    Object o = action.getValue(Action.ACCELERATOR_KEY);
    if (o instanceof KeyStroke) {
      myTree.registerKeyboardAction(action, (KeyStroke)o, JComponent.WHEN_FOCUSED);
    }
  }

  @Override
  public void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull @NlsContexts.Button String text) {
    addButtonToLowerPane(new AbstractAction(UIUtil.replaceMnemonicAmpersand(text)) {
      @Override
      public void actionPerformed(ActionEvent e) {
        runnable.run();
      }
    });
  }

  @Override
  public void setAdditionalComponent(@Nullable JComponent comp) {
    BorderLayout layout = (BorderLayout)myAdditionalComponent.getLayout();
    Component prev = layout.getLayoutComponent(myAdditionalComponent, BorderLayout.CENTER);
    if (prev == comp) return;
    if (prev != null) myAdditionalComponent.remove(prev);
    if (comp != null) myAdditionalComponent.add(comp, BorderLayout.CENTER);
    myAdditionalComponent.revalidate();
  }

  @Override
  public void addPerformOperationAction(@NotNull Runnable processRunnable,
                                        @Nullable @NlsContexts.Command String commandName,
                                        @NotNull @NlsContexts.DialogMessage String cannotMakeString,
                                        @NotNull @NlsContexts.Button String shortDescription) {
    addPerformOperationAction(processRunnable, commandName, cannotMakeString, shortDescription, true);
  }

  @Override
  public void addPerformOperationAction(@NotNull Runnable processRunnable,
                                        @Nullable @NlsContexts.Command String commandName,
                                        @NotNull @NlsContexts.DialogMessage String cannotMakeString,
                                        @NotNull @NlsContexts.Button String shortDescription,
                                        boolean checkReadOnlyStatus) {
    Runnable runnable = new MyPerformOperationRunnable(processRunnable, commandName, cannotMakeString, checkReadOnlyStatus);
    addButtonToLowerPane(runnable, shortDescription);
  }

  private boolean allTargetsAreValid() {
    for (UsageTarget target : myTargets) {
      if (!target.isValid()) {
        return false;
      }
    }

    return true;
  }

  @Override
  public @NotNull UsageViewPresentation getPresentation() {
    return myPresentation;
  }

  @ApiStatus.Internal
  public boolean canPerformReRun() {
    if (myRerunAction != null && myRerunAction.isEnabled()) return allTargetsAreValid();
    try {
      return myUsageSearcherFactory != null && allTargetsAreValid() && myUsageSearcherFactory.get() != null;
    }
    catch (PsiInvalidElementAccessException e) {
      return false;
    }
  }

  private boolean checkReadonlyUsages() {
    Set<VirtualFile> readOnlyUsages = getReadOnlyUsagesFiles();

    return readOnlyUsages.isEmpty() ||
           !ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(readOnlyUsages).hasReadonlyFiles();
  }

  private @NotNull Set<Usage> getReadOnlyUsages() {
    Set<Usage> result = new HashSet<>();
    Set<Map.Entry<Usage, UsageNode>> usages = myUsageNodes.entrySet();
    for (Map.Entry<Usage, UsageNode> entry : usages) {
      Usage usage = entry.getKey();
      UsageNode node = entry.getValue();
      if (node != null && node != NULL_NODE && !node.isExcluded() && usage.isReadOnly()) {
        result.add(usage);
      }
    }
    return result;
  }

  private @NotNull Set<VirtualFile> getReadOnlyUsagesFiles() {
    Set<Usage> usages = getReadOnlyUsages();
    Set<VirtualFile> result = new HashSet<>();
    for (Usage usage : usages) {
      if (usage instanceof UsageInFile usageInFile) {
        VirtualFile file = usageInFile.getFile();
        if (file != null && file.isValid()) result.add(file);
      }

      if (usage instanceof UsageInFiles usageInFiles) {
        ContainerUtil.addAll(result, usageInFiles.getFiles());
      }
    }
    for (UsageTarget target : myTargets) {
      VirtualFile[] files = target.getFiles();
      if (files == null) continue;
      ContainerUtil.addAll(result, files);
    }
    return result;
  }

  @Override
  public @NotNull Set<Usage> getExcludedUsages() {
    Set<Usage> result = new HashSet<>();
    for (Map.Entry<Usage, UsageNode> entry : myUsageNodes.entrySet()) {
      UsageNode node = entry.getValue();
      Usage usage = entry.getKey();
      if (node == NULL_NODE || node == null) {
        continue;
      }
      if (node.isExcluded()) {
        result.add(usage);
      }
    }

    return result;
  }


  private @Nullable Node getSelectedNode() {
    ThreadingAssertions.assertEventDispatchThread();
    TreePath path = myTree.getLeadSelectionPath();
    Object node = path == null ? null : path.getLastPathComponent();
    return node instanceof Node ? (Node)node : null;
  }

  private @NotNull List<TreeNode> selectedNodes() {
    ThreadingAssertions.assertEventDispatchThread();
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    return selectionPaths == null ? Collections.emptyList() : ContainerUtil.mapNotNull(selectionPaths, p-> ObjectUtils.tryCast(p.getLastPathComponent(), TreeNode.class));
  }

  private @NotNull List<@NotNull TreeNode> allSelectedNodes() {
    return TreeUtil.treeNodeTraverser(null).withRoots(selectedNodes()).traverse().toList();
  }

  @Override
  public @NotNull Set<Usage> getSelectedUsages() {
    ThreadingAssertions.assertEventDispatchThread();
    return new HashSet<>(allUsagesRecursive(selectedNodes()));
  }

  private static @NotNull List<@NotNull Usage> allUsagesRecursive(@NotNull List<? extends TreeNode> selection) {
    return TreeUtil.treeNodeTraverser(null).withRoots(selection).traverse()
      .filterMap(o -> o instanceof UsageNode ? ((UsageNode)o).getUsage() : null).toList();
  }

  @Override
  public @NotNull Set<Usage> getUsages() {
    return myUsageNodes.keySet();
  }

  @Override
  public @NotNull List<Usage> getSortedUsages() {
    List<Usage> usages = new ArrayList<>(getUsages());
    usages.sort(USAGE_COMPARATOR_BY_FILE_AND_OFFSET);
    return usages;
  }

  private @Nullable Navigatable getNavigatableForNode(@NotNull DefaultMutableTreeNode node, boolean allowRequestFocus) {
    Object userObject = node.getUserObject();
    if (userObject instanceof Navigatable navigatable) {
      return navigatable.canNavigate() ? new Navigatable() {
        @Override
        public void navigate(boolean requestFocus) {
          if (Registry.is("ide.usages.next.previous.occurrence.only.show.in.preview") &&
              isPreviewUsages() && myRootPanel.isShowing()) select();
          else navigatable.navigate(allowRequestFocus && requestFocus);
        }

        @Override
        public boolean canNavigate() {
          return navigatable.canNavigate();
        }

        @Override
        public boolean canNavigateToSource() {
          return navigatable.canNavigateToSource();
        }
      } : null;
    }
    return null;
  }

  boolean areTargetsValid() {
    return myModel.areTargetsValid();
  }

  private final class MyPanel extends JPanel implements UiDataProvider, OccurenceNavigator, Disposable {
    private @Nullable OccurenceNavigatorSupport mySupport;
    private final CopyProvider myCopyProvider;

    private MyPanel(@NotNull JTree tree) {
      mySupport = new OccurenceNavigatorSupport(tree) {
        @Override
        protected Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node) {
          if (node.getChildCount() > 0) return null;
          if (node instanceof Node && ((Node)node).isExcluded()) return null;
          return getNavigatableForNode(node, !myPresentation.isReplaceMode());
        }

        @Override
        public @NotNull String getNextOccurenceActionName() {
          return UsageViewBundle.message("action.next.occurrence");
        }

        @Override
        public @NotNull String getPreviousOccurenceActionName() {
          return UsageViewBundle.message("action.previous.occurrence");
        }
      };
      myCopyProvider = new TextCopyProvider() {
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }

        @Override
        public @Nullable Collection<String> getTextLinesToCopy() {
          List<String> lines = ContainerUtil.mapNotNull(selectedNodes(), o -> o instanceof Node ? ((Node)o).getNodeText() : null);
          return lines.isEmpty() ? null : lines;
        }
      };
    }

    // this is a temp workaround to fix IDEA-192713. [tav] todo: invent something
    @Override
    protected void processFocusEvent(FocusEvent e) {
      super.processFocusEvent(e);
      if (e.getID() == FocusEvent.FOCUS_GAINED) {
        transferFocus();
      }
    }

    @Override
    public void dispose() {
      mySupport = null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean hasNextOccurence() {
      return mySupport != null && mySupport.hasNextOccurence();
    }

    @Override
    public boolean hasPreviousOccurence() {
      return mySupport != null && mySupport.hasPreviousOccurence();
    }

    @Override
    public OccurenceInfo goNextOccurence() {
      return mySupport != null ? mySupport.goNextOccurence() : null;
    }

    @Override
    public OccurenceInfo goPreviousOccurence() {
      return mySupport != null ? mySupport.goPreviousOccurence() : null;
    }

    @Override
    public @NotNull String getNextOccurenceActionName() {
      return mySupport != null ? mySupport.getNextOccurenceActionName() : "";
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return mySupport != null ? mySupport.getPreviousOccurenceActionName() : "";
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      List<TreeNode> selection = selectedNodes();

      sink.set(CommonDataKeys.PROJECT, myProject);
      sink.set(USAGE_VIEW_KEY, UsageViewImpl.this);
      sink.set(PlatformCoreDataKeys.HELP_ID, HELP_ID);
      sink.set(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
      sink.set(ExclusionHandler.EXCLUSION_HANDLER, myExclusionHandler);
      sink.set(PlatformDataKeys.EXPORTER_TO_TEXT_FILE, myTextFileExporter);
      sink.set(CommonDataKeys.NAVIGATABLE_ARRAY, ContainerUtil.mapNotNull(
        selection, n -> ObjectUtils.tryCast(TreeUtil.getUserObject(n), Navigatable.class))
        .toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY));
      var targets = ContainerUtil.mapNotNull(
        selection, o -> o instanceof UsageTargetNode oo ? oo.getTarget() : null);
      sink.set(USAGE_TARGETS_KEY, targets.isEmpty() ? null : targets.toArray(UsageTarget.EMPTY_ARRAY));

      List<TreeNode> selectedNodes = allSelectedNodes();
      DataSink.uiDataSnapshot(sink, TreeUtil.getUserObject(getSelectedNode()));

      sink.lazy(USAGES_KEY, () -> {
        return selectedUsages(selectedNodes)
          .toArray(n -> n == 0 ? Usage.EMPTY_ARRAY : new Usage[n]);
      });
      sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
        return selectedUsages(selectedNodes)
          .filter(usage -> usage instanceof PsiElementUsage)
          .map(usage -> ((PsiElementUsage)usage).getElement())
          .filter(element -> element != null)
          .toArray(PsiElement.ARRAY_FACTORY::create);
      });
      sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY, () -> {
        return JBIterable.from(selectedNodes)
          .filterMap(o -> o instanceof UsageNode ? ((UsageNode)o).getUsage() :
                          o instanceof UsageTargetNode ? ((UsageTargetNode)o).getTarget() : null)
          .flatMap(o -> o instanceof UsageInFile oo ? ContainerUtil.createMaybeSingletonList(oo.getFile()) :
                        o instanceof UsageInFiles oo ? Arrays.asList(oo.getFiles()) :
                        o instanceof UsageTarget oo
                        ? Arrays.asList(ObjectUtils.notNull(oo.getFiles(), VirtualFile.EMPTY_ARRAY))
                        : Collections.emptyList())
          .filter(VirtualFile::isValid)
          .unique()
          .toArray(VirtualFile.EMPTY_ARRAY);
      });
    }
  }

  private static @NotNull Stream<@NotNull Usage> selectedUsages(@NotNull List<@NotNull TreeNode> selectedNodes) {
    return selectedNodes.stream()
      .filter(node -> node instanceof UsageNode)
      .map(node -> ((UsageNode)node).getUsage())
      .distinct();
  }

  private final class ButtonPanel extends JPanel {
    private ButtonPanel() {
      setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
      getProject().getMessageBus().connect(UsageViewImpl.this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
          update();
        }

        @Override
        public void exitDumbMode() {
          update();
        }
      });
    }

    //Here we use
    // Action.LONG_DESCRIPTION as hint label for button
    // Action.SHORT_DESCRIPTION as a tooltip for button
    private void addButtonAction(int index, @NotNull Action action) {
      JButton button = new JButton(action);
      add(button, index);
      DialogUtil.registerMnemonic(button);

      if (getBorder() == null) setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
      update();
      Object s = action.getValue(Action.LONG_DESCRIPTION);
      if (s instanceof String) {
        JBLabel label = new JBLabel((String)s);
        label.setEnabled(false);
        label.setFont(JBUI.Fonts.smallFont());
        add(JBUI.Borders.emptyLeft(-1).wrap(label));
      }
      s = action.getValue(Action.SHORT_DESCRIPTION);
      if (s instanceof String) {
        button.setToolTipText((String)s);
      }
      invalidate();
      if (getParent() != null) {
        getParent().validate();
      }
    }

    void update() {
      boolean globallyEnabled = !isSearchInProgress() && !DumbService.isDumb(myProject);
      for (int i = 0; i < getComponentCount(); ++i) {
        Component component = getComponent(i);
        if (component instanceof JButton button) {
          Action action = button.getAction();
          if (action != null) {
            if (myNeedUpdateButtons) {
              button.setEnabled(globallyEnabled && action.isEnabled());
            }
            Object name = action.getValue(Action.NAME);
            if (name instanceof String) {
              DialogUtil.setTextWithMnemonic(button, (String)name);
            }
          }
          else {
            button.setEnabled(globallyEnabled);
          }
        }
      }
      myNeedUpdateButtons = false;
    }
  }

  private final class UsageState {
    private final Usage myUsage;
    private final boolean mySelected;

    private UsageState(@NotNull Usage usage, boolean isSelected) {
      myUsage = usage;
      mySelected = isSelected;
    }

    private void restore() {
      ThreadingAssertions.assertEventDispatchThread();
      UsageNode node = myUsageNodes.get(myUsage);
      if (node == NULL_NODE || node == null) {
        return;
      }
      DefaultMutableTreeNode parentGroupingNode = (DefaultMutableTreeNode)node.getParent();
      if (parentGroupingNode != null) {
        TreePath treePath = new TreePath(parentGroupingNode.getPath());
        myTree.expandPath(treePath);
        if (mySelected) {
          myTree.addSelectionPath(treePath.pathByAddingChild(node));
        }
      }
    }
  }

  private final class MyPerformOperationRunnable implements Runnable {
    private final @NlsContexts.DialogMessage String myCannotMakeString;
    private final Runnable myProcessRunnable;
    private final @NlsContexts.Command String myCommandName;
    private final boolean myCheckReadOnlyStatus;

    private MyPerformOperationRunnable(@NotNull Runnable processRunnable,
                                       @Nullable @NlsContexts.Command String commandName,
                                       @NlsContexts.DialogMessage String cannotMakeString,
                                       boolean checkReadOnlyStatus) {
      myCannotMakeString = cannotMakeString;
      myProcessRunnable = processRunnable;
      myCommandName = commandName;
      myCheckReadOnlyStatus = checkReadOnlyStatus;
    }

    @Override
    public void run() {
      if (myCheckReadOnlyStatus && !checkReadonlyUsages()) return;
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (myCannotMakeString != null && !myCannotMakeString.isEmpty() && myChangesDetected) {
        String title = UsageViewBundle.message("changes.detected.error.title");
        if (canPerformReRun()) {
          String message = myCannotMakeString + "\n\n" + UsageViewBundle.message("dialog.rerun.search");
          int answer = Messages.showYesNoCancelDialog(myProject, message, title, UsageViewBundle.message("action.description.rerun"),
                                                      UsageViewBundle.message("button.text.continue"),
                                                      UsageViewBundle.message("usage.view.cancel.button"), Messages.getErrorIcon());
          if (answer == Messages.YES) {
            refreshUsages();
            return;
          }
          else if (answer == Messages.CANCEL) {
            return;
          }
          //continue as is
        }
        else {
          Messages.showMessageDialog(myProject, myCannotMakeString, title, Messages.getErrorIcon());
          return;
        }
      }

      try {
        if (myCommandName == null) {
          myProcessRunnable.run();
        }
        else {
          CommandProcessor.getInstance().executeCommand(
            myProject, myProcessRunnable,
            myCommandName,
            null
          );
        }
      }
      finally {
        close();
      }
    }
  }

  @NotNull Set<@NotNull GroupNode> selectedGroupNodes() {
    return selectedNodes().stream().filter(node -> node instanceof GroupNode).map(node -> (GroupNode)node)
      .collect(Collectors.toCollection(HashSet::new));
  }

  public @NotNull GroupNode getRoot() {
    return myRoot;
  }

  @TestOnly
  public @NotNull String getNodeText(@NotNull TreeNode node) {
    return myUsageViewTreeCellRenderer.getPlainTextForNode(node);
  }

  public boolean isVisible(@NotNull Usage usage) {
    return myBuilder != null && myBuilder.isVisible(usage);
  }

  public UsageTarget @NotNull [] getTargets() {
    return myTargets;
  }

  private boolean isFilterDuplicateLines() {
    return myPresentation.isMergeDupLinesAvailable() && getUsageViewSettings().isFilterDuplicatedLine();
  }

  @ApiStatus.Internal
  public Usage getNextToSelect(@NotNull Usage toDelete) {
    ThreadingAssertions.assertEventDispatchThread();
    UsageNode usageNode = myUsageNodes.get(toDelete);
    if (usageNode == null || usageNode.getParent().getChildCount() == 0) return null;

    DefaultMutableTreeNode node = myRootPanel.mySupport.findNextNodeAfter(myTree, usageNode, true);
    if (node == null) node = myRootPanel.mySupport.findNextNodeAfter(myTree, usageNode, false); // last node

    return node == null ? null : node.getUserObject() instanceof Usage ? (Usage)node.getUserObject() : null;
  }

  @ApiStatus.Internal
  public Usage getNextToSelect(@NotNull Collection<? extends Usage> toDelete) {
    ThreadingAssertions.assertEventDispatchThread();
    Usage toSelect = null;
    for (Usage usage : toDelete) {
      Usage next = getNextToSelect(usage);
      if (next != null && !toDelete.contains(next)) {
        toSelect = next;
        break;
      }
    }
    return toSelect;
  }

  private interface ExclusionHandlerEx<Node> extends ExclusionHandler<Node> {
    void excludeNodeSilently(@NotNull Node node);
  }

  public static @Nullable KeyboardShortcut getShowUsagesWithSettingsShortcut() {
    return UsageViewUtil.getShowUsagesWithSettingsShortcut();
  }
}