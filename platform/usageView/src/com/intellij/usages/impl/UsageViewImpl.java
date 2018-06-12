// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.openapi.vfs.VfsUtilCore;
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
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.*;
import com.intellij.usages.rules.*;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Queue;
import com.intellij.util.enumeration.EmptyEnumeration;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.DialogUtil;
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
import javax.swing.event.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.util.containers.Queue;


/**
 * @author max
 */
public class UsageViewImpl implements UsageViewEx {
  @NonNls public static final String SHOW_RECENT_FIND_USAGES_ACTION_ID = "UsageView.ShowRecentFindUsages";

  private final UsageNodeTreeBuilder myBuilder;
  private MyPanel myRootPanel; // accessed in EDT only
  private JTree myTree; // accessed in EDT only
  private final ScheduledFuture<?> myFireEventsFuture;
  private Content myContent;

  private final UsageViewPresentation myPresentation;
  private final UsageTarget[] myTargets;
  private final Factory<UsageSearcher> myUsageSearcherFactory;
  private final Project myProject;

  private volatile boolean mySearchInProgress = true;
  private final ExporterToTextFile myTextFileExporter = new ExporterToTextFile(this, getUsageViewSettings());
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private final ExclusionHandler<DefaultMutableTreeNode> myExclusionHandler;
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
      final int selfcompared = ((Comparable<Usage>)o1).compareTo(o2);
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

  private JPanel myCentralPanel;

  private final GroupNode myRoot;
  private final UsageViewTreeModelBuilder myModel;
  private final Object lock = new Object();
  private Splitter myPreviewSplitter;
  private volatile ProgressIndicator associatedProgress; // the progress that current find usages is running under

  // true if usages tree is currently expanding
  // (either at the end of find usages thanks to the 'expand usages after find' setting or
  // because the user pressed 'expand all' button. During this, some ugly hacks applied
  // to speed up the expanding (see getExpandedDescendants() here and UsageViewTreeCellRenderer.customizeCellRenderer())
  private boolean expandingAll;
  private final UsageViewTreeCellRenderer myUsageViewTreeCellRenderer;
  private Usage myOriginUsage;
  @Nullable private Runnable myRerunActivity;
  private boolean myDisposeSmartPointersOnClose = true;
  private final Queue<Future<?>> updateRequests = new Queue<>(10); // guarded by insertionRequests

  public UsageViewImpl(@NotNull final Project project,
                       @NotNull UsageViewPresentation presentation,
                       @NotNull UsageTarget[] targets,
                       Factory<UsageSearcher> usageSearcherFactory) {
    // fire events every 50 ms, not more often to batch requests
    myFireEventsFuture = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(this::fireEvents, 50, 50, TimeUnit.MILLISECONDS);
    Disposer.register(this, ()-> myFireEventsFuture.cancel(false));

    myPresentation = presentation;
    myTargets = targets;
    myUsageSearcherFactory = usageSearcherFactory;
    myProject = project;

    myButtonPanel = new ButtonPanel();

    myModel = new UsageViewTreeModelBuilder(myPresentation, targets);
    myRoot = (GroupNode)myModel.getRoot();

    UsageModelTracker myModelTracker = new UsageModelTracker(project);
    Disposer.register(this, myModelTracker);

    myBuilder = new UsageNodeTreeBuilder(myTargets, getActiveGroupingRules(project, getUsageViewSettings()), getActiveFilteringRules(project), myRoot, myProject);

    final MessageBusConnection messageBusConnection = myProject.getMessageBus().connect(this);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, this::rulesChanged);

