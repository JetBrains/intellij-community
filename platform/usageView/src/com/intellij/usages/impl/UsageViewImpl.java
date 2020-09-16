// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.find.FindManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.lang.Language;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.*;
import com.intellij.usages.impl.actions.RuleAction;
import com.intellij.usages.rules.*;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class UsageViewImpl implements UsageViewEx {
  private static final GroupNode.NodeComparator COMPARATOR = new GroupNode.NodeComparator();
  private static final Logger LOG = Logger.getInstance(UsageViewImpl.class);
  @NonNls public static final String SHOW_RECENT_FIND_USAGES_ACTION_ID = "UsageView.ShowRecentFindUsages";

  private final UsageNodeTreeBuilder myBuilder;
  private MyPanel myRootPanel; // accessed in EDT only
  private JTree myTree; // accessed in EDT only
  private final ScheduledFuture<?> myFireEventsFuture;
  private Content myContent;

  private final UsageViewPresentation myPresentation;
  private final UsageTarget[] myTargets;
  protected UsageGroupingRule[] myGroupingRules;
  protected UsageFilteringRule[] myFilteringRules;
  private final Factory<? extends UsageSearcher> myUsageSearcherFactory;
  private final Project myProject;

  private volatile boolean mySearchInProgress = true;
  private final ExporterToTextFile myTextFileExporter = new ExporterToTextFile(this, getUsageViewSettings());
  private final SingleAlarm myUpdateAlarm = new SingleAlarm(() -> {
    if (isDisposed()) {
      return;
    }
    PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(getProject());
    documentManager.cancelAndRunWhenAllCommitted("UpdateUsageView", this::updateImmediately);
  }, 300);

  private final ExclusionHandlerEx<DefaultMutableTreeNode> myExclusionHandler;
  private final Map<Usage, UsageNode> myUsageNodes = new ConcurrentHashMap<>();
  public static final UsageNode NULL_NODE = new UsageNode(null, NullUsage.INSTANCE);
  private final ButtonPanel myButtonPanel;
  private boolean myNeedUpdateButtons;
  private final JComponent myAdditionalComponent = new JPanel(new BorderLayout());
  private volatile boolean isDisposed;
  private volatile boolean myChangesDetected;

  public static final Comparator<Usage> USAGE_COMPARATOR = (o1, o2) -> {
    if (o1 == o2) return 0;
    if (o1 == NullUsage.INSTANCE) return -1;
    if (o2 == NullUsage.INSTANCE) return 1;
    if (o1 instanceof Comparable && o2 instanceof Comparable && o1.getClass() == o2.getClass()) {
      //noinspection unchecked
      int selfcompared = ((Comparable<Usage>)o1).compareTo(o2);
      if (selfcompared != 0) return selfcompared;

      if (o1 instanceof UsageInFile && o2 instanceof UsageInFile) {
        UsageInFile u1 = (UsageInFile)o1;
        UsageInFile u2 = (UsageInFile)o2;

        VirtualFile f1 = u1.getFile();
        VirtualFile f2 = u2.getFile();

        if (f1 != null && f1.isValid() && f2 != null && f2.isValid()) {
          return f1.getPresentableUrl().compareTo(f2.getPresentableUrl());
        }
      }

      return 0;
    }
    return o1.toString().compareTo(o2.toString());
  };
  @NonNls public static final String HELP_ID = "ideaInterface.find";
  private UsageContextPanel myCurrentUsageContextPanel;
  private List<UsageContextPanel.Provider> myUsageContextPanelProviders;
  private UsageContextPanel.Provider myCurrentUsageContextProvider;

  private JPanel myCentralPanel; // accessed in EDT only

  @NotNull
  private final GroupNode myRoot;
  private final UsageViewTreeModelBuilder myModel;
  private final Object lock = new Object();
  private Splitter myPreviewSplitter; // accessed in EDT only
  private volatile ProgressIndicator associatedProgress; // the progress that current find usages is running under

  // true if usages tree is currently expanding or collapsing
  // (either at the end of find usages thanks to the 'expand usages after find' setting or
  // because the user pressed 'expand all' or 'collapse all' button. During this, some ugly hacks applied
  // to speed up the expanding (see getExpandedDescendants() here and UsageViewTreeCellRenderer.customizeCellRenderer())
  private boolean myExpandingCollapsing;
  private final UsageViewTreeCellRenderer myUsageViewTreeCellRenderer;
  private Usage myOriginUsage;
  @Nullable private Action myRerunAction;
  private boolean myDisposeSmartPointersOnClose = true;
  private final ExecutorService updateRequests = AppExecutorUtil
    .createBoundedApplicationPoolExecutor("Usage View Update Requests", AppExecutorUtil.getAppExecutorService(),
                                          JobSchedulerImpl.getJobPoolParallelism(), this);
  private final List<ExcludeListener> myExcludeListeners = ContainerUtil.createConcurrentList();
  private final Set<Pair<Class<? extends PsiReference>, Language>> myReportedReferenceClasses = ContainerUtil.newConcurrentSet();

  public UsageViewImpl(@NotNull Project project,
                       @NotNull UsageViewPresentation presentation,
                       UsageTarget @NotNull [] targets,
                       @Nullable Factory<? extends UsageSearcher> usageSearcherFactory) {
    // fire events every 50 ms, not more often to batch requests
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

    UsageModelTracker myModelTracker = new UsageModelTracker(project);
    Disposer.register(this, myModelTracker);

    myGroupingRules = getActiveGroupingRules(project, getUsageViewSettings());
    myFilteringRules = getActiveFilteringRules(project);

    myBuilder = new UsageNodeTreeBuilder(myTargets, myGroupingRules, myFilteringRules, myRoot, myProject);

    MessageBusConnection messageBusConnection = myProject.getMessageBus().connect(this);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, this::rulesChanged);
    messageBusConnection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        rulesChanged();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        rulesChanged();
      }
    });

    myUsageViewTreeCellRenderer = new UsageViewTreeCellRenderer(this);
    if (!myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(() -> {
        // lock here to avoid concurrent execution of this init and dispose in other thread
        synchronized (lock) {
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
            SwingUtilities.invokeLater(() -> {
              if (!isDisposed()) {
                updateOnSelectionChanged();
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
      });
    }
    myExclusionHandler = new ExclusionHandlerEx<DefaultMutableTreeNode>() {
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
        collectAllChildNodes(node, nodes);
        collectParentNodes(node, true, nodes);
        setExcludeNodes(nodes, true, true);
      }

      @Override
      public void excludeNodeSilently(@NotNull DefaultMutableTreeNode node) {
        Set<Node> nodes = new HashSet<>();
        collectAllChildNodes(node, nodes);
        collectParentNodes(node, true, nodes);
        setExcludeNodes(nodes, true, false);
      }

      // include the parent if its all children (except the "node" itself) excluded flags are "almostAllChildrenExcluded"
      private void collectParentNodes(@NotNull DefaultMutableTreeNode node,
                                      boolean almostAllChildrenExcluded,
                                      @NotNull Set<? super Node> nodes) {
        TreeNode parent = node.getParent();
        if (parent == myRoot || !(parent instanceof GroupNode)) return;
        GroupNode parentNode = (GroupNode)parent;
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
        collectAllChildNodes(node, nodes);
        collectParentNodes(node, false, nodes);
        setExcludeNodes(nodes, false, true);
      }

      @Override
      public boolean isActionEnabled(boolean isExcludeAction) {
        return getPresentation().isExcludeAvailable();
      }

      @Override
      public void onDone(boolean isExcludeAction) {
        EDT.assertIsEdt();
        if (myRootPanel.hasNextOccurence()) {
          myRootPanel.goNextOccurence();
        }
      }
    };
  }

  @NotNull
  public UsageViewSettings getUsageViewSettings() {
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
   * Type of a change that occurs in the GroupNode.myChildren
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
    @NotNull
    private final NodeChangeType nodeChangeType;
    /**
     * The one that was replaced or removed, or a parent for the added node
     */
    @NotNull
    private final Node parentNode;

    /**
     * The one that was added or the one that replaced the first
     */
    @Nullable
    private final Node childNode;

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

    private boolean isValid() {
      boolean parentValid = !parentNode.isStructuralChangeDetected();

      boolean childValid = true;
      if (childNode != null) {
        childValid = !childNode.isStructuralChangeDetected();
      }

      return parentValid && childValid;
    }
  }

  /**
   * Executes a new appendUsage request with the updateRequests executor
   */
  private final Consumer<Usage> invalidatedUsagesConsumer = (@NotNull Usage usage) -> {
    if (!getPresentation().isDetachedMode() && !isDisposed) {
      myUsageNodes.remove(usage);
      addUpdateRequest(() -> ReadAction.run(() -> doAppendUsage(usage)));
    }
  };


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
    EDT.assertIsEdt();

    syncModelWithSwingNodes();
    fireEventsForChangedNodes();
  }

  /**
   * group nodes from changedNodesToFire by their parents
   * and issue corresponding javax.swing.tree.DefaultTreeModel.fireTreeNodesChanged()
   */
  private void fireEventsForChangedNodes() {
    TIntArrayList indicesToFire = new TIntArrayList();
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

      myModel.fireTreeNodesChanged(parentNode, myModel.getPathToRoot(parentNode), indicesToFire.toNativeArray(),
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

    TIntArrayList indicesToFire = new TIntArrayList();
    List<Node> nodesToFire = new ArrayList<>();

    //first grouping changes by parent node
    Map<Node, List<NodeChange>> groupByParent = nodeChanges.stream().collect(groupingBy(NodeChange::getParentNode));
    for (Node parentNode : groupByParent.keySet()) {
      synchronized (parentNode) {
        List<NodeChange> changes = groupByParent.get(parentNode);
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
            if (!change.isValid()) {
              continue;
            }
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
              myModel.fireTreeNodesInserted(parentNode, myModel.getPathToRoot(parentNode), indicesToFire.toNativeArray(),
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

  private void clearRendererCache() {
    EDT.assertIsEdt();
    if (myExpandingCollapsing) return; // to avoid quadratic row enumeration
    // clear renderer cache of node preferred size
    TreeUI ui = myTree.getUI();
    if (ui instanceof BasicTreeUI) {
      AbstractLayoutCache treeState = ReflectionUtil.getField(BasicTreeUI.class, ui, AbstractLayoutCache.class, "treeState");
      Rectangle visibleRect = myTree.getVisibleRect();
      int rowForLocation = myTree.getClosestRowForLocation(0, visibleRect.y);
      int visibleRowCount = getVisibleRowCount();
      List<Node> toUpdate = new ArrayList<>();
      for (int i = rowForLocation + visibleRowCount + 1; i >= rowForLocation; i--) {
        TreePath eachPath = myTree.getPathForRow(i);
        if (eachPath == null) continue;

        treeState.invalidatePathBounds(eachPath);
        Object node = eachPath.getLastPathComponent();
        if (node instanceof UsageNode) {
          toUpdate.add((Node)node);
        }
      }
      queueUpdateBulk(toUpdate, () -> {
        if (!isDisposed()) {
          myTree.repaint(visibleRect);
        }
      });
    }
    else {
      myTree.setCellRenderer(myUsageViewTreeCellRenderer);
    }
  }

  private int getVisibleRowCount() {
    EDT.assertIsEdt();
    return TreeUtil.getVisibleRowCount(myTree);
  }

  private void setupCentralPanel() {
    EDT.assertIsEdt();

    JScrollPane treePane = ScrollPaneFactory.createScrollPane(myTree);
    // add reaction to scrolling:
    // since the UsageViewTreeCellRenderer ignores invisible nodes (outside the viewport), their preferred size is incorrect
    // and we need to recalculate them when the node scrolled into the visible rectangle
    treePane.getViewport().addChangeListener(__ -> clearRendererCache());
    myPreviewSplitter = new OnePixelSplitter(false, 0.5f, 0.1f, 0.9f);
    myPreviewSplitter.setFirstComponent(treePane);

    myCentralPanel.add(myPreviewSplitter, BorderLayout.CENTER);

    updateUsagesContextPanels();

    myCentralPanel.add(myAdditionalComponent, BorderLayout.SOUTH);
    myAdditionalComponent.add(myButtonPanel, BorderLayout.SOUTH);
  }

  private void updateUsagesContextPanels() {
    EDT.assertIsEdt();
    disposeUsageContextPanels();
    if (isPreviewUsages()) {
      myPreviewSplitter.setProportion(getUsageViewSettings().getPreviewUsagesSplitterProportion());
      JBTabbedPane tabbedPane = new JBTabbedPane(SwingConstants.BOTTOM);
      tabbedPane.setTabComponentInsets(null);

      UsageContextPanel.Provider[] extensions = UsageContextPanel.Provider.EP_NAME.getExtensions(myProject);
      myUsageContextPanelProviders = ContainerUtil.filter(extensions, provider -> provider.isAvailableFor(this));
      Map<@NlsContexts.TabTitle String, JComponent> components = new LinkedHashMap<>();
      for (UsageContextPanel.Provider provider : myUsageContextPanelProviders) {
        JComponent component;
        if (myCurrentUsageContextProvider == null || myCurrentUsageContextProvider == provider) {
          myCurrentUsageContextProvider = provider;
          myCurrentUsageContextPanel = provider.create(this);
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
    EDT.assertIsEdt();
    myCurrentUsageContextProvider = provider;
    updateUsagesContextPanels();
    updateOnSelectionChanged();
  }

  private void disposeUsageContextPanels() {
    EDT.assertIsEdt();
    if (myCurrentUsageContextPanel != null) {
      saveSplitterProportions();
      Disposer.dispose(myCurrentUsageContextPanel);
      myCurrentUsageContextPanel = null;
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
    for (UsageFilteringRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.getActiveRules(project));
    }
    return list.toArray(UsageFilteringRule.EMPTY_ARRAY);
  }


  protected static UsageGroupingRule @NotNull [] getActiveGroupingRules(@NotNull Project project,
                                                                        @NotNull UsageViewSettings usageViewSettings) {
    List<UsageGroupingRuleProvider> providers = UsageGroupingRuleProvider.EP_NAME.getExtensionList();
    List<UsageGroupingRule> list = new ArrayList<>(providers.size());
    for (UsageGroupingRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.getActiveRules(project, usageViewSettings));
    }

    list.sort(Comparator.comparingInt(UsageGroupingRule::getRank));
    return list.toArray(UsageGroupingRule.EMPTY_ARRAY);
  }

  private void initTree() {
    EDT.assertIsEdt();
    myTree.setShowsRootHandles(true);
    SmartExpander.installOn(myTree);
    TreeUtil.installActions(myTree);
    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);

    TreeUtil.promiseSelectFirst(myTree);
    PopupHandler.installPopupHandler(myTree, IdeActions.GROUP_USAGE_VIEW_POPUP, ActionPlaces.USAGE_VIEW_POPUP);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        clearRendererCache();

        TreePath path = event.getPath();
        Object component = path.getLastPathComponent();
        if (component instanceof Node) {
          Node node = (Node)component;
          if (!myExpandingCollapsing && node.needsUpdate()) {
            List<Node> toUpdate = new ArrayList<>();
            checkNodeValidity(node, path, toUpdate);
            queueUpdateBulk(toUpdate, EmptyRunnable.getInstance());
          }
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        clearRendererCache();
      }
    });

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree, o -> {
      Object value = o.getLastPathComponent();
      TreeCellRenderer renderer = myTree.getCellRenderer();
      if (renderer instanceof UsageViewTreeCellRenderer) {
        UsageViewTreeCellRenderer coloredRenderer = (UsageViewTreeCellRenderer)renderer;
        return coloredRenderer.getPlainTextForNode(value);
      }
      return value == null ? null : value.toString();
    }, true);
  }

  @NotNull
  private JComponent createActionsToolbar() {
    EDT.assertIsEdt();

    DefaultActionGroup group = new DefaultActionGroup() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        myButtonPanel.update();
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

  @NotNull
  private JComponent toUsageViewToolbar(@NotNull DefaultActionGroup group) {
    EDT.assertIsEdt();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, group, false);
    actionToolbar.setTargetComponent(myRootPanel);
    return actionToolbar.getComponent();
  }

  @SuppressWarnings("WeakerAccess") // used in rider
  protected boolean isPreviewUsageActionEnabled() {
    return true;
  }

  public void addFilteringActions(@NotNull DefaultActionGroup group) {
    EDT.assertIsEdt();
    addFilteringActions(group, true);
  }

  protected void addFilteringActions(@NotNull DefaultActionGroup group, boolean includeExtensionPoints) {
    if (getPresentation().isMergeDupLinesAvailable()) {
      MergeDupLines mergeDupLines = new MergeDupLines();
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
    for (UsageFilteringRuleProvider provider : UsageFilteringRuleProvider.EP_NAME.getExtensionList()) {
      AnAction[] actions = provider.createFilteringActions(this);
      for (AnAction action : actions) {
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
    EDT.assertIsEdt();

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
    group.getTemplatePresentation().setMultipleChoice(true);
    AnAction[] groupingActions = createGroupingActions();
    for (AnAction a : groupingActions) {
      a.getTemplatePresentation().setMultipleChoice(true);
    }
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
      group,
      filteringSubgroup,
      expandAllAction,
      collapseAllAction,
      new Separator(),
      isPreviewUsageActionEnabled() ? new PreviewUsageAction(this) : null,
      new Separator(),
      canShowSettings() ? new ShowSettings() : null,
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
    ActionUtil.moveActionTo(list, UsageViewBundle.message("action.group.by.module"),
                            UsageViewBundle.message("action.flatten.modules"), true);
    return list.toArray(AnAction.EMPTY_ARRAY);
  }


  /**
   * create*Action() methods can be used in createActions() method in subclasses to create a toolbar
   */
  protected @NotNull AnAction createPreviewAction() {
    return new PreviewUsageAction(this);
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
    ActionUtil.sortAlphabetically(actions);
  }

  private boolean shouldTreeReactNowToRuleChanges() {
    EDT.assertIsEdt();
    return myPresentation.isDetachedMode() || myTree.isShowing();
  }

  private boolean rulesChanged; // accessed in EDT only

  private void rulesChanged() {
    EDT.assertIsEdt();
    if (!shouldTreeReactNowToRuleChanges()) {
      rulesChanged = true;
      return;
    }

    List<UsageState> states = new ArrayList<>();
    if (myTree != null) {
      captureUsagesExpandState(new TreePath(myTree.getModel().getRoot()), states);
    }
    List<Usage> allUsages = new ArrayList<>(myUsageNodes.keySet());
    allUsages.sort(USAGE_COMPARATOR);
    Set<Usage> excludedUsages = getExcludedUsages();
    reset();
    myGroupingRules = getActiveGroupingRules(myProject, getUsageViewSettings());
    myFilteringRules = getActiveFilteringRules(myProject);

    myBuilder.setGroupingRules(myGroupingRules);
    myBuilder.setFilteringRules(myFilteringRules);

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
    //noinspection SSBasedInspection
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
    EDT.assertIsEdt();
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
    EDT.assertIsEdt();
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

  /**
   * Allows to skip a lot of {@link #clearRendererCache}, received via {@link TreeExpansionListener}.
   *
   * @param task that expands or collapses a tree
   */
  private void doExpandingCollapsing(@NotNull Runnable task) {
    if (isDisposed()) return;
    EDT.assertIsEdt();
    fireEvents();  // drain all remaining insertion events in the queue

    myExpandingCollapsing = true;
    try {
      task.run();
    }
    finally {
      myExpandingCollapsing = false;
    }
    clearRendererCache();
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
    EDT.assertIsEdt();
    return (DefaultMutableTreeNode)myTree.getModel().getRoot();
  }

  public void select() {
    EDT.assertIsEdt();
    // can be null during ctr execution
    if (myTree != null) {
      myTree.requestFocusInWindow();
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public static KeyboardShortcut getShowUsagesWithSettingsShortcut() {
    return ActionManager.getInstance().getKeyboardShortcut("ShowSettingsAndFindUsages");
  }

  static KeyboardShortcut getShowUsagesWithSettingsShortcut(UsageTarget @NotNull [] targets) {
    ConfigurableUsageTarget configurableTarget = getConfigurableTarget(targets);
    return configurableTarget == null ? getShowUsagesWithSettingsShortcut() : configurableTarget.getShortcut();
  }

  @Override
  public void associateProgress(@NotNull ProgressIndicator indicator) {
    associatedProgress = indicator;
  }

  private static final class MergeDupLines extends RuleAction {
    private MergeDupLines() {
      super(UsageViewBundle.message("action.merge.same.line"), AllIcons.Toolbar.Filterdups);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean getOptionValue(@NotNull AnActionEvent e) {
      return getUsageViewSettings(e).isFilterDuplicatedLine();
    }

    @Override
    protected void setOptionValue(@NotNull AnActionEvent e, boolean value) {
      getUsageViewSettings(e).setFilterDuplicatedLine(value);
    }
  }

  private final class ShowSettings extends AnAction {
    private ShowSettings() {
      super(UsageViewBundle.message("action.text.usage.view.settings"), null, AllIcons.General.GearPlain);
      ConfigurableUsageTarget configurableUsageTarget = getConfigurableTarget(myTargets);
      Supplier<String> description = null;
      try {
        description = configurableUsageTarget == null
                      ? null
                      : UsageViewBundle.messagePointer("action.ShowSettings.show.settings.for.description",
                                                       configurableUsageTarget.getLongDescriptiveName());
      }
      catch (IndexNotReadyException ignored) {
      }
      if (description == null) {
        description = UsageViewBundle.messagePointer("action.ShowSettings.show.find.usages.settings.dialog.description");
      }
      getTemplatePresentation().setDescription(description);
      KeyboardShortcut shortcut =
        configurableUsageTarget == null ? getShowUsagesWithSettingsShortcut() : configurableUsageTarget.getShortcut();
      if (shortcut != null) {
        registerCustomShortcutSet(new CustomShortcutSet(shortcut), getComponent());
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
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
    EDT.assertIsEdt();
    myUsageNodes.clear();
    myModel.reset();
    synchronized (modelToSwingNodeChanges) {
      modelToSwingNodeChanges.clear();
    }

    if (!myPresentation.isDetachedMode()) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> expandTree(2));
    }
  }

  void drainQueuedUsageNodes() {
    assert !ApplicationManager.getApplication().isDispatchThread() : Thread.currentThread();
    UIUtil.invokeAndWaitIfNeeded((Runnable)this::fireEvents);
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

  protected void addUpdateRequest(@NotNull Runnable request) {
    updateRequests.execute(request);
  }

  @Override
  public void waitForUpdateRequestsCompletion() {
    assert !ApplicationManager.getApplication().isDispatchThread();
    try {
      ((BoundedTaskExecutor)updateRequests).waitAllTasksExecuted(10, TimeUnit.MINUTES);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @NotNull
  @Override
  public CompletableFuture<?> appendUsagesInBulk(@NotNull Collection<? extends Usage> usages) {
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
    assert !ApplicationManager.getApplication().isDispatchThread();
    // invoke in ReadAction to be be sure that usages are not invalidated while the tree is being built
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!usage.isValid()) {
      // because the view is built incrementally, the usage may be already invalid, so need to filter such cases
      return null;
    }

    for (UsageViewElementsListener listener : UsageViewElementsListener.EP_NAME.getExtensionList()) {
      listener.beforeUsageAdded(this, usage);
    }
    reportToFUS(usage);

    UsageNode child = myBuilder.appendOrGet(usage, isFilterDuplicateLines(), edtModelToSwingNodeChangesQueue, invalidatedUsagesConsumer);
    myUsageNodes.put(usage, child == null ? NULL_NODE : child);

    if (child != null && getPresentation().isExcludeAvailable()) {
      for (UsageViewElementsListener listener : UsageViewElementsListener.EP_NAME.getExtensionList()) {
        if (listener.isExcludedByDefault(this, usage)) {
          myExclusionHandler.excludeNodeSilently(child);
        }
      }
    }

    for (Node node = child; node != myRoot && node != null; node = (Node)node.getParent()) {
      node.update(this, edtFireTreeNodesChangedQueue);
    }

    return child;
  }

  private void reportToFUS(@NotNull Usage usage) {
    if (usage instanceof PsiElementUsage) {
      PsiElementUsage elementUsage = (PsiElementUsage)usage;
      Class<? extends PsiReference> referenceClass = elementUsage.getReferenceClass();
      PsiElement element = elementUsage.getElement();
      if (referenceClass != null || element != null) {
        Pair<Class<? extends PsiReference>, Language> pair = Pair.create(referenceClass, element != null ? element.getLanguage() : null);
        if (myReportedReferenceClasses.add(pair)) {
          UsageViewStatisticsCollector.logUsageShown(myProject, pair.first, pair.second);
        }
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

  @NotNull
  private Stream<UsageNode> usagesToNodes(@NotNull Stream<? extends Usage> usages) {
    return usages
      .map(myUsageNodes::get)
      .filter(node -> node != NULL_NODE && node != null);
  }

  @Override
  public void selectUsages(Usage @NotNull [] usages) {
    EDT.assertIsEdt();
    TreePath[] paths = usagesToNodes(Arrays.stream(usages))
      .map(node -> new TreePath(node.getPath()))
      .toArray(TreePath[]::new);

    myTree.setSelectionPaths(paths);
    if (paths.length != 0) myTree.scrollPathToVisible(paths[0]);
  }

  @NotNull
  @Override
  public JComponent getPreferredFocusableComponent() {
    EDT.assertIsEdt();
    return myTree != null ? myTree : getComponent();
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    EDT.assertIsEdt();
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
    EDT.assertIsEdt();
    if (isDisposed()) return;
    TreeNode root = (TreeNode)myTree.getModel().getRoot();
    List<Node> toUpdate = new ArrayList<>();
    checkNodeValidity(root, new TreePath(root), toUpdate);
    queueUpdateBulk(toUpdate, EmptyRunnable.getInstance());
    updateOnSelectionChanged();
  }

  private void queueUpdateBulk(@NotNull List<? extends Node> toUpdate, @NotNull Runnable onCompletedInEdt) {
    if (toUpdate.isEmpty() || isDisposed()) return;
    ReadAction
      .nonBlocking(() -> {
        for (Node node : toUpdate) {
          try {
            node.update(this, edtFireTreeNodesChangedQueue);
          }
          catch (IndexNotReadyException ignore) {
          }
        }
      })
      .expireWith(this)
      .finishOnUiThread(ModalityState.defaultModalityState(), __ -> onCompletedInEdt.run())
      .submit(updateRequests);
  }

  private void updateImmediatelyNodesUpToRoot(@NotNull Collection<? extends Node> nodes) {
    EDT.assertIsEdt();
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


  private void updateOnSelectionChanged() {
    EDT.assertIsEdt();
    if (myCurrentUsageContextPanel != null) {
      try {
        myCurrentUsageContextPanel.updateLayout(getSelectedUsageInfos());
      }
      catch (IndexNotReadyException ignore) {
      }
    }
  }

  private void checkNodeValidity(@NotNull TreeNode node, @NotNull TreePath path, @NotNull List<? super Node> result) {
    EDT.assertIsEdt();
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
    myUpdateAlarm.cancelAndRequest();
  }

  @Override
  public void close() {
    cancelCurrentSearch();
    if (myContent != null) {
      UsageViewContentManager.getInstance(myProject).closeContent(myContent);
    }
  }

  private void saveSplitterProportions() {
    EDT.assertIsEdt();
    getUsageViewSettings().setPreviewUsagesSplitterProportion(myPreviewSplitter.getProportion());
  }

  @Override
  public void dispose() {
    EDT.assertIsEdt();
    disposeUsageContextPanels();
    synchronized (lock) {
      isDisposed = true;
      myUpdateAlarm.cancelAllRequests();

      cancelCurrentSearch();
      myRerunAction = null;
      if (myTree != null) {
        ToolTipManager.sharedInstance().unregisterComponent(myTree);
      }
    }
    if (myDisposeSmartPointersOnClose) {
      disposeSmartPointers();
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
        UsageNode firstUsageNode = myModel.getFirstUsageNode();
        if (firstUsageNode == null) return;

        Node node = getSelectedNode();
        if (node != null && !Comparing.equal(new TreePath(node.getPath()), TreeUtil.getFirstNodePath(myTree))) {
          // user has selected node already
          return;
        }
        showNode(firstUsageNode);
        if (getUsageViewSettings().isExpanded() && myUsageNodes.size() < 10000) {
          expandAll();
        }
      });
    }
  }

  public boolean isDisposed() {
    return isDisposed || myProject.isDisposed();
  }

  private void showNode(@NotNull UsageNode node) {
    EDT.assertIsEdt();
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
    EDT.assertIsEdt();
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
  public void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull @NlsContexts.Button String text, char mnemonic) {
    // implemented method is deprecated, so, it just calls non-deprecated overloading one
    addButtonToLowerPane(runnable, text);
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

  @NotNull
  @Override
  public UsageViewPresentation getPresentation() {
    return myPresentation;
  }

  public boolean canPerformReRun() {
    if (myRerunAction != null && myRerunAction.isEnabled()) return allTargetsAreValid();
    try {
      return myUsageSearcherFactory != null && allTargetsAreValid() && myUsageSearcherFactory.create() != null;
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

  @NotNull
  private Set<Usage> getReadOnlyUsages() {
    Set<Usage> result = new THashSet<>();
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

  @NotNull
  private Set<VirtualFile> getReadOnlyUsagesFiles() {
    Set<Usage> usages = getReadOnlyUsages();
    Set<VirtualFile> result = new THashSet<>();
    for (Usage usage : usages) {
      if (usage instanceof UsageInFile) {
        UsageInFile usageInFile = (UsageInFile)usage;
        VirtualFile file = usageInFile.getFile();
        if (file != null && file.isValid()) result.add(file);
      }

      if (usage instanceof UsageInFiles) {
        UsageInFiles usageInFiles = (UsageInFiles)usage;
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
  @NotNull
  public Set<Usage> getExcludedUsages() {
    Set<Usage> result = new THashSet<>();
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


  @Nullable
  private Node getSelectedNode() {
    EDT.assertIsEdt();
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    if (leadSelectionPath == null) return null;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent();
    return node instanceof Node ? (Node)node : null;
  }

  private Node @Nullable [] getSelectedNodes() {
    EDT.assertIsEdt();
    TreePath[] leadSelectionPath = myTree.getSelectionPaths();
    if (leadSelectionPath == null || leadSelectionPath.length == 0) return null;

    List<Node> result = new ArrayList<>();
    for (TreePath comp : leadSelectionPath) {
      Object lastPathComponent = comp.getLastPathComponent();
      if (lastPathComponent instanceof Node) {
        Node node = (Node)lastPathComponent;
        result.add(node);
      }
    }
    return result.isEmpty() ? null : result.toArray(new Node[0]);
  }

  @Override
  @NotNull
  public Set<Usage> getSelectedUsages() {
    EDT.assertIsEdt();
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) {
      return Collections.emptySet();
    }

    Set<Usage> usages = new THashSet<>();
    for (TreePath selectionPath : selectionPaths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      collectUsages(node, usages);
    }

    return usages;
  }

  @Override
  @NotNull
  public Set<Usage> getUsages() {
    return myUsageNodes.keySet();
  }

  @Override
  @NotNull
  public List<Usage> getSortedUsages() {
    List<Usage> usages = new ArrayList<>(getUsages());
    usages.sort(USAGE_COMPARATOR);
    return usages;
  }

  private static void collectUsages(@NotNull DefaultMutableTreeNode node, @NotNull Set<? super Usage> usages) {
    if (node instanceof UsageNode) {
      UsageNode usageNode = (UsageNode)node;
      Usage usage = usageNode.getUsage();
      usages.add(usage);
    }

    Enumeration<?> enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)enumeration.nextElement();
      collectUsages(child, usages);
    }
  }

  private static void collectAllChildNodes(@NotNull DefaultMutableTreeNode node, @NotNull Set<? super Node> nodes) {
    if (node instanceof Node) {
      nodes.add((Node)node);
    }

    Enumeration<?> enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)enumeration.nextElement();
      collectAllChildNodes(child, nodes);
    }
  }

  private UsageTarget @Nullable [] getSelectedUsageTargets() {
    EDT.assertIsEdt();
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) return null;

    Set<UsageTarget> targets = new THashSet<>();
    for (TreePath selectionPath : selectionPaths) {
      Object lastPathComponent = selectionPath.getLastPathComponent();
      if (lastPathComponent instanceof UsageTargetNode) {
        UsageTargetNode usageTargetNode = (UsageTargetNode)lastPathComponent;
        UsageTarget target = usageTargetNode.getTarget();
        if (target.isValid()) {
          targets.add(target);
        }
      }
    }

    return targets.isEmpty() ? null : targets.toArray(UsageTarget.EMPTY_ARRAY);
  }

  @Nullable
  private static Navigatable getNavigatableForNode(@NotNull DefaultMutableTreeNode node, boolean allowRequestFocus) {
    Object userObject = node.getUserObject();
    if (userObject instanceof Navigatable) {
      Navigatable navigatable = (Navigatable)userObject;
      return navigatable.canNavigate() ? new Navigatable() {
        @Override
        public void navigate(boolean requestFocus) {
          navigatable.navigate(allowRequestFocus && requestFocus);
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

  /* nodes with non-valid data are not included */
  private static Navigatable[] getNavigatablesForNodes(Node[] nodes) {
    if (nodes == null) {
      return null;
    }
    List<Navigatable> result = new ArrayList<>();
    for (Node node : nodes) {
      Object userObject = node.getUserObject();
      if (userObject instanceof Navigatable) {
        result.add((Navigatable)userObject);
      }
    }
    return result.toArray(new Navigatable[0]);
  }

  boolean areTargetsValid() {
    return myModel.areTargetsValid();
  }

  private final class MyPanel extends JPanel implements TypeSafeDataProvider, OccurenceNavigator, Disposable {
    @Nullable private OccurenceNavigatorSupport mySupport;
    private final CopyProvider myCopyProvider;

    private MyPanel(@NotNull JTree tree) {
      mySupport = new OccurenceNavigatorSupport(tree) {
        @Override
        protected Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node) {
          if (node.getChildCount() > 0) return null;
          if (node instanceof Node && ((Node)node).isExcluded()) return null;
          return getNavigatableForNode(node, !myPresentation.isReplaceMode());
        }

        @NotNull
        @Override
        public String getNextOccurenceActionName() {
          return UsageViewBundle.message("action.next.occurrence");
        }

        @NotNull
        @Override
        public String getPreviousOccurenceActionName() {
          return UsageViewBundle.message("action.previous.occurrence");
        }
      };
      myCopyProvider = new TextCopyProvider() {
        @Nullable
        @Override
        public Collection<String> getTextLinesToCopy() {
          Node[] selectedNodes = getSelectedNodes();
          if (selectedNodes != null && selectedNodes.length > 0) {
            List<String> lines = new ArrayList<>();
            for (Node node : selectedNodes) {
              lines.add(node.getText(UsageViewImpl.this));
            }
            return lines;
          }
          return null;
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

    @NotNull
    @Override
    public String getNextOccurenceActionName() {
      return mySupport != null ? mySupport.getNextOccurenceActionName() : "";
    }

    @NotNull
    @Override
    public String getPreviousOccurenceActionName() {
      return mySupport != null ? mySupport.getPreviousOccurenceActionName() : "";
    }

    @Override
    public void calcData(@NotNull DataKey key, @NotNull DataSink sink) {
      if (key == CommonDataKeys.PROJECT) {
        sink.put(CommonDataKeys.PROJECT, myProject);
      }
      else if (key == USAGE_VIEW_KEY) {
        sink.put(USAGE_VIEW_KEY, UsageViewImpl.this);
      }
      else if (key == ExclusionHandler.EXCLUSION_HANDLER) {
        sink.put(ExclusionHandler.EXCLUSION_HANDLER, myExclusionHandler);
      }

      else if (key == CommonDataKeys.NAVIGATABLE_ARRAY) {
        Node[] nodes = ApplicationManager.getApplication().isDispatchThread() ? getSelectedNodes() : null;
        sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatablesForNodes(nodes));
      }

      else if (key == PlatformDataKeys.EXPORTER_TO_TEXT_FILE) {
        sink.put(PlatformDataKeys.EXPORTER_TO_TEXT_FILE, myTextFileExporter);
      }

      else if (key == USAGES_KEY) {
        Set<Usage> selectedUsages = ApplicationManager.getApplication().isDispatchThread() ? getSelectedUsages() : null;
        sink.put(USAGES_KEY, selectedUsages == null ? null : selectedUsages.toArray(Usage.EMPTY_ARRAY));
      }

      else if (key == USAGE_TARGETS_KEY) {
        UsageTarget[] targets = ApplicationManager.getApplication().isDispatchThread() ? getSelectedUsageTargets() : null;
        sink.put(USAGE_TARGETS_KEY, targets);
      }

      else if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
        Set<Usage> usages = ApplicationManager.getApplication().isDispatchThread() ? getSelectedUsages() : null;
        Usage[] ua = usages == null ? null : usages.toArray(Usage.EMPTY_ARRAY);
        UsageTarget[] usageTargets = ApplicationManager.getApplication().isDispatchThread() ? getSelectedUsageTargets() : null;
        VirtualFile[] data = UsageDataUtil.provideVirtualFileArray(ua, usageTargets);
        sink.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, data);
      }
      else if (key == PlatformDataKeys.HELP_ID) {
        sink.put(PlatformDataKeys.HELP_ID, HELP_ID);
      }
      else if (key == PlatformDataKeys.COPY_PROVIDER) {
        sink.put(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
      }
      else if (key == LangDataKeys.PSI_ELEMENT_ARRAY) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          sink.put(LangDataKeys.PSI_ELEMENT_ARRAY, getSelectedUsages()
            .stream()
            .filter(u -> u instanceof PsiElementUsage)
            .map(u -> ((PsiElementUsage)u).getElement())
            .filter(Objects::nonNull)
            .toArray(PsiElement.ARRAY_FACTORY::create));
        }
      }
      else {
        // can arrive here outside EDT from usage view preview.
        // ignore all these fancy actions in this case.
        Node node = ApplicationManager.getApplication().isDispatchThread() ? getSelectedNode() : null;
        if (node != null) {
          Object userObject = node.getUserObject();
          if (userObject instanceof TypeSafeDataProvider) {
            ((TypeSafeDataProvider)userObject).calcData(key, sink);
          }
          else if (userObject instanceof DataProvider) {
            DataProvider dataProvider = (DataProvider)userObject;
            Object data = dataProvider.getData(key.getName());
            if (data != null) {
              sink.put(key, data);
            }
          }
        }
      }
    }
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
        if (component instanceof JButton) {
          JButton button = (JButton)component;
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
      EDT.assertIsEdt();
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
      if (myCannotMakeString != null && myChangesDetected) {
        String title = UsageViewBundle.message("changes.detected.error.title");
        if (canPerformReRun()) {
          String[] options = {UsageViewBundle.message("action.description.rerun"), UsageViewBundle.message("button.text.continue"),
            UsageViewBundle.message("usage.view.cancel.button")};
          String message = myCannotMakeString + "\n\n" + UsageViewBundle.message("dialog.rerun.search");
          int answer =
            Messages.showYesNoCancelDialog(myProject, message, title, options[0], options[1], options[2], Messages.getErrorIcon());
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

      // can't dispose pointers because refactoring might want to re-use the usage infos from the preview
      myDisposeSmartPointersOnClose = false;
      close();

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
        disposeSmartPointers();
      }
    }
  }

  private List<UsageInfo> getSelectedUsageInfos() {
    EDT.assertIsEdt();
    return USAGE_INFO_LIST_KEY.getData(DataManager.getInstance().getDataContext(myRootPanel));
  }

  @NotNull
  public GroupNode getRoot() {
    return myRoot;
  }

  @TestOnly
  @NotNull
  public String getNodeText(@NotNull TreeNode node) {
    return myUsageViewTreeCellRenderer.getPlainTextForNode(node);
  }

  public boolean isVisible(@NotNull Usage usage) {
    return myBuilder != null && myBuilder.isVisible(usage);
  }

  public UsageTarget @NotNull [] getTargets() {
    return myTargets;
  }

  /**
   * The element the "find usages" action was invoked on.
   * E.g. if the "find usages" was invoked on the reference "getName(2)" pointing to the method "getName()" then the origin usage is this reference.
   */
  public void setOriginUsage(@NotNull Usage usage) {
    myOriginUsage = usage;
  }

  /**
   * true if the {@param usage} points to the element the "find usages" action was invoked on
   */
  public boolean isOriginUsage(@NotNull Usage usage) {
    return
      myOriginUsage instanceof UsageInfo2UsageAdapter &&
      usage instanceof UsageInfo2UsageAdapter &&
      ((UsageInfo2UsageAdapter)usage).getUsageInfo().equals(((UsageInfo2UsageAdapter)myOriginUsage).getUsageInfo());
  }

  private boolean isFilterDuplicateLines() {
    return myPresentation.isMergeDupLinesAvailable() && getUsageViewSettings().isFilterDuplicatedLine();
  }

  public Usage getNextToSelect(@NotNull Usage toDelete) {
    EDT.assertIsEdt();
    UsageNode usageNode = myUsageNodes.get(toDelete);
    if (usageNode == null || usageNode.getParent().getChildCount() == 0) return null;

    DefaultMutableTreeNode node = myRootPanel.mySupport.findNextNodeAfter(myTree, usageNode, true);
    if (node == null) node = myRootPanel.mySupport.findNextNodeAfter(myTree, usageNode, false); // last node

    return node == null ? null : node.getUserObject() instanceof Usage ? (Usage)node.getUserObject() : null;
  }

  public Usage getNextToSelect(@NotNull Collection<? extends Usage> toDelete) {
    EDT.assertIsEdt();
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
}
