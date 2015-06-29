/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.*;
import com.intellij.usages.rules.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.enumeration.EmptyEnumeration;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author max
 */
public class UsageViewImpl implements UsageView, UsageModelTracker.UsageModelTrackerListener {
  @NonNls public static final String SHOW_RECENT_FIND_USAGES_ACTION_ID = "UsageView.ShowRecentFindUsages";

  private final UsageNodeTreeBuilder myBuilder;
  private final MyPanel myRootPanel;
  @NotNull
  private final JTree myTree;
  private Content myContent;

  private final UsageViewPresentation myPresentation;
  private final UsageTarget[] myTargets;
  private final Factory<UsageSearcher> myUsageSearcherFactory;
  private final Project myProject;

  private volatile boolean mySearchInProgress = true;
  private final ExporterToTextFile myTextFileExporter = new ExporterToTextFile(this);
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private final UsageModelTracker myModelTracker;
  private final Map<Usage, UsageNode> myUsageNodes = new ConcurrentHashMap<Usage, UsageNode>();
  public static final UsageNode NULL_NODE = new UsageNode(NullUsage.INSTANCE, new UsageViewTreeModelBuilder(new UsageViewPresentation(), UsageTarget.EMPTY_ARRAY));
  private final ButtonPanel myButtonPanel = new ButtonPanel();
  private volatile boolean isDisposed;
  private volatile boolean myChangesDetected = false;
  public static final Comparator<Usage> USAGE_COMPARATOR = new Comparator<Usage>() {
    @Override
    public int compare(final Usage o1, final Usage o2) {
      if (o1 == o2) return 0;
      if (o1 == NULL_NODE) return -1;
      if (o2 == NULL_NODE) return 1;
      if (o1 instanceof Comparable && o2 instanceof Comparable) {
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
    }
  };
  @NonNls private static final String HELP_ID = "ideaInterface.find";
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

  UsageViewImpl(@NotNull final Project project,
                @NotNull UsageViewPresentation presentation,
                @NotNull UsageTarget[] targets,
                Factory<UsageSearcher> usageSearcherFactory) {
    myPresentation = presentation;
    myTargets = targets;
    myUsageSearcherFactory = usageSearcherFactory;
    myProject = project;
    myTree = new Tree() {
      {
        ToolTipManager.sharedInstance().registerComponent(this);
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
        return expandingAll ? EmptyEnumeration.<TreePath>getInstance() : super.getExpandedDescendants(parent);
      }
    };
    myRootPanel = new MyPanel(myTree);
    Disposer.register(this, myRootPanel);
    myModelTracker = new UsageModelTracker(project);
    Disposer.register(this, myModelTracker);

    myModel = new UsageViewTreeModelBuilder(myPresentation, targets);
    myRoot = (GroupNode)myModel.getRoot();
    myBuilder = new UsageNodeTreeBuilder(myTargets, getActiveGroupingRules(project), getActiveFilteringRules(project), myRoot, myProject);

    final MessageBusConnection messageBusConnection = myProject.getMessageBus().connect(this);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, new Runnable() {
      @Override
      public void run() {
        rulesChanged();
      }
    });

    myUsageViewTreeCellRenderer = new UsageViewTreeCellRenderer(this);
    if (!myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          // lock here to avoid concurrent execution of this init and dispose in other thread
          synchronized (lock) {
            if (isDisposed) return;
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
            collapseAll();

            myModelTracker.addListener(UsageViewImpl.this);

            if (myPresentation.isShowCancelButton()) {
              addButtonToLowerPane(new Runnable() {
                @Override
                public void run() {
                  close();
                }
              }, UsageViewBundle.message("usage.view.cancel.button"));
            }

            myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
              @Override
              public void valueChanged(final TreeSelectionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    if (isDisposed || myProject.isDisposed()) return;
                    updateOnSelectionChanged();
                  }
                });
              }
            });
          }
        }
      });
    }
    myTransferToEDTQueue = new TransferToEDTQueue<Runnable>("Insert usages", new Processor<Runnable>() {
      @Override
      public boolean process(Runnable runnable) {
        runnable.run();
        return true;
      }
    }, new Condition<Object>() {
      @Override
      public boolean value(Object o) {
        return isDisposed || project.isDisposed();
      }
    },200);
  }

  protected boolean searchHasBeenCancelled() {
    ProgressIndicator progress = associatedProgress;
    return progress != null && progress.isCanceled();
  }

  protected void cancelCurrentSearch() {
    ProgressIndicator progress = associatedProgress;
    if (progress != null) {
      ProgressWrapper.unwrap(progress).cancel();
    }
  }

  private void clearRendererCache() {
    // clear renderer cache of node preferred size
    TreeUI ui = myTree.getUI();
    if (ui instanceof BasicTreeUI) {
      AbstractLayoutCache treeState = ReflectionUtil.getField(BasicTreeUI.class, ui, AbstractLayoutCache.class, "treeState");
      Rectangle visibleRect = myTree.getVisibleRect();
      int rowForLocation = myTree.getClosestRowForLocation(0, visibleRect.y);
      int visibleRowCount = getVisibleRowCount();
      for (int i = rowForLocation + visibleRowCount + 1; i >= rowForLocation; i--) {
        final TreePath eachPath = myTree.getPathForRow(i);
        if (eachPath == null) continue;

        treeState.invalidatePathBounds(eachPath);
      }
      myTree.repaint(visibleRect);
    }
    else {
      myTree.setCellRenderer(myUsageViewTreeCellRenderer);
    }
  }

  private int getVisibleRowCount() {
    // myTree.getVisibleRowCount returns 20
    return TreeUtil.getVisibleRowCountForFixedRowHeight(myTree);
  }

  private void setupCentralPanel() {
    myCentralPanel.removeAll();
    disposeUsageContextPanels();

    JScrollPane treePane = ScrollPaneFactory.createScrollPane(myTree);
    // add reaction to scrolling:
    // since the UsageViewTreeCellRenderer ignores invisible nodes (outside the viewport), their preferred size is incorrect
    // and we need to recalculate them when the node scrolled into the visible rectangle
    treePane.getViewport().addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        clearRendererCache();
      }
    });
    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        clearRendererCache();
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        clearRendererCache();
      }
    });
    myPreviewSplitter = new Splitter(false, 0.5f, 0.1f, 0.9f);
    myPreviewSplitter.setFirstComponent(treePane);

    myCentralPanel.add(myPreviewSplitter, BorderLayout.CENTER);

    if (UsageViewSettings.getInstance().IS_PREVIEW_USAGES) {
      myPreviewSplitter.setProportion(UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS);
      treePane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.RIGHT);
      final JBTabbedPane tabbedPane = new JBTabbedPane(SwingConstants.BOTTOM){
        @NotNull
        @Override
        protected Insets getInsetsForTabComponent() {
          return new Insets(0,0,0,0);
        }
      };

      UsageContextPanel.Provider[] extensions = Extensions.getExtensions(UsageContextPanel.Provider.EP_NAME, myProject);
      myUsageContextPanelProviders = ContainerUtil.filter(extensions, new Condition<UsageContextPanel.Provider>() {
        @Override
        public boolean value(UsageContextPanel.Provider provider) {
          return provider.isAvailableFor(UsageViewImpl.this);
        }
      });
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

        tabbedPane.addTab(provider.getTabTitle(), component);
      }
      int index = myUsageContextPanelProviders.indexOf(myCurrentUsageContextProvider);
      tabbedPane.setSelectedIndex(index);
      tabbedPane.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          int currentIndex = tabbedPane.getSelectedIndex();
          UsageContextPanel.Provider selectedProvider = myUsageContextPanelProviders.get(currentIndex);
          if (selectedProvider != myCurrentUsageContextProvider) {
            tabSelected(selectedProvider);
          }
        }
      });
      tabbedPane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
      myPreviewSplitter.setSecondComponent(tabbedPane);
    }
    else {
      myPreviewSplitter.setProportion(1);
    }

    myCentralPanel.add(myButtonPanel, BorderLayout.SOUTH);

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

  private static UsageFilteringRule[] getActiveFilteringRules(final Project project) {
    final UsageFilteringRuleProvider[] providers = Extensions.getExtensions(UsageFilteringRuleProvider.EP_NAME);
    List<UsageFilteringRule> list = new ArrayList<UsageFilteringRule>(providers.length);
    for (UsageFilteringRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.getActiveRules(project));
    }
    return list.toArray(new UsageFilteringRule[list.size()]);
  }

  private static UsageGroupingRule[] getActiveGroupingRules(@NotNull final Project project) {
    final UsageGroupingRuleProvider[] providers = Extensions.getExtensions(UsageGroupingRuleProvider.EP_NAME);
    List<UsageGroupingRule> list = new ArrayList<UsageGroupingRule>(providers.length);
    for (UsageGroupingRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.getActiveRules(project));
    }

    Collections.sort(list, new Comparator<UsageGroupingRule>() {
      @Override
      public int compare(final UsageGroupingRule o1, final UsageGroupingRule o2) {
        return getRank(o1) - getRank(o2);
      }

      private int getRank(final UsageGroupingRule rule) {
        if (rule instanceof OrderableUsageGroupingRule) {
          return ((OrderableUsageGroupingRule)rule).getRank();
        }

        return Integer.MAX_VALUE;
      }
    });

    return list.toArray(new UsageGroupingRule[list.size()]);
  }

  @Override
  public void modelChanged(boolean isPropertyChange) {
    if (!isPropertyChange) {
      myChangesDetected = true;
    }
    updateLater();
  }

  private void initTree() {
    myTree.setRootVisible(false);
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
            Navigatable navigatable = getNavigatableForNode(node);
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
        TreePath path = event.getPath();
        Object component = path.getLastPathComponent();
        if (!(component instanceof Node)) return;
        Node node = (Node)component;
        if (!expandingAll && node.needsUpdate()) {
          checkNodeValidity(node, path);
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath o) {
        Object value = o.getLastPathComponent();
        TreeCellRenderer renderer = myTree.getCellRenderer();
        if (renderer instanceof UsageViewTreeCellRenderer) {
          UsageViewTreeCellRenderer coloredRenderer = (UsageViewTreeCellRenderer)renderer;
          return coloredRenderer.getPlainTextForNode(value);
        }
        return value == null ? null : value.toString();
      }
    }, true);
  }

  @NotNull
  private JComponent createActionsToolbar() {
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
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, group, false);
    actionToolbar.setTargetComponent(myRootPanel);
    return actionToolbar.getComponent();
  }

  @NotNull
  private JComponent createFiltersToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();

    final AnAction[] groupingActions = createGroupingActions();
    for (AnAction groupingAction : groupingActions) {
      group.add(groupingAction);
    }

    addFilteringActions(group);
    group.add(new PreviewUsageAction(this));

    group.add(new SortMembersAlphabeticallyAction(this));
    return toUsageViewToolbar(group);
  }

  public void addFilteringActions(@NotNull DefaultActionGroup group) {
    final JComponent component = getComponent();

    if (getPresentation().isMergeDupLinesAvailable()) {
      final MergeDupLines mergeDupLines = new MergeDupLines();
      mergeDupLines.registerCustomShortcutSet(mergeDupLines.getShortcutSet(), component, this);
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

  public void scheduleDisposeOnClose(@NotNull Disposable disposable) {
    Disposer.register(this, disposable);
  }

  @NotNull
  private AnAction[] createActions() {
    final TreeExpander treeExpander = new TreeExpander() {
      @Override
      public void expandAll() {
        UsageViewImpl.this.expandAll();
        UsageViewSettings.getInstance().setExpanded(true);
      }

      @Override
      public boolean canExpand() {
        return true;
      }

      @Override
      public void collapseAll() {
        UsageViewImpl.this.collapseAll();
        UsageViewSettings.getInstance().setExpanded(false);
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

    scheduleDisposeOnClose(new Disposable() {
      @Override
      public void dispose() {
        expandAllAction.unregisterCustomShortcutSet(component);
        collapseAllAction.unregisterCustomShortcutSet(component);
      }
    });


    return new AnAction[] {
      canShowSettings() ? showSettings() : null,
      ActionManager.getInstance().getAction("UsageView.Rerun"),
      new CloseAction(),
      ActionManager.getInstance().getAction("PinToolwindowTab"),
      createRecentFindUsagesAction(),
      expandAllAction,
      collapseAllAction,
      actionsManager.createPrevOccurenceAction(myRootPanel),
      actionsManager.createNextOccurenceAction(myRootPanel),
      actionsManager.installAutoscrollToSourceHandler(myProject, myTree, new MyAutoScrollToSourceOptionProvider()),
      actionsManager.createExportToTextFileAction(myTextFileExporter),
      actionsManager.createHelpAction(HELP_ID)
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
    String description = configurableUsageTarget == null ? "Show find usages settings dialog" : "Show settings for "+configurableUsageTarget.getLongDescriptiveName();
    return new AnAction("Settings...", description, AllIcons.General.ProjectSettings) {
      {
        KeyboardShortcut shortcut = configurableUsageTarget == null ? getShowUsagesWithSettingsShortcut() : configurableUsageTarget.getShortcut();
        if (shortcut != null) {
          registerCustomShortcutSet(new CustomShortcutSet(shortcut), getComponent());
        }
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
    List<AnAction> list = new ArrayList<AnAction>(providers.length);
    for (UsageGroupingRuleProvider provider : providers) {
      ContainerUtil.addAll(list, provider.createGroupingActions(this));
    }
    return list.toArray(new AnAction[list.size()]);
  }

  private void rulesChanged() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final List<UsageState> states = new ArrayList<UsageState>();
    captureUsagesExpandState(new TreePath(myTree.getModel().getRoot()), states);
    final List<Usage> allUsages = new ArrayList<Usage>(myUsageNodes.keySet());
    Collections.sort(allUsages, USAGE_COMPARATOR);
    final Set<Usage> excludedUsages = getExcludedUsages();
    reset();
    myBuilder.setGroupingRules(getActiveGroupingRules(myProject));
    myBuilder.setFilteringRules(getActiveFilteringRules(myProject));
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (Usage usage : allUsages) {
          if (!usage.isValid()) {
            continue;
          }
          if (usage instanceof MergeableUsage) {
            ((MergeableUsage)usage).reset();
          }
          appendUsage(usage);
        }
      }
    });
    excludeUsages(excludedUsages.toArray(new Usage[excludedUsages.size()]));
    if (myCentralPanel != null) {
      setupCentralPanel();
    }
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (isDisposed) return;
        restoreUsageExpandState(states);
        updateImmediately();
      }
    });
  }

  private void captureUsagesExpandState(TreePath pathFrom, final Collection<UsageState> states) {
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

  private void expandAll() {
    expandingAll = true;
    try {
      TreeUtil.expandAll(myTree);
    }
    finally {
      expandingAll = false;
    }
  }

  private void collapseAll() {
    TreeUtil.collapseAll(myTree, 3);
    TreeUtil.expand(myTree, 2);
  }

  public DefaultMutableTreeNode getModelRoot() {
    return (DefaultMutableTreeNode)myTree.getModel().getRoot();
  }

  public void select() {
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

  void associateProgress(@NotNull ProgressIndicator indicator) {
    associatedProgress = indicator;
  }

  private class CloseAction extends CloseTabToolbarAction {
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myContent != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      close();
    }
  }

  private class MergeDupLines extends RuleAction {
    private MergeDupLines() {
      super(UsageViewImpl.this, UsageViewBundle.message("action.merge.same.line"), AllIcons.Toolbar.Filterdups);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean getOptionValue() {
      return UsageViewSettings.getInstance().isFilterDuplicatedLine();
    }

    @Override
    protected void setOptionValue(boolean value) {
      UsageViewSettings.getInstance().setFilterDuplicatedLine(value);
    }
  }

  public void refreshUsages() {
    reset();
    doReRun();
  }

  private void doReRun() {
    final AtomicInteger usageCountWithoutDefinition = new AtomicInteger(0);
    final Project project = myProject;
    Task.Backgroundable task = new Task.Backgroundable(project, UsageViewManagerImpl.getProgressTitle(myPresentation)) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        final TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.createFor(indicator);
        setSearchInProgress(true);
        associateProgress(indicator);

        myChangesDetected = false;
        UsageSearcher usageSearcher = myUsageSearcherFactory.create();
        usageSearcher.generate(new Processor<Usage>() {
          @Override
          public boolean process(final Usage usage) {
            if (searchHasBeenCancelled()) return false;
            TooManyUsagesStatus.getFrom(indicator).pauseProcessingIfTooManyUsages();

            boolean incrementCounter = !com.intellij.usages.UsageViewManager.isSelfUsage(usage, myTargets);

            if (incrementCounter) {
              final int usageCount = usageCountWithoutDefinition.incrementAndGet();
              if (usageCount > UsageLimitUtil.USAGES_LIMIT) {
                if (tooManyUsagesStatus.switchTooManyUsagesStatus()) {
                  UsageViewManagerImpl
                    .showTooManyUsagesWarning(project, tooManyUsagesStatus, indicator, getPresentation(), usageCountWithoutDefinition.get(), UsageViewImpl.this);
                }
              }
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                  appendUsage(usage);
                }
              });
            }
            return !indicator.isCanceled();
          }
        });
        drainQueuedUsageNodes();
        setSearchInProgress(false);
      }
    };
    ProgressManager.getInstance().run(task);
  }

  private void reset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myUsageNodes.clear();
    myModel.reset();
    if (!myPresentation.isDetachedMode()) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (isDisposed) return;
          TreeUtil.expand(myTree, 2);
        }
      });
    }
  }

  private final TransferToEDTQueue<Runnable> myTransferToEDTQueue;
  public void drainQueuedUsageNodes() {
    assert !ApplicationManager.getApplication().isDispatchThread() : Thread.currentThread();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        myTransferToEDTQueue.drain();
      }
    });
  }

  @Override
  public void appendUsage(@NotNull Usage usage) {
    doAppendUsage(usage);
  }

  @Nullable
  public UsageNode doAppendUsage(@NotNull Usage usage) {
    // invoke in ReadAction to be be sure that usages are not invalidated while the tree is being built
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!usage.isValid()) {
      // because the view is built incrementally, the usage may be already invalid, so need to filter such cases
      return null;
    }
    UsageNode node = myBuilder.appendUsage(usage, new Consumer<Runnable>() {
      @Override
      public void consume(Runnable runnable) {
        myTransferToEDTQueue.offer(runnable);
      }
    });
    if (node != null) {
      // update and cache flags while the node is still hot
      node.update(this);
    }
    myUsageNodes.put(usage, node == null ? NULL_NODE : node);
    return node;
  }

  @Override
  public void removeUsage(@NotNull Usage usage) {
    final UsageNode node = myUsageNodes.remove(usage);
    if (node != NULL_NODE && node != null && !myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (isDisposed) return;
          TreeModel treeModel = myTree.getModel();
          ((DefaultTreeModel)treeModel).removeNodeFromParent(node);
          ((GroupNode)myTree.getModel().getRoot()).removeUsage(node);
        }
      });
    }
  }

  @Override
  public void removeUsagesBulk(@NotNull Collection<Usage> usages) {
    final Set<UsageNode> nodes = new THashSet<UsageNode>(usages.size());
    for (Usage usage : usages) {
      UsageNode node = myUsageNodes.remove(usage);
      if (node != null && node != NULL_NODE) {
        nodes.add(node);
      }
    }
    if (!nodes.isEmpty() && !myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (isDisposed) return;
          DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
          for (UsageNode node : nodes) {
            MutableTreeNode parent = (MutableTreeNode)node.getParent();
            int childIndex = parent.getIndex(node);
            if (childIndex != -1) {
              parent.remove(childIndex);
            }
          }
          ((GroupNode)myTree.getModel().getRoot()).removeUsagesBulk(nodes);

          treeModel.reload();
        }
      });
    }
  }

  @Override
  public void includeUsages(@NotNull Usage[] usages) {
    List<TreeNode> nodes = new ArrayList<TreeNode>(usages.length);
    for (Usage usage : usages) {
      final UsageNode node = myUsageNodes.get(usage);
      if (node != NULL_NODE && node != null) {
        node.setUsageExcluded(false);
        nodes.add(node);
      }
    }
    updateImmediatelyNodesUpToRoot(nodes);
  }

  @Override
  public void excludeUsages(@NotNull Usage[] usages) {
    List<TreeNode> nodes = new ArrayList<TreeNode>(usages.length);
    for (Usage usage : usages) {
      final UsageNode node = myUsageNodes.get(usage);
      if (node != NULL_NODE && node != null) {
        node.setUsageExcluded(true);
        nodes.add(node);
      }
    }
    updateImmediatelyNodesUpToRoot(nodes);
  }

  @Override
  public void selectUsages(@NotNull Usage[] usages) {
    List<TreePath> paths = new LinkedList<TreePath>();

    for (Usage usage : usages) {
      final UsageNode node = myUsageNodes.get(usage);

      if (node != NULL_NODE && node != null) {
        paths.add(new TreePath(node.getPath()));
      }
    }

    myTree.setSelectionPaths(paths.toArray(new TreePath[paths.size()]));
    if (!paths.isEmpty()) myTree.scrollPathToVisible(paths.get(0));
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myRootPanel;
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
    if (myProject.isDisposed()) return;
    TreeNode root = (TreeNode)myTree.getModel().getRoot();
    checkNodeValidity(root, new TreePath(root));
    updateOnSelectionChanged();
  }

  private void updateImmediatelyNodesUpToRoot(@NotNull List<TreeNode> nodes) {
    if (myProject.isDisposed()) return;
    TreeNode root = (TreeNode)myTree.getModel().getRoot();

    for (int i=0; i<nodes.size(); i++) {
      TreeNode node = nodes.get(i);
      if (node instanceof Node) {
        ((Node)node).update(this);
        TreeNode parent = node.getParent();
        if (parent != root && parent != null) {
          nodes.add(parent);
        }
      }
    }
    updateImmediately();
  }


  private void updateOnSelectionChanged() {
    if (myCurrentUsageContextPanel != null) {
      try {
        myCurrentUsageContextPanel.updateLayout(getSelectedUsageInfos());
      }
      catch (IndexNotReadyException ignore) {
      }
    }
  }

  private void checkNodeValidity(@NotNull TreeNode node, @NotNull TreePath path) {
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
        checkNodeValidity(child, path.pathByAddingChild(child));
      }
    }

    // call update last, to let children a chance to update their cache first
    if (node instanceof Node && node != getModelRoot() && isVisible == UsageViewTreeCellRenderer.RowLocation.INSIDE_VISIBLE_RECT) {
      try {
        ((Node)node).update(this);
      }
      catch (IndexNotReadyException ignore) {
      }
    }
  }

  private void updateLater() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) return;
        PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
        documentManager.cancelAndRunWhenAllCommitted("UpdateUsageView", new Runnable() {
          @Override
          public void run() {
            updateImmediately();
          }
        });
      }
    }, 300);
  }

  @Override
  public void close() {
    cancelCurrentSearch();
    UsageViewManager.getInstance(myProject).closeContent(myContent);
  }

  private void saveSplitterProportions() {
    UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS = myPreviewSplitter.getProportion();
  }

  @Override
  public void dispose() {
    disposeUsageContextPanels();
    synchronized (lock) {
      isDisposed = true;
      ToolTipManager.sharedInstance().unregisterComponent(myTree);
      myModelTracker.removeListener(this);
      myUpdateAlarm.cancelAllRequests();
    }
    disposeSmartPointers();
  }

  private void disposeSmartPointers() {
    SmartPointerManager pointerManager = SmartPointerManager.getInstance(getProject());
    for (Usage usage : myUsageNodes.keySet()) {
      if (usage instanceof UsageInfo2UsageAdapter) {
        SmartPsiElementPointer<?> pointer = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getSmartPointer();
        pointerManager.removePointer(pointer);
      }
    }
  }

  @Override
  public boolean isSearchInProgress() {
    return mySearchInProgress;
  }

  public void setSearchInProgress(boolean searchInProgress) {
    mySearchInProgress = searchInProgress;
    if (!myPresentation.isDetachedMode()) {
      myTransferToEDTQueue.offer(new Runnable() {
        @Override
        public void run() {
          if (isDisposed) return;
          final UsageNode firstUsageNode = myModel.getFirstUsageNode();
          if (firstUsageNode == null) return;

          Node node = getSelectedNode();
          if (node != null && !Comparing.equal(new TreePath(node.getPath()), TreeUtil.getFirstNodePath(myTree))) {
            // user has selected node already
            return;
          }
          showNode(firstUsageNode);
          if (UsageViewSettings.getInstance().isExpanded() && myUsageNodes.size() < 10000) {
            expandAll();
          }
        }
      });
    }
  }

  public boolean isDisposed() {
    return isDisposed;
  }

  private void showNode(@NotNull final UsageNode node) {
    if (!myPresentation.isDetachedMode()) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (isDisposed) return;
          TreePath usagePath = new TreePath(node.getPath());
          myTree.expandPath(usagePath.getParentPath());
          TreeUtil.selectPath(myTree, usagePath);
        }
      });
    }
  }

  @Override
  public void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull String text) {
    int index = myButtonPanel.getComponentCount();
    if (!SystemInfo.isMac && index > 0 && myPresentation.isShowCancelButton()) index--;
    myButtonPanel.addButtonRunnable(index, runnable, text);
  }

  @Override
  public void addButtonToLowerPane(@NotNull final Runnable runnable, @NotNull String text, char mnemonic) {
    // implemented method is deprecated, so, it just calls non-deprecated overloading one
    addButtonToLowerPane(runnable, text);
  }

  @Override
  public void addPerformOperationAction(@NotNull final Runnable processRunnable,
                                        final String commandName,
                                        final String cannotMakeString,
                                        @NotNull String shortDescription) {
    addPerformOperationAction(processRunnable, commandName, cannotMakeString, shortDescription, true);
  }

  @Override
  public void addPerformOperationAction(@NotNull Runnable processRunnable,
                                        String commandName,
                                        String cannotMakeString,
                                        @NotNull String shortDescription,
                                        boolean checkReadOnlyStatus) {
    addButtonToLowerPane(newPerformOperationRunnable(processRunnable, commandName, cannotMakeString, checkReadOnlyStatus), shortDescription);
  }

  public MyPerformOperationRunnable newPerformOperationRunnable(Runnable processRunnable,
                                                                String commandName,
                                                                String cannotMakeString, boolean checkReadOnlyStatus) {
    return new MyPerformOperationRunnable(cannotMakeString, processRunnable, commandName, checkReadOnlyStatus);
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
    return myUsageSearcherFactory != null && allTargetsAreValid();
  }

  private boolean checkReadonlyUsages() {
    final Set<VirtualFile> readOnlyUsages = getReadOnlyUsagesFiles();

    return readOnlyUsages.isEmpty() ||
           !ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(VfsUtilCore.toVirtualFileArray(readOnlyUsages)).hasReadonlyFiles();
  }

  @NotNull
  private Set<Usage> getReadOnlyUsages() {
    final Set<Usage> result = new THashSet<Usage>();
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
    Set<VirtualFile> result = new THashSet<VirtualFile>();
    for (Usage usage : usages) {
      if (usage instanceof UsageInFile) {
        UsageInFile usageInFile = (UsageInFile)usage;
        VirtualFile file = usageInFile.getFile();
        if (file != null) result.add(file);
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
    Set<Usage> result = new THashSet<Usage>();
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
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    if (leadSelectionPath == null) return null;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent();
    return node instanceof Node ? (Node)node : null;
  }

  @Nullable
  private Node[] getSelectedNodes() {
    TreePath[] leadSelectionPath = myTree.getSelectionPaths();
    if (leadSelectionPath == null || leadSelectionPath.length == 0) return null;

    final List<Node> result = new ArrayList<Node>();
    for (TreePath comp : leadSelectionPath) {
      final Object lastPathComponent = comp.getLastPathComponent();
      if (lastPathComponent instanceof Node) {
        final Node node = (Node)lastPathComponent;
        result.add(node);
      }
    }
    return result.isEmpty() ? null : result.toArray(new Node[result.size()]);
  }

  @Override
  @Nullable
  public Set<Usage> getSelectedUsages() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) {
      return null;
    }

    Set<Usage> usages = new THashSet<Usage>();
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
    List<Usage> usages = new ArrayList<Usage>(getUsages());
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

  @Nullable
  private UsageTarget[] getSelectedUsageTargets() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) return null;

    Set<UsageTarget> targets = new THashSet<UsageTarget>();
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

    return targets.isEmpty() ? null : targets.toArray(new UsageTarget[targets.size()]);
  }

  @Nullable
  private static Navigatable getNavigatableForNode(@NotNull DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (userObject instanceof Navigatable) {
      final Navigatable navigatable = (Navigatable)userObject;
      return navigatable.canNavigate() ? navigatable : null;
    }
    return null;
  }

  /* nodes with non-valid data are not included */
  private static Navigatable[] getNavigatablesForNodes(Node[] nodes) {
    if (nodes == null) {
      return null;
    }
    final List<Navigatable> result = new ArrayList<Navigatable>();
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
    return result.toArray(new Navigatable[result.size()]);
  }

  public boolean areTargetsValid() {
    return myModel.areTargetsValid();
  }

  private class MyPanel extends JPanel implements TypeSafeDataProvider, OccurenceNavigator,Disposable, CopyProvider {
    @Nullable private OccurenceNavigatorSupport mySupport;

    private MyPanel(@NotNull JTree tree) {
      mySupport = new OccurenceNavigatorSupport(tree) {
        @Override
        protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
          if (node.getChildCount() > 0) return null;
          if (node instanceof Node && ((Node)node).isExcluded()) return null;
          return getNavigatableForNode(node);
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
    public void performCopy(@NotNull DataContext dataContext) {
      final Node selectedNode = getSelectedNode();
      assert selectedNode != null;
      final String plainText = selectedNode.getText(UsageViewImpl.this);
      CopyPasteManager.getInstance().setContents(new StringSelection(plainText.trim()));
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return getSelectedNode() != null;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public void calcData(final DataKey key, final DataSink sink) {
      Node node = getSelectedNode();

      if (key == CommonDataKeys.PROJECT) {
        sink.put(CommonDataKeys.PROJECT, myProject);
      }
      else if (key == USAGE_VIEW_KEY) {
        sink.put(USAGE_VIEW_KEY, UsageViewImpl.this);
      }

      else if (key == CommonDataKeys.NAVIGATABLE_ARRAY) {
        sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatablesForNodes(getSelectedNodes()));
      }

      else if (key == PlatformDataKeys.EXPORTER_TO_TEXT_FILE) {
        sink.put(PlatformDataKeys.EXPORTER_TO_TEXT_FILE, myTextFileExporter);
      }

      else if (key == USAGES_KEY) {
        final Set<Usage> selectedUsages = getSelectedUsages();
        sink.put(USAGES_KEY, selectedUsages != null ? selectedUsages.toArray(new Usage[selectedUsages.size()]) : null);
      }

      else if (key == USAGE_TARGETS_KEY) {
        sink.put(USAGE_TARGETS_KEY, getSelectedUsageTargets());
      }

      else if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
        final Set<Usage> usages = getSelectedUsages();
        Usage[] ua = usages != null ? usages.toArray(new Usage[usages.size()]) : null;
        VirtualFile[] data = UsageDataUtil.provideVirtualFileArray(ua, getSelectedUsageTargets());
        sink.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, data);
      }

      else if (key == PlatformDataKeys.HELP_ID) {
        sink.put(PlatformDataKeys.HELP_ID, HELP_ID);
      }
      else if (key == PlatformDataKeys.COPY_PROVIDER) {
        sink.put(PlatformDataKeys.COPY_PROVIDER, this);
      }
      else if (node != null) {
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

  private static class MyAutoScrollToSourceOptionProvider implements AutoScrollToSourceOptionProvider {
    @Override
    public boolean isAutoScrollMode() {
      return UsageViewSettings.getInstance().IS_AUTOSCROLL_TO_SOURCE;
    }

    @Override
    public void setAutoScrollMode(boolean state) {
      UsageViewSettings.getInstance().IS_AUTOSCROLL_TO_SOURCE = state;
    }
  }

  private final class ButtonPanel extends JPanel {
    private ButtonPanel() {
      setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
    }

    private void addButtonRunnable(int index, final Runnable runnable, String text) {
      if (getBorder() == null) setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));

      final JButton button = new JButton(UIUtil.replaceMnemonicAmpersand(text));
      DialogUtil.registerMnemonic(button);

      button.setFocusable(false);
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          runnable.run();
        }
      });

      add(button, index);

      invalidate();
      if (getParent() != null) {
        getParent().validate();
      }
    }

    void update() {
      for (int i = 0; i < getComponentCount(); ++i) {
        Component component = getComponent(i);
        if (component instanceof JButton) {
          final JButton button = (JButton)component;
          button.setEnabled(!isSearchInProgress());
        }
      }
    }
  }

  private class UsageState {
    private final Usage myUsage;
    private final boolean mySelected;

    private UsageState(@NotNull Usage usage, boolean isSelected) {
      myUsage = usage;
      mySelected = isSelected;
    }

    public void restore() {
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

    private MyPerformOperationRunnable(final String cannotMakeString,
                                       final Runnable processRunnable,
                                       final String commandName,
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

      close();

      CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
          @Override
          public void run() {
            myProcessRunnable.run();
          }
        },
        myCommandName,
        null
      );
    }
  }

  private List<UsageInfo> getSelectedUsageInfos() {
    return USAGE_INFO_LIST_KEY.getData(DataManager.getInstance().getDataContext(myRootPanel));
  }

  public GroupNode getRoot() {
    return myRoot;
  }

  public boolean isVisible(@NotNull Usage usage) {
    return myBuilder != null && myBuilder.isVisible(usage);
  }

  @NotNull
  public UsageTarget[] getTargets() {
    return myTargets;
  }
}
