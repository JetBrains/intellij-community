/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getFiles;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray;
import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;

/**
 * @author yole
 */
public class CommittedChangesTreeBrowser extends JPanel implements TypeSafeDataProvider, Disposable, DecoratorManager {
  private static final Border RIGHT_BORDER = IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT);

  private final Project myProject;
  @NotNull private final ChangesBrowserTree myChangesTree;
  private final MyRepositoryChangesViewer myDetailsView;
  private List<CommittedChangeList> myChangeLists;
  private List<CommittedChangeList> mySelectedChangeLists;
  @NotNull private ChangeListGroupingStrategy myGroupingStrategy = new DateChangeListGroupingStrategy();
  private final CompositeChangeListFilteringStrategy myFilteringStrategy = new CompositeChangeListFilteringStrategy();
  private final JPanel myLeftPanel;
  private final FilterChangeListener myFilterChangeListener = new FilterChangeListener();
  private final SplitterProportionsData mySplitterProportionsData = new SplitterProportionsDataImpl();
  private final CopyProvider myCopyProvider;
  private final TreeExpander myTreeExpander;
  private String myHelpId;

  public static final Topic<CommittedChangesReloadListener> ITEMS_RELOADED =
    new Topic<>("ITEMS_RELOADED", CommittedChangesReloadListener.class);

  private final List<CommittedChangeListDecorator> myDecorators;

  @NonNls public static final String ourHelpId = "reference.changesToolWindow.incoming";

  private WiseSplitter myInnerSplitter;
  private final MessageBusConnection myConnection;
  private TreeState myState;

  public CommittedChangesTreeBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(new BorderLayout());

    myProject = project;
    myDecorators = new LinkedList<>();
    myChangeLists = new ArrayList<>(changeLists);
    myChangesTree = new ChangesBrowserTree();
    myChangesTree.setRootVisible(false);
    myChangesTree.setShowsRootHandles(true);
    myChangesTree.setCellRenderer(new CommittedChangeListRenderer(project, myDecorators));
    TreeUtil.expandAll(myChangesTree);
    myChangesTree.setExpandableItemsEnabled(false);

    myDetailsView = new MyRepositoryChangesViewer(project);
    myDetailsView.getViewerScrollPane().setBorder(RIGHT_BORDER);

    myChangesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateBySelectionChange();
      }
    });

    myChangesTree.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        myChangesTree.invalidateNodeSizes();
      }
    });

    final TreeLinkMouseListener linkMouseListener = new TreeLinkMouseListener(new CommittedChangeListRenderer(project, myDecorators));
    linkMouseListener.installOn(myChangesTree);

    myLeftPanel = new JPanel(new BorderLayout());

    initSplitters();

    updateBySelectionChange();

    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    CustomShortcutSet quickdocShortcuts = new CustomShortcutSet(keymap.getShortcuts(IdeActions.ACTION_QUICK_JAVADOC));
    EmptyAction.registerWithShortcutSet("CommittedChanges.Details", quickdocShortcuts, this);

    myCopyProvider = new TreeCopyProvider(myChangesTree);
    myTreeExpander = new DefaultTreeExpander(myChangesTree);
    myDetailsView.addToolbarAction(ActionManager.getInstance().getAction("Vcs.ShowTabbedFileHistory"));

    myHelpId = ourHelpId;

    myDetailsView.getDiffAction().registerCustomShortcutSet(myDetailsView.getDiffAction().getShortcutSet(), myChangesTree);

    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ITEMS_RELOADED, new CommittedChangesReloadListener() {
      public void itemsReloaded() {
      }
      public void emptyRefresh() {
        updateGrouping();
      }
    });
  }

  private void initSplitters() {
    final Splitter filterSplitter = new Splitter(false, 0.5f);

    filterSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myChangesTree));
    myLeftPanel.add(filterSplitter, BorderLayout.CENTER);
    final Splitter mainSplitter = new Splitter(false, 0.7f);
    mainSplitter.setFirstComponent(myLeftPanel);
    mainSplitter.setSecondComponent(myDetailsView);

    add(mainSplitter, BorderLayout.CENTER);

    myInnerSplitter = new WiseSplitter(() -> {
      filterSplitter.doLayout();
      updateModel();
    }, filterSplitter);
    Disposer.register(this, myInnerSplitter);

    mySplitterProportionsData.externalizeFromDimensionService("CommittedChanges.SplitterProportions");
    mySplitterProportionsData.restoreSplitterProportions(this);
  }

  public void addFilter(final ChangeListFilteringStrategy strategy) {
    myFilteringStrategy.addStrategy(strategy.getKey(), strategy);
    strategy.addChangeListener(myFilterChangeListener);
  }

  private void updateGrouping() {
    if (myGroupingStrategy.changedSinceApply()) {
      ApplicationManager.getApplication().invokeLater(() -> updateModel(), ModalityState.NON_MODAL);
    }
  }

  private TreeModel buildTreeModel(final List<CommittedChangeList> filteredChangeLists) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    Collections.sort(filteredChangeLists, myGroupingStrategy.getComparator());
    myGroupingStrategy.beforeStart();
    DefaultMutableTreeNode lastGroupNode = null;
    String lastGroupName = null;
    for(CommittedChangeList list: filteredChangeLists) {
      String groupName = StringUtil.notNullize(myGroupingStrategy.getGroupName(list));
      if (!Comparing.equal(groupName, lastGroupName)) {
        lastGroupName = groupName;
        lastGroupNode = new DefaultMutableTreeNode(lastGroupName);
        root.add(lastGroupNode);
      }
      assert lastGroupNode != null;
      lastGroupNode.add(new DefaultMutableTreeNode(list));
    }
    return model;
  }

  public void setHelpId(final String helpId) {
    myHelpId = helpId;
  }

  public StatusText getEmptyText() {
    return myChangesTree.getEmptyText();
  }

  public void setToolBar(JComponent toolBar) {
    myLeftPanel.add(toolBar, BorderLayout.NORTH);
    myDetailsView.syncSizeWithToolbar(toolBar);
  }

  public void dispose() {
    myConnection.disconnect();
    mySplitterProportionsData.saveSplitterProportions(this);
    mySplitterProportionsData.externalizeToDimensionService("CommittedChanges.SplitterProportions");
  }

  public void setItems(@NotNull List<CommittedChangeList> items, final CommittedChangesBrowserUseCase useCase) {
    myDetailsView.setUseCase(useCase);
    myChangeLists = new ArrayList<>(items);
    myFilteringStrategy.setFilterBase(items);
    BackgroundTaskUtil.syncPublisher(myProject, ITEMS_RELOADED).itemsReloaded();
    updateModel();
  }

  private void updateModel() {
    TreeState state = TreeState.createOn(myChangesTree, (DefaultMutableTreeNode)myChangesTree.getModel().getRoot());
    final List<CommittedChangeList> filteredChangeLists = myFilteringStrategy.filterChangeLists(myChangeLists);
    myChangesTree.setModel(buildTreeModel(filteredChangeLists));
    state.applyTo(myChangesTree);
    TreeUtil.expandAll(myChangesTree);
  }

  public void setGroupingStrategy(@NotNull ChangeListGroupingStrategy strategy) {
    myGroupingStrategy = strategy;
    updateModel();
  }

  @NotNull
  public ChangeListGroupingStrategy getGroupingStrategy() {
    return myGroupingStrategy;
  }

  private void updateBySelectionChange() {
    List<CommittedChangeList> selection = new ArrayList<>();
    final TreePath[] selectionPaths = myChangesTree.getSelectionPaths();
    if (selectionPaths != null) {
      for(TreePath path: selectionPaths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof CommittedChangeList) {
          selection.add((CommittedChangeList) node.getUserObject());
        }
      }
    }

    if (!selection.equals(mySelectedChangeLists)) {
      mySelectedChangeLists = selection;
      myDetailsView.setChangesToDisplay(collectChanges(mySelectedChangeLists, false));
    }
  }

  @NotNull
  public static List<Change> collectChanges(final List<? extends CommittedChangeList> selectedChangeLists, final boolean withMovedTrees) {
    Collections.sort(selectedChangeLists, CommittedChangeListByDateComparator.ASCENDING);

    List<Change> changes = new ArrayList<>();
    for (CommittedChangeList cl : selectedChangeLists) {
      changes.addAll(withMovedTrees ? cl.getChangesWithMovedTrees() : cl.getChanges());
    }
    return zipChanges(changes);
  }

  /**
   * Zips changes by removing duplicates (changes in the same file) and compounding the diff.
   * <b>NB:</b> changes must be given in the time-ascending order, i.e the first change in the list should be the oldest one.
   */
  @NotNull
  public static List<Change> zipChanges(@NotNull List<Change> changes) {
    // TODO: further improvements needed
    // We may want to process collisions more consistent

    // Possible solution: avoid creating duplicate entries for the same FilePath. No changes in the output should have same beforePath or afterPath.
    // We may take earliest and latest revisions for each file.
    //
    // The main problem would be to keep existing movements in non-conflicting cases (where input changes are taken from linear sequence of commits)
    // case1: "a -> b; b -> c" - file renamed twice in the same revision (as source and as target)
    // case2: "a -> b" "b -> c" - file renamed twice in consequent commits
    // case3: "a -> b; b -> a" - files swapped vs "a -> b" "b -> a" - file rename canceled
    // case4: "delete a" "b -> a" "modify a"
    // ...
    // but return "good enough" result for input with conflicting changes
    // case1: "new a", "new a"
    // case2: "a -> b", "new b"
    // ...
    //
    // getting "actually good" results is impossible without knowledge of commits topology.


    // key - after path (nullable)
    LinkedMultiMap<FilePath, Change> map = new LinkedMultiMap<>();

    for (Change change : changes) {
      ContentRevision bRev = change.getBeforeRevision();
      ContentRevision aRev = change.getAfterRevision();
      FilePath bPath = bRev != null ? bRev.getFile() : null;
      FilePath aPath = aRev != null ? aRev.getFile() : null;

      if (bRev == null) {
        map.putValue(aPath, change);
        continue;
      }

      Collection<Change> bucket = map.get(bPath);
      if (bucket.isEmpty()) {
        map.putValue(aPath, change);
        continue;
      }

      Change oldChange = bucket.iterator().next();
      bucket.remove(oldChange);

      ContentRevision oldRevision = oldChange.getBeforeRevision();
      if (oldRevision != null || aRev != null) {
        map.putValue(aPath, new Change(oldRevision, aRev));
      }
    }

    // put deletions into appropriate place in list
    Collection<Change> deleted = map.remove(null);
    if (deleted != null) {
      for (Change change : deleted) {
        //noinspection ConstantConditions
        map.putValue(change.getBeforeRevision().getFile(), change);
      }
    }

    return new ArrayList<>(map.values());
  }

  private List<CommittedChangeList> getSelectedChangeLists() {
    return TreeUtil.collectSelectedObjectsOfType(myChangesTree, CommittedChangeList.class);
  }

  public void setTableContextMenu(final ActionGroup group, final List<AnAction> auxiliaryActions) {
    DefaultActionGroup menuGroup = new DefaultActionGroup();
    menuGroup.add(group);
    for (AnAction action : auxiliaryActions) {
      menuGroup.add(action);
    }
    menuGroup.add(ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER));
    PopupHandler.installPopupHandler(myChangesTree, menuGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  public void removeFilteringStrategy(final CommittedChangesFilterKey key) {
    final ChangeListFilteringStrategy strategy = myFilteringStrategy.removeStrategy(key);
    if (strategy != null) {
      strategy.removeChangeListener(myFilterChangeListener);
    }
    myInnerSplitter.remove(key);
  }

  public boolean setFilteringStrategy(final ChangeListFilteringStrategy filteringStrategy) {
    if (myInnerSplitter.canAdd()) {
      filteringStrategy.addChangeListener(myFilterChangeListener);

      final CommittedChangesFilterKey key = filteringStrategy.getKey();
      myFilteringStrategy.addStrategy(key, filteringStrategy);
      myFilteringStrategy.setFilterBase(myChangeLists);

      final JComponent filterUI = filteringStrategy.getFilterUI();
      if (filterUI != null) {
        myInnerSplitter.add(key, filterUI);
      }
      return true;
    }
    return false;
  }

  public ActionToolbar createGroupFilterToolbar(final Project project, final ActionGroup leadGroup, @Nullable final ActionGroup tailGroup,
                                                final List<AnAction> extra) {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(leadGroup);
    toolbarGroup.addSeparator();
    toolbarGroup.add(new SelectFilteringAction(project, this));
    toolbarGroup.add(new SelectGroupingAction(project, this));
    final ExpandAllAction expandAllAction = new ExpandAllAction(myChangesTree);
    final CollapseAllAction collapseAllAction = new CollapseAllAction(myChangesTree);
    expandAllAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_EXPAND_ALL), myChangesTree);
    collapseAllAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_COLLAPSE_ALL), myChangesTree);
    toolbarGroup.add(expandAllAction);
    toolbarGroup.add(collapseAllAction);
    toolbarGroup.add(ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER));
    toolbarGroup.add(new ContextHelpAction(myHelpId));
    if (tailGroup != null) {
      toolbarGroup.add(tailGroup);
    }
    for (AnAction anAction : extra) {
      toolbarGroup.add(anAction);
    }
    return ActionManager.getInstance().createActionToolbar("CommittedChangesTree", toolbarGroup, true);
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(VcsDataKeys.CHANGES)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), false);
      sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
    } else if (key.equals(VcsDataKeys.HAVE_SELECTED_CHANGES)) {
      final int count = myChangesTree.getSelectionCount();
      sink.put(VcsDataKeys.HAVE_SELECTED_CHANGES, count > 0);
    }
    else if (key.equals(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), true);
      sink.put(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN, changes.toArray(new Change[changes.size()]));
    }
    else if (key.equals(VcsDataKeys.CHANGE_LISTS)) {
      final List<CommittedChangeList> lists = getSelectedChangeLists();
      if (!lists.isEmpty()) {
        sink.put(VcsDataKeys.CHANGE_LISTS, lists.toArray(new CommittedChangeList[lists.size()]));
      }
    }
    else if (key.equals(CommonDataKeys.NAVIGATABLE_ARRAY)) {
      Collection<Change> changes = collectChanges(getSelectedChangeLists(), false);
      sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatableArray(myProject, getFiles(changes.stream())));
    }
    else if (key.equals(PlatformDataKeys.HELP_ID)) {
      sink.put(PlatformDataKeys.HELP_ID, myHelpId);
    } else if (VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.equals(key)) {
      final List<Change> selectedChanges = myDetailsView.getSelectedChanges();
      sink.put(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS, selectedChanges.toArray(new Change[selectedChanges.size()]));
    }
  }

  public TreeExpander getTreeExpander() {
    return myTreeExpander;
  }

  public void repaintTree() {
    myChangesTree.revalidate();
    myChangesTree.repaint();
  }

  public void install(final CommittedChangeListDecorator decorator) {
    myDecorators.add(decorator);
    repaintTree();
  }

  public void remove(final CommittedChangeListDecorator decorator) {
    myDecorators.remove(decorator);
    repaintTree();
  }

  public void reportLoadedLists(final CommittedChangeListsListener listener) {
    List<CommittedChangeList> lists = new ArrayList<>(myChangeLists);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      listener.onBeforeStartReport();
      for (CommittedChangeList list : lists) {
        listener.report(list);
      }
      listener.onAfterEndReport();
    });
  }

  // for appendable view
  public void reset() {
    myChangeLists.clear();
    myFilteringStrategy.resetFilterBase();

    myState = TreeState.createOn(myChangesTree, (DefaultMutableTreeNode)myChangesTree.getModel().getRoot());
    updateModel();
  }

  public void append(final List<CommittedChangeList> list) {
    final TreeState state = myChangeLists.isEmpty() && myState != null ? myState :
      TreeState.createOn(myChangesTree, (DefaultMutableTreeNode)myChangesTree.getModel().getRoot());
    state.setScrollToSelection(false);
    myChangeLists.addAll(list);

    myFilteringStrategy.appendFilterBase(list);

    myChangesTree.setModel(buildTreeModel(myFilteringStrategy.filterChangeLists(myChangeLists)));
    state.applyTo(myChangesTree, myChangesTree.getModel().getRoot());
    TreeUtil.expandAll(myChangesTree);
    BackgroundTaskUtil.syncPublisher(myProject, ITEMS_RELOADED).itemsReloaded();
  }

  public static class MoreLauncher implements Runnable {
    private final Project myProject;
    private final CommittedChangeList myList;

    MoreLauncher(final Project project, final CommittedChangeList list) {
      myProject = project;
      myList = list;
    }

    public void run() {
      ChangeListDetailsAction.showDetailsPopup(myProject, myList);
    }
  }

  private class FilterChangeListener implements ChangeListener {
    public void stateChanged(ChangeEvent e) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        updateModel();
      } else {
        ApplicationManager.getApplication().invokeLater(() -> updateModel());
      }
    }
  }

  private class ChangesBrowserTree extends Tree implements TypeSafeDataProvider {
    public ChangesBrowserTree() {
      super(buildTreeModel(myFilteringStrategy.filterChangeLists(myChangeLists)));
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (key.equals(PlatformDataKeys.COPY_PROVIDER)) {
        sink.put(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
      }
      else if (key.equals(PlatformDataKeys.TREE_EXPANDER)) {
        sink.put(PlatformDataKeys.TREE_EXPANDER, myTreeExpander);
      } else {
        final String name = key.getName();
        if (VcsDataKeys.SELECTED_CHANGES.is(name) || VcsDataKeys.CHANGE_LEAD_SELECTION.is(name) ||
            CommittedChangesBrowserUseCase.DATA_KEY.is(name)) {
          final Object data = myDetailsView.getData(name);
          if (data != null) {
            sink.put(key, data);
          }
        }
      }
    }

    public void invalidateNodeSizes() {
      TreeUI ui = getUI();

      if (ui instanceof WideSelectionTreeUI) {
        ((WideSelectionTreeUI)ui).invalidateNodeSizes();
      }

      repaint();
    }
  }

  public interface CommittedChangesReloadListener {
    void itemsReloaded();
    void emptyRefresh();
  }

  public void setLoading(final boolean value) {
    runOrInvokeLaterAboveProgress(() -> myChangesTree.setPaintBusy(value), ModalityState.NON_MODAL, myProject);
  }

  private static class MyRepositoryChangesViewer extends CommittedChangesBrowser {
    private final JComponent myHeaderPanel = new JPanel();

    public MyRepositoryChangesViewer(Project project) {
      super(project);
    }

    @Nullable
    @Override
    protected JComponent createHeaderPanel() {
      return myHeaderPanel;
    }

    public void syncSizeWithToolbar(@NotNull JComponent toolbar) {
      myHeaderPanel.setPreferredSize(new Dimension(0, toolbar.getPreferredSize().height));
    }
  }
}