    myUsageViewTreeCellRenderer = new UsageViewTreeCellRenderer(this);
    if (!myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(() -> {
        // lock here to avoid concurrent execution of this init and dispose in other thread
        synchronized (lock) {
          if (isDisposed) return;
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
            public boolean isPathEditable(final TreePath path) {
              return path.getLastPathComponent() instanceof UsageViewTreeModelBuilder.TargetsRootNode;
            }

            // hack to avoid quadratic expandAll()
            @Override
            public Enumeration<TreePath> getExpandedDescendants(TreePath parent) {
              return expandingAll ? EmptyEnumeration.getInstance() : super.getExpandedDescendants(parent);
            }
          };
          myTree.setName("UsageViewTree");

          myRootPanel = new MyPanel(myTree);
          Disposer.register(this, myRootPanel);
          myTree.setModel(myModel);

          myRootPanel.setLayout(new BorderLayout());

          SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(false, true);
          myRootPanel.add(toolWindowPanel, BorderLayout.CENTER);

          JPanel toolbarPanel = new JPanel(new BorderLayout());
          toolbarPanel.add(createActionsToolbar(), BorderLayout.WEST);
          toolbarPanel.add(createFiltersToolbar(), BorderLayout.CENTER);
          toolWindowPanel.setToolbar(toolbarPanel);

          myCentralPanel = new JPanel(new BorderLayout());
          setupCentralPanel();

          initTree();
          toolWindowPanel.setContent(myCentralPanel);

          myTree.setCellRenderer(myUsageViewTreeCellRenderer);
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            if (isDisposed || myProject.isDisposed()) return;
            collapseAll();
          });

          myModelTracker.addListener(isPropertyChange-> {
            if (!isPropertyChange) {
              myChangesDetected = true;
            }
            updateLater();
          }, this);

          if (myPresentation.isShowCancelButton()) {
            addButtonToLowerPane(this::close, UsageViewBundle.message("usage.view.cancel.button"));
          }

          myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(final TreeSelectionEvent e) {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(() -> {
                if (isDisposed || myProject.isDisposed()) return;
                updateOnSelectionChanged();
                myNeedUpdateButtons = true;
              });
            }
          });
          myModel.addTreeModelListener(new TreeModelAdapter() {
            @Override
            protected void process(TreeModelEvent event, EventType type) {
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
    myExclusionHandler = new ExclusionHandler<DefaultMutableTreeNode>() {
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
        collectParentNodes(node, nodes, true);
        setExcludeNodes(nodes, true);
      }

      // include the parent if its all children (except the "node" itself) excluded flags are "almostAllChildrenExcluded"
      private void collectParentNodes(DefaultMutableTreeNode node, Set<Node> nodes, boolean almostAllChildrenExcluded) {
        TreeNode parent = node.getParent();
        if (parent == myRoot || !(parent instanceof GroupNode)) return;
        GroupNode parentNode = (GroupNode)parent;
        List<Node> otherNodes =
          parentNode.getChildren().stream().filter(n -> n.isExcluded() != almostAllChildrenExcluded).collect(Collectors.toList());
        if (otherNodes.size() == 1 && otherNodes.get(0) == node) {
          nodes.add(parentNode);
          collectParentNodes(parentNode, nodes, almostAllChildrenExcluded);
        }
      }

      private void setExcludeNodes(@NotNull Set<Node> nodes, boolean excluded) {
        for (Node node : nodes) {
          node.setExcluded(excluded, edtNodeChangedQueue);
        }
        updateImmediatelyNodesUpToRoot(nodes);
      }

      @Override
      public void includeNode(@NotNull DefaultMutableTreeNode node) {
        Set<Node> nodes = new HashSet<>();
        collectAllChildNodes(node, nodes);
        collectParentNodes(node, nodes, false);
        setExcludeNodes(nodes, false);
      }

      @Override
      public boolean isActionEnabled(boolean isExcludeAction) {
        return getPresentation().isExcludeAvailable();
      }

      @Override
      public void onDone(boolean isExcludeAction) {
         if (myRootPanel.hasNextOccurence()) {
           myRootPanel.goNextOccurence();
         }
      }
    };
  }

  @NotNull
  UsageViewSettings getUsageViewSettings() {
    return UsageViewSettings.getInstance();
  }

  // nodes just changed: parent node -> changed child
  // this collection is needed for firing javax.swing.tree.DefaultTreeModel.nodesChanged() events in batch
  // has to be linked because events for child nodes should be fired after events for parent nodes
  private final MultiMap<Node, Node> changedNodesToFire = new LinkedMultiMap<>(); // guarded by changedNodesToFire

  private final Consumer<Node> edtNodeChangedQueue = node -> {
    if (!getPresentation().isDetachedMode()) {
      synchronized (changedNodesToFire) {
        changedNodesToFire.putValue((Node)node.getParent(), node);
      }
    }
  };

  // parent nodes under which the child node was just inserted.
  // it is needed for firing javax.swing.tree.DefaultTreeModel.fireTreeNodesInserted() events in batch.
  // has to be linked because events for child nodes should be fired after events for parent nodes.
  private final Set<Node> nodesInsertedUnder = new LinkedHashSet<>(); // guarded by nodesInsertedUnder

  private final Consumer<Node> edtNodeInsertedUnderQueue = (@NotNull Node parent) -> {
    if (!getPresentation().isDetachedMode()) {
      synchronized (nodesInsertedUnder) {
        nodesInsertedUnder.add(parent);
      }
    }
  };

  // this method is called regularly every 50ms to fire events in batch
  private void fireEvents() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Node> insertedUnder;
    synchronized (nodesInsertedUnder) {
      insertedUnder = new ArrayList<>(nodesInsertedUnder);
      nodesInsertedUnder.clear();
    }
    // for each node synchronize its Swing children (javax.swing.tree.DefaultMutableTreeNode.children)
    // and its model children (com.intellij.usages.impl.GroupNode.getChildren())
    // by issuing corresponding javax.swing.tree.DefaultMutableTreeNode.insert() and then calling javax.swing.tree.DefaultTreeModel.nodesWereInserted()
    TIntArrayList indicesToFire = new TIntArrayList();
    List<Node> nodesToFire = new ArrayList<>();
    for (Node parentNode : insertedUnder) {
      List<Node> swingChildren = ((GroupNode)parentNode).getSwingChildren();
      synchronized (parentNode) {
        List<Node> modelChildren = ((GroupNode)parentNode).getChildren();
        assert modelChildren.size() >= swingChildren.size();

        int k = 0; // index in swingChildren
        for (int i = 0; i < modelChildren.size(); i++) {
          Node modelNode = modelChildren.get(i);
          Node swingNode = k >= swingChildren.size() ? null : swingChildren.get(k);
          if (swingNode == modelNode) {
            k++;
            continue;
          }
          parentNode.insertNewNode(modelNode, i);
          indicesToFire.add(i);
          nodesToFire.add(modelNode);
          if (k==i) k++; // ignore just inserted node
          if (modelNode instanceof UsageNode) {
            Node parent = (Node)modelNode.getParent();
            if (parent instanceof GroupNode) {
              ((GroupNode)parent).incrementUsageCount();
            }
          }
        }
      }

      myModel.fireTreeNodesInserted(parentNode, myModel.getPathToRoot(parentNode), indicesToFire.toNativeArray(), nodesToFire.toArray(new Node[0]));
      nodesToFire.clear();
      indicesToFire.clear();
    }

    // group nodes from changedNodesToFire by their parents and issue corresponding javax.swing.tree.DefaultTreeModel.fireTreeNodesChanged()
    List<Map.Entry<Node, Collection<Node>>> changed;
    synchronized (changedNodesToFire) {
      changed = new ArrayList<>(changedNodesToFire.entrySet());
      changedNodesToFire.clear();
    }
    for (Map.Entry<Node, Collection<Node>> entry : changed) {
      Node parentNode = entry.getKey();
      Set<Node> childrenToUpdate = new THashSet<>(entry.getValue());

      for (int i = 0; i < parentNode.getChildCount(); i++) {
        Node childNode = (Node)parentNode.getChildAt(i);
        if (childrenToUpdate.contains(childNode)) {
          nodesToFire.add(childNode);
          indicesToFire.add(i);
        }
      }

      myModel.fireTreeNodesChanged(parentNode, myModel.getPathToRoot(parentNode), indicesToFire.toNativeArray(), nodesToFire.toArray(new Node[0]));
      nodesToFire.clear();
      indicesToFire.clear();
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
      ProgressWrapper.unwrap(progress).cancel();
    }
  }

  private void clearRendererCache() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (expandingAll) return; // to avoid quadratic row enumeration
    // clear renderer cache of node preferred size
    TreeUI ui = myTree.getUI();
    if (ui instanceof BasicTreeUI) {
      AbstractLayoutCache treeState = ReflectionUtil.getField(BasicTreeUI.class, ui, AbstractLayoutCache.class, "treeState");
      Rectangle visibleRect = myTree.getVisibleRect();
      int rowForLocation = myTree.getClosestRowForLocation(0, visibleRect.y);
      int visibleRowCount = getVisibleRowCount();
      List<Node> toUpdate = new ArrayList<>();
      for (int i = rowForLocation + visibleRowCount + 1; i >= rowForLocation; i--) {
        final TreePath eachPath = myTree.getPathForRow(i);
        if (eachPath == null) continue;

        treeState.invalidatePathBounds(eachPath);
        Object node = eachPath.getLastPathComponent();
        if (node instanceof UsageNode) {
          toUpdate.add((Node)node);
        }
      }
      queueUpdateBulk(toUpdate, ()->{
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    // myTree.getVisibleRowCount returns 20
    return TreeUtil.getVisibleRowCountForFixedRowHeight(myTree);
  }

  private void setupCentralPanel() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myCentralPanel.removeAll();
    disposeUsageContextPanels();

    JScrollPane treePane = ScrollPaneFactory.createScrollPane(myTree);
    // add reaction to scrolling:
    // since the UsageViewTreeCellRenderer ignores invisible nodes (outside the viewport), their preferred size is incorrect
    // and we need to recalculate them when the node scrolled into the visible rectangle
    treePane.getViewport().addChangeListener(__ -> clearRendererCache());
    myPreviewSplitter = new OnePixelSplitter(false, 0.5f, 0.1f, 0.9f);
    myPreviewSplitter.setFirstComponent(treePane);

    myCentralPanel.add(myPreviewSplitter, BorderLayout.CENTER);

    if (isPreviewUsages()) {
      myPreviewSplitter.setProportion(getUsageViewSettings().getPreviewUsagesSplitterProportion());
      final JBTabbedPane tabbedPane = new JBTabbedPane(SwingConstants.BOTTOM){
        @NotNull
        @Override
        protected Insets getInsetsForTabComponent() {
          return new Insets(0,0,0,0);
        }
      };

      UsageContextPanel.Provider[] extensions = Extensions.getExtensions(UsageContextPanel.Provider.EP_NAME, myProject);
      myUsageContextPanelProviders = ContainerUtil.filter(extensions, provider -> provider.isAvailableFor(this));
      Map<String, JComponent> components = new LinkedHashMap<>();
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
      } else {
        for (Map.Entry<String, JComponent> entry : components.entrySet()) {
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
      myPreviewSplitter.setProportion(1);
    }

    myCentralPanel.add(myAdditionalComponent, BorderLayout.SOUTH);
    myAdditionalComponent.add(myButtonPanel, BorderLayout.SOUTH);

    myRootPanel.revalidate();
    myRootPanel.repaint();
  }

  private void tabSelected(@NotNull final UsageContextPanel.Provider provider) {
    myCurrentUsageContextProvider = provider;
    setupCentralPanel();
    updateOnSelectionChanged();
  }

  private void disposeUsageContextPanels() {
    if (myCurrentUsageContextPanel != null) {
      saveSplitterProportions();
      Disposer.dispose(myCurrentUsageContextPanel);
      myCurrentUsageContextPanel = null;
    }
  }

  public boolean isPreviewUsages() {
    return myPresentation.isReplaceMode() ? getUsageViewSettings().isReplacePreviewUsages() : getUsageViewSettings().isPreviewUsages();
  }

  public void setPreviewUsages(boolean state) {
    if (myPresentation.isReplaceMode()) {
      getUsageViewSettings().setReplacePreviewUsages(state);
    } else {
      getUsageViewSettings().setPreviewUsages(state);
    }
  }

  @NotNull
  private static UsageFilteringRule[] getActiveFilteringRules(final Project project) {
    final UsageFilteringRuleProvider[] providers = Extensions.getExtensions(UsageFilteringRuleProvider.EP_NAME);
    List<UsageFilteringRule> list = new ArrayList<>(providers.length);
    for (UsageFilteringRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.getActiveRules(project));
    }
    return list.toArray(UsageFilteringRule.EMPTY_ARRAY);
  }

  @NotNull
  private static UsageGroupingRule[] getActiveGroupingRules(@NotNull final Project project, @NotNull UsageViewSettings usageViewSettings) {
    final UsageGroupingRuleProvider[] providers = Extensions.getExtensions(UsageGroupingRuleProvider.EP_NAME);
    List<UsageGroupingRule> list = new ArrayList<>(providers.length);
    for (UsageGroupingRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.getActiveRules(project, usageViewSettings));
    }

    Collections.sort(list, Comparator.comparingInt(UsageGroupingRule::getRank));
    return list.toArray(UsageGroupingRule.EMPTY_ARRAY);
  }

  private void initTree() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myTree.setShowsRootHandles(true);
    SmartExpander.installOn(myTree);
    TreeUtil.installActions(myTree);
    EditSourceOnDoubleClickHandler.install(myTree);
    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          TreePath leadSelectionPath = myTree.getLeadSelectionPath();
          if (leadSelectionPath == null) return;

          DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent();
          if (node instanceof UsageNode) {
            final Usage usage = ((UsageNode)node).getUsage();
            usage.navigate(false);
            usage.highlightInEditor();
          }
          else if (node.isLeaf()) {
            Navigatable navigatable = getNavigatableForNode(node, !myPresentation.isReplaceMode());
            if (navigatable != null && navigatable.canNavigate()) {
              navigatable.navigate(false);
            }
          }
        }
      }
    });

    TreeUtil.selectFirstNode(myTree);
    PopupHandler.installPopupHandler(myTree, IdeActions.GROUP_USAGE_VIEW_POPUP, ActionPlaces.USAGE_VIEW_POPUP);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        clearRendererCache();

        TreePath path = event.getPath();
        Object component = path.getLastPathComponent();
        if (component instanceof Node) {
          Node node = (Node)component;
          if (!expandingAll && node.needsUpdate()) {
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
    ApplicationManager.getApplication().assertIsDispatchThread();

    DefaultActionGroup group = new DefaultActionGroup() {
      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        myButtonPanel.update();
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };

    AnAction[] actions = createActions();
    for (final AnAction action : actions) {
      if (action != null) {
        group.add(action);
      }
    }
    return toUsageViewToolbar(group);
  }

  @NotNull
  private JComponent toUsageViewToolbar(@NotNull DefaultActionGroup group) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, group, false);
    actionToolbar.setTargetComponent(myRootPanel);
    return actionToolbar.getComponent();
  }

  @SuppressWarnings("WeakerAccess") // used in rider
  protected boolean isPreviewUsageActionEnabled() {
    return true;
  }

  @NotNull
  private JComponent createFiltersToolbar() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DefaultActionGroup group = new DefaultActionGroup();

    final AnAction[] groupingActions = createGroupingActions();
    for (AnAction groupingAction : groupingActions) {
      group.add(groupingAction);
    }

    addFilteringActions(group);
    if (isPreviewUsageActionEnabled()) {
      group.add(new PreviewUsageAction(this));
    }

    group.add(new SortMembersAlphabeticallyAction(this));
    return toUsageViewToolbar(group);
  }

  public void addFilteringActions(@NotNull DefaultActionGroup group) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (getPresentation().isMergeDupLinesAvailable()) {
      final MergeDupLines mergeDupLines = new MergeDupLines();
      final JComponent component = myRootPanel;
      if (component != null) {
        mergeDupLines.registerCustomShortcutSet(mergeDupLines.getShortcutSet(), component, this);
      }
      group.add(mergeDupLines);
    }

    final UsageFilteringRuleProvider[] providers = Extensions.getExtensions(UsageFilteringRuleProvider.EP_NAME);
    for (UsageFilteringRuleProvider provider : providers) {
      AnAction[] actions = provider.createFilteringActions(this);
      for (AnAction action : actions) {
        group.add(action);
      }
    }
  }

  @NotNull
  protected AnAction[] createActions() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final TreeExpander treeExpander = new TreeExpander() {
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

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();

    final JComponent component = getComponent();

    final AnAction expandAllAction = actionsManager.createExpandAllAction(treeExpander, component);
    final AnAction collapseAllAction = actionsManager.createCollapseAllAction(treeExpander, component);

    Disposer.register(this, () -> {
      expandAllAction.unregisterCustomShortcutSet(component);
      collapseAllAction.unregisterCustomShortcutSet(component);
    });


    return new AnAction[] {
      canShowSettings() ? showSettings() : null,
      ActionManager.getInstance().getAction("UsageView.Rerun"),
      ActionManager.getInstance().getAction(IdeActions.ACTION_PIN_ACTIVE_TAB),
      createRecentFindUsagesAction(),
      expandAllAction,
      collapseAllAction,
      actionsManager.createPrevOccurenceAction(myRootPanel),
      actionsManager.createNextOccurenceAction(myRootPanel),
      actionsManager.installAutoscrollToSourceHandler(myProject, myTree, new MyAutoScrollToSourceOptionProvider(getUsageViewSettings())),
      actionsManager.createExportToTextFileAction(myTextFileExporter)
    };
  }

  private boolean canShowSettings() {
    if (myTargets.length == 0) return false;
    NavigationItem target = myTargets[0];
    return target instanceof ConfigurableUsageTarget;
  }

  @NotNull
  private AnAction showSettings() {
    final ConfigurableUsageTarget configurableUsageTarget = getConfigurableTarget(myTargets);
    String description = null;
    try {
      description = configurableUsageTarget == null ? null : "Show settings for "+configurableUsageTarget.getLongDescriptiveName();
    }
    catch (IndexNotReadyException ignored) {
    }
    if (description == null) {
      description = "Show find usages settings dialog";
    }
    return new AnAction("Settings...", description, AllIcons.General.GearPlain) {
      {
        KeyboardShortcut shortcut = configurableUsageTarget == null ? getShowUsagesWithSettingsShortcut() : configurableUsageTarget.getShortcut();
        if (shortcut != null) {
          registerCustomShortcutSet(new CustomShortcutSet(shortcut), getComponent());
        }
      }

      @Override
      public boolean startInTransaction() {
        return true;
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        FindManager.getInstance(getProject()).showSettingsAndFindUsages(myTargets);
      }
    };
  }

  private static ConfigurableUsageTarget getConfigurableTarget(@NotNull UsageTarget[] targets) {
    ConfigurableUsageTarget configurableUsageTarget = null;
    if (targets.length != 0) {
      NavigationItem target = targets[0];
      if (target instanceof ConfigurableUsageTarget) {
        configurableUsageTarget = (ConfigurableUsageTarget)target;
      }
    }
    return configurableUsageTarget;
  }

  @NotNull
  private AnAction createRecentFindUsagesAction() {
    AnAction action = ActionManager.getInstance().getAction(SHOW_RECENT_FIND_USAGES_ACTION_ID);
    action.registerCustomShortcutSet(action.getShortcutSet(), getComponent());
    return action;
  }

  @NotNull
  private AnAction[] createGroupingActions() {
    final UsageGroupingRuleProvider[] providers = Extensions.getExtensions(UsageGroupingRuleProvider.EP_NAME);
    List<AnAction> list = new ArrayList<>(providers.length);
    for (UsageGroupingRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.createGroupingActions(this));
    }
    return list.toArray(AnAction.EMPTY_ARRAY);
  }

  private boolean shouldTreeReactNowToRuleChanges() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myPresentation.isDetachedMode() || myTree.isShowing();
  }

  private boolean rulesChanged;
  private void rulesChanged() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!shouldTreeReactNowToRuleChanges()) {
      rulesChanged = true;
      return;
    }

    final List<UsageState> states = new ArrayList<>();
    if (myTree != null) {
      captureUsagesExpandState(new TreePath(myTree.getModel().getRoot()), states);
    }
    final List<Usage> allUsages = new ArrayList<>(myUsageNodes.keySet());
    Collections.sort(allUsages, USAGE_COMPARATOR);
    final Set<Usage> excludedUsages = getExcludedUsages();
    reset();
    myBuilder.setGroupingRules(getActiveGroupingRules(myProject, getUsageViewSettings()));
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
    //noinspection SSBasedInspection
    appendUsagesInBulk(allUsages).thenRun(()-> SwingUtilities.invokeLater(() -> {
      if (isDisposed) return;
      if (myTree != null) {
        excludeUsages(excludedUsages.toArray(Usage.EMPTY_ARRAY));
        restoreUsageExpandState(states);
        updateImmediately();
      }}));
    if (myCentralPanel != null) {
      setupCentralPanel();
    }
  }

  private void captureUsagesExpandState(@NotNull TreePath pathFrom, @NotNull Collection<UsageState> states) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myTree.isExpanded(pathFrom)) {
      return;
    }
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)pathFrom.getLastPathComponent();
    final int childCount = node.getChildCount();
    for (int idx = 0; idx < childCount; idx++) {
      final TreeNode child = node.getChildAt(idx);
      if (child instanceof UsageNode) {
        final Usage usage = ((UsageNode)child).getUsage();
        states.add(new UsageState(usage, myTree.getSelectionModel().isPathSelected(pathFrom.pathByAddingChild(child))));
      }
      else {
        captureUsagesExpandState(pathFrom.pathByAddingChild(child), states);
      }
    }
  }

  private void restoreUsageExpandState(@NotNull Collection<UsageState> states) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    //always expand the last level group
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    for (int i = root.getChildCount() - 1; i >= 0; i--) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)root.getChildAt(i);
      if (child instanceof GroupNode){
        final TreePath treePath = new TreePath(child.getPath());
        myTree.expandPath(treePath);
      }
    }
    myTree.getSelectionModel().clearSelection();
    for (final UsageState usageState : states) {
      usageState.restore();
    }
  }

  public void expandAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    fireEvents();  // drain all remaining insertion events in the queue

    expandingAll = true;
    try {
      TreeUtil.expandAll(myTree);
    }
    finally {
      expandingAll = false;
    }
    clearRendererCache();
  }

  private void collapseAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    fireEvents();
    TreeUtil.collapseAll(myTree, 3);
    TreeUtil.expand(myTree, 2);
  }

  void expandRoot() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    fireEvents();
    TreeUtil.expand(myTree, 1);
  }

  @NotNull
  DefaultMutableTreeNode getModelRoot() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return (DefaultMutableTreeNode)myTree.getModel().getRoot();
  }

  public void select() {
    // can be null during ctr execution
    //noinspection ConstantConditions
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

  static KeyboardShortcut getShowUsagesWithSettingsShortcut(@NotNull UsageTarget[] targets) {
    ConfigurableUsageTarget configurableTarget = getConfigurableTarget(targets);
    return configurableTarget == null ? getShowUsagesWithSettingsShortcut() : configurableTarget.getShortcut();
  }

  @Override
  public void associateProgress(@NotNull ProgressIndicator indicator) {
    associatedProgress = indicator;
  }

  private class MergeDupLines extends RuleAction {
    private MergeDupLines() {
      super(UsageViewImpl.this, UsageViewBundle.message("action.merge.same.line"), AllIcons.Toolbar.Filterdups);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean getOptionValue() {
      return getUsageViewSettings().isFilterDuplicatedLine();
    }

    @Override
    protected void setOptionValue(boolean value) {
      getUsageViewSettings().setFilterDuplicatedLine(value);
    }
  }

  public void refreshUsages() {
    if (!myPresentation.isOpenInNewTab()) {
      reset();
    }
    doReRun();
  }

  /**
   * @return usage view which will be shown after re-run (either {@code this} if it knows how to re-run itself, or the new created one otherwise)
   */
  @SuppressWarnings("WeakerAccess") // used in rider
  protected UsageView doReRun() {
    myChangesDetected = false;
    if (myRerunActivity == null) {
      return com.intellij.usages.UsageViewManager.getInstance(getProject()).
        searchAndShowUsages(myTargets, myUsageSearcherFactory, true, false, myPresentation, null);
    }
    myRerunActivity.run();
    return this;
  }

  private void reset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myUsageNodes.clear();
    myModel.reset();
    if (!myPresentation.isDetachedMode()) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (isDisposed) return;
        fireEvents();
        TreeUtil.expand(myTree, 2);
      });
    }
  }

  void drainQueuedUsageNodes() {
    assert !ApplicationManager.getApplication().isDispatchThread() : Thread.currentThread();
    UIUtil.invokeAndWaitIfNeeded((Runnable)this::fireEvents);
  }

  @Override
  public void appendUsage(@NotNull Usage usage) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      addUpdateRequest(ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> doAppendUsage(usage))));
    }
    else {
      doAppendUsage(usage);
    }
  }

  private void addUpdateRequest(@NotNull Future<?> request) {
    synchronized (updateRequests) {
      while (!updateRequests.isEmpty() && updateRequests.peekFirst().isDone()) {
        updateRequests.pullFirst();
      }
      updateRequests.addLast(request);
    }
  }

  @Override
  public void waitForUpdateRequestsCompletion() {
    assert !ApplicationManager.getApplication().isDispatchThread();
    while (true) {
      Future<?> request;
      synchronized (updateRequests) {
        request = updateRequests.isEmpty() ? null : updateRequests.pullFirst();
      }
      if (request == null) break;
      try {
        request.get();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @NotNull
  @Override
  public CompletableFuture<?> appendUsagesInBulk(@NotNull Collection<Usage> usages) {
    CompletableFuture<Object> result = new CompletableFuture<>();
    addUpdateRequest(ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
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
    })));
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

    UsageNode child = myBuilder.appendOrGet(usage, edtNodeInsertedUnderQueue, isFilterDuplicateLines());
    myUsageNodes.put(usage, child == null ? NULL_NODE : child);

    for (Node node = child; node != myRoot && node != null; node = (Node)node.getParent()) {
      node.update(this, edtNodeChangedQueue);
    }

    return child;
  }

  @Override
  public void removeUsage(@NotNull Usage usage) {
    removeUsagesBulk(Collections.singleton(usage));
  }

  @Override
  public void removeUsagesBulk(@NotNull Collection<Usage> usages) {
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
        myUsageNodes.keySet().removeIf(usage -> usage instanceof UsageInfo2UsageAdapter && mergedInfos.contains(((UsageInfo2UsageAdapter)usage).getUsageInfo()));
      }
    }

    if (!nodes.isEmpty() && !myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (isDisposed) return;
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
  public void includeUsages(@NotNull Usage[] usages) {
    usagesToNodes(Arrays.stream(usages))
      .forEach(myExclusionHandler::includeNode);
  }

  @Override
  public void excludeUsages(@NotNull Usage[] usages) {
    usagesToNodes(Arrays.stream(usages))
      .forEach(myExclusionHandler::excludeNode);
  }

  private Stream<UsageNode> usagesToNodes(Stream<Usage> usages) {
    return usages
      .map(myUsageNodes::get)
      .filter(node -> node != NULL_NODE && node != null);
  }

  @Override
  public void selectUsages(@NotNull Usage[] usages) {
    TreePath[] paths = usagesToNodes(Arrays.stream(usages))
      .map(node -> new TreePath(node.getPath()))
      .toArray(TreePath[]::new);

    myTree.setSelectionPaths(paths);
    if (paths.length != 0) myTree.scrollPathToVisible(paths[0]);
  }

  @NotNull
  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTree != null ? myTree : getComponent();
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myRootPanel == null ? new JLabel() : myRootPanel;
  }

  @Override
  public int getUsagesCount() {
    return myUsageNodes.size();
  }

  void setContent(@NotNull Content content) {
    myContent = content;
    content.setDisposer(this);
  }

  private void updateImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) return;
    TreeNode root = (TreeNode)myTree.getModel().getRoot();
    List<Node> toUpdate = new ArrayList<>();
    checkNodeValidity(root, new TreePath(root), toUpdate);
    queueUpdateBulk(toUpdate, EmptyRunnable.getInstance());
    updateOnSelectionChanged();
  }

  private void queueUpdateBulk(@NotNull List<Node> toUpdate, @NotNull Runnable onCompletedInEdt) {
    if (toUpdate.isEmpty()) return;
    addUpdateRequest(ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (Node node : toUpdate) {
        try {
          if (isDisposed()) break;
          ReadAction.run(() -> node.update(this, edtNodeChangedQueue));
        }
        catch (IndexNotReadyException ignore) {
        }
      }
      ApplicationManager.getApplication().invokeLater(onCompletedInEdt);
    }));
  }

  private void updateImmediatelyNodesUpToRoot(@NotNull Collection<Node> nodes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) return;
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myCurrentUsageContextPanel != null) {
      try {
        myCurrentUsageContextPanel.updateLayout(getSelectedUsageInfos());
      }
      catch (IndexNotReadyException ignore) {
      }
    }
  }

  private void checkNodeValidity(@NotNull TreeNode node, @NotNull TreePath path, @NotNull List<Node> result) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
      for (int i=0; i < node.getChildCount(); i++) {
        TreeNode child = node.getChildAt(i);
        checkNodeValidity(child, path.pathByAddingChild(child), result);
      }
    }

    // call update last, to let children a chance to update their cache first
    if (node instanceof Node && node != getModelRoot() && isVisible == UsageViewTreeCellRenderer.RowLocation.INSIDE_VISIBLE_RECT) {
      result.add((Node)node);
    }
  }

  private void updateLater() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> {
      if (myProject.isDisposed()) return;
      PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
      documentManager.cancelAndRunWhenAllCommitted("UpdateUsageView", this::updateImmediately);
    }, 300);
  }

  @Override
  public void close() {
    cancelCurrentSearch();
    if (myContent != null) {
      UsageViewManager.getInstance(myProject).closeContent(myContent);
    }
  }

  private void saveSplitterProportions() {
    getUsageViewSettings().setPreviewUsagesSplitterProportion(myPreviewSplitter.getProportion());
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    disposeUsageContextPanels();
    synchronized (lock) {
      isDisposed = true;
      if (myTree != null) {
        ToolTipManager.sharedInstance().unregisterComponent(myTree);
      }
      myUpdateAlarm.cancelAllRequests();
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
      SmartPointerManager pointerManager = SmartPointerManager.getInstance(getProject());
      for (SmartPsiElementPointer<?> pointer : smartPointers) {
        pointerManager.removePointer(pointer);
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
        if (isDisposed) return;
        final UsageNode firstUsageNode = myModel.getFirstUsageNode();
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
    return isDisposed;
  }

  private void showNode(@NotNull final UsageNode node) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!isDisposed && !myPresentation.isDetachedMode()) {
      fireEvents();
      TreePath usagePath = new TreePath(node.getPath());
      myTree.expandPath(usagePath.getParentPath());
      TreeUtil.selectPath(myTree, usagePath);
    }
  }

  @Override
  public void setReRunActivity(@NotNull Runnable runnable) {
    myRerunActivity = runnable;
  }

  @Override
  public void addButtonToLowerPane(@NotNull Action action) {
    int index = myButtonPanel.getComponentCount();
    if (!SystemInfo.isMac && index > 0 && myPresentation.isShowCancelButton()) index--;
    myButtonPanel.addButtonAction(index, action);
    Object o = action.getValue(Action.ACCELERATOR_KEY);
    if (o instanceof KeyStroke) {
      myTree.registerKeyboardAction(action, (KeyStroke)o, JComponent.WHEN_FOCUSED);
    }
  }

  @Override
  public void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull String text) {
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
  public void addButtonToLowerPane(@NotNull final Runnable runnable, @NotNull String text, char mnemonic) {
    // implemented method is deprecated, so, it just calls non-deprecated overloading one
    addButtonToLowerPane(runnable, text);
  }

  @Override
  public void addPerformOperationAction(@NotNull final Runnable processRunnable,
                                        @NotNull final String commandName,
                                        final String cannotMakeString,
                                        @NotNull String shortDescription) {
    addPerformOperationAction(processRunnable, commandName, cannotMakeString, shortDescription, true);
  }

  @Override
  public void addPerformOperationAction(@NotNull Runnable processRunnable,
                                        @NotNull String commandName,
                                        String cannotMakeString,
                                        @NotNull String shortDescription,
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
    try {
      return myUsageSearcherFactory != null && allTargetsAreValid() && myUsageSearcherFactory.create() != null;
    }
    catch (PsiInvalidElementAccessException e) {
      return false;
    }
  }

  private boolean checkReadonlyUsages() {
    final Set<VirtualFile> readOnlyUsages = getReadOnlyUsagesFiles();

    return readOnlyUsages.isEmpty() ||
           !ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(VfsUtilCore.toVirtualFileArray(readOnlyUsages)).hasReadonlyFiles();
  }

  @NotNull
  private Set<Usage> getReadOnlyUsages() {
    final Set<Usage> result = new THashSet<>();
    final Set<Map.Entry<Usage,UsageNode>> usages = myUsageNodes.entrySet();
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    if (leadSelectionPath == null) return null;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent();
    return node instanceof Node ? (Node)node : null;
  }

  @Nullable
  private Node[] getSelectedNodes() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    TreePath[] leadSelectionPath = myTree.getSelectionPaths();
    if (leadSelectionPath == null || leadSelectionPath.length == 0) return null;

    final List<Node> result = new ArrayList<>();
    for (TreePath comp : leadSelectionPath) {
      final Object lastPathComponent = comp.getLastPathComponent();
      if (lastPathComponent instanceof Node) {
        final Node node = (Node)lastPathComponent;
        result.add(node);
      }
    }
    return result.isEmpty() ? null : result.toArray(new Node[0]);
  }

  @Override
  @NotNull
  public Set<Usage> getSelectedUsages() {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    Collections.sort(usages, USAGE_COMPARATOR);
    return usages;
  }

  private static void collectUsages(@NotNull DefaultMutableTreeNode node, @NotNull Set<Usage> usages) {
    if (node instanceof UsageNode) {
      UsageNode usageNode = (UsageNode)node;
      final Usage usage = usageNode.getUsage();
      usages.add(usage);
    }

    Enumeration enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)enumeration.nextElement();
      collectUsages(child, usages);
    }
  }

  private static void collectAllChildNodes(@NotNull DefaultMutableTreeNode node, @NotNull Set<Node> nodes) {
    if (node instanceof Node) {
      nodes.add((Node)node);
    }

    Enumeration enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)enumeration.nextElement();
      collectAllChildNodes(child, nodes);
    }
  }

  @Nullable
  private UsageTarget[] getSelectedUsageTargets() {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
      final Navigatable navigatable = (Navigatable)userObject;
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
    final List<Navigatable> result = new ArrayList<>();
    for (final Node node : nodes) {
      /*
      if (!node.isDataValid()) {
        continue;
      }
      */
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

  private class MyPanel extends JPanel implements TypeSafeDataProvider, OccurenceNavigator, Disposable{
    @Nullable private OccurenceNavigatorSupport mySupport;
    private final CopyProvider myCopyProvider;

    private MyPanel(@NotNull JTree tree) {
      mySupport = new OccurenceNavigatorSupport(tree) {
        @Override
        protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
          if (node.getChildCount() > 0) return null;
          if (node instanceof Node && ((Node)node).isExcluded()) return null;
          return getNavigatableForNode(node, !myPresentation.isReplaceMode());
        }

        @Override
        public String getNextOccurenceActionName() {
          return UsageViewBundle.message("action.next.occurrence");
        }

        @Override
        public String getPreviousOccurenceActionName() {
          return UsageViewBundle.message("action.previous.occurrence");
        }
      };
      myCopyProvider = new TextCopyProvider() {
        @Nullable
        @Override
        public Collection<String> getTextLinesToCopy() {
          final Node[] selectedNodes = getSelectedNodes();
          if (selectedNodes != null && selectedNodes.length > 0) {
            ArrayList<String> lines = ContainerUtil.newArrayList();
            for (Node node : selectedNodes) {
              lines.add(node.getText(UsageViewImpl.this));
            }
            return lines;
          }
          return null;
        }
      };
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

    @Override
    public String getNextOccurenceActionName() {
      return mySupport != null ? mySupport.getNextOccurenceActionName() : "";
    }

    @Override
    public String getPreviousOccurenceActionName() {
      return mySupport != null ? mySupport.getPreviousOccurenceActionName() : "";
    }

    @Override
    public void calcData(final DataKey key, final DataSink sink) {
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
        sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatablesForNodes(getSelectedNodes()));
      }

      else if (key == PlatformDataKeys.EXPORTER_TO_TEXT_FILE) {
        sink.put(PlatformDataKeys.EXPORTER_TO_TEXT_FILE, myTextFileExporter);
      }

      else if (key == USAGES_KEY) {
        final Set<Usage> selectedUsages = getSelectedUsages();
        sink.put(USAGES_KEY, selectedUsages.toArray(Usage.EMPTY_ARRAY));
      }

      else if (key == USAGE_TARGETS_KEY) {
        sink.put(USAGE_TARGETS_KEY, getSelectedUsageTargets());
      }

      else if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
        final Set<Usage> usages = ApplicationManager.getApplication().isDispatchThread() ? getSelectedUsages() : null;
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
        sink.put(LangDataKeys.PSI_ELEMENT_ARRAY, getSelectedUsages()
          .stream()
          .filter(u -> u instanceof PsiElementUsage)
          .map(u -> ((PsiElementUsage)u).getElement())
          .filter(Objects::nonNull)
          .toArray(PsiElement.ARRAY_FACTORY::create));
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

  private static class MyAutoScrollToSourceOptionProvider implements AutoScrollToSourceOptionProvider {
    @NotNull private final UsageViewSettings myUsageViewSettings;

    MyAutoScrollToSourceOptionProvider(@NotNull UsageViewSettings usageViewSettings) {
      myUsageViewSettings = usageViewSettings;
    }

    @Override
    public boolean isAutoScrollMode() {
      return myUsageViewSettings.isAutoScrollToSource();
    }

    @Override
    public void setAutoScrollMode(boolean state) {
      myUsageViewSettings.setAutoScrollToSource(state);
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
      button.setFocusable(false);
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
          final JButton button = (JButton)component;
          Action action = button.getAction();
          if (action != null) {
            if (myNeedUpdateButtons) {
              button.setEnabled(globallyEnabled && action.isEnabled());
            }
            Object name = action.getValue(Action.NAME);
            if (name instanceof String) {
              DialogUtil.setTextWithMnemonic(button, (String)name);
            }
          } else {
            button.setEnabled(globallyEnabled);
          }
        }
      }
      myNeedUpdateButtons = false;
    }
  }

  private class UsageState {
    private final Usage myUsage;
    private final boolean mySelected;

    private UsageState(@NotNull Usage usage, boolean isSelected) {
      myUsage = usage;
      mySelected = isSelected;
    }

    private void restore() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      final UsageNode node = myUsageNodes.get(myUsage);
      if (node == NULL_NODE || node == null) {
        return;
      }
      final DefaultMutableTreeNode parentGroupingNode = (DefaultMutableTreeNode)node.getParent();
      if (parentGroupingNode != null) {
        final TreePath treePath = new TreePath(parentGroupingNode.getPath());
        myTree.expandPath(treePath);
        if (mySelected) {
          myTree.addSelectionPath(treePath.pathByAddingChild(node));
        }
      }
    }
  }

  private class MyPerformOperationRunnable implements Runnable {
    private final String myCannotMakeString;
    private final Runnable myProcessRunnable;
    private final String myCommandName;
    private final boolean myCheckReadOnlyStatus;

    private MyPerformOperationRunnable(@NotNull Runnable processRunnable, @NotNull String commandName, final String cannotMakeString,
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
          String[] options = {UsageViewBundle.message("action.description.rerun"), UsageViewBundle.message("usage.view.cancel.button")};
          String message = myCannotMakeString + "\n\n" + UsageViewBundle.message("dialog.rerun.search");
          int answer = Messages.showOkCancelDialog(myProject, message, title, options[0], options[1], Messages.getErrorIcon());
          if (answer == Messages.OK) {
            refreshUsages();
          }
        }
        else {
          Messages.showMessageDialog(myProject, myCannotMakeString, title, Messages.getErrorIcon());
          //todo[myakovlev] request focus to tree
          //myUsageView.getTree().requestFocus();
        }
        return;
      }

      // can't dispose pointers because refactoring might want to re-use the usage infos from the preview
      myDisposeSmartPointersOnClose = false;
      close();

      try {
        CommandProcessor.getInstance().executeCommand(
          myProject, myProcessRunnable,
          myCommandName,
          null
        );
      }
      finally {
        disposeSmartPointers();
      }
    }
  }

  private List<UsageInfo> getSelectedUsageInfos() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return USAGE_INFO_LIST_KEY.getData(DataManager.getInstance().getDataContext(myRootPanel));
  }

  public GroupNode getRoot() {
    return myRoot;
  }

  @TestOnly
  public String getNodeText(@NotNull TreeNode node) {
    return myUsageViewTreeCellRenderer.getPlainTextForNode(node);
  }

  public boolean isVisible(@NotNull Usage usage) {
    return myBuilder != null && myBuilder.isVisible(usage);
  }

  @NotNull
  public UsageTarget[] getTargets() {
    return myTargets;
  }

  /**
   * The element the "find usages" action was invoked on.
   * E.g. if the "find usages" was invoked on the reference "getName(2)" pointing to the method "getName()" then the origin usage is this reference.
   */
  public void setOriginUsage(@NotNull Usage usage) {
    myOriginUsage = usage;
  }

  /** true if the {@param usage} points to the element the "find usages" action was invoked on */
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    UsageNode usageNode = myUsageNodes.get(toDelete);
    if (usageNode == null) return null;

    DefaultMutableTreeNode node = myRootPanel.mySupport.findNode(myTree, usageNode, true, null);
    if (node == null) node = myRootPanel.mySupport.findNode(myTree, usageNode, false, null); // last node

    return node == null ? null : node.getUserObject() instanceof Usage ? (Usage)node.getUserObject() : null;
  }

  public Usage getNextToSelect(@NotNull Collection<Usage> toDelete) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Usage toSelect = null;
    for (Usage usage : toDelete) {
      Usage next = getNextToSelect(usage);
      if (!toDelete.contains(next)) {
        toSelect = next;
        break;
      }
    }
    return toSelect;
  }
}
