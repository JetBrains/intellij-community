// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider.PatchesPreloader;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import kotlinx.coroutines.CoroutineScope;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.Predicates.nonNull;
import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.SHELVE_DELETION_UNDO;
import static com.intellij.openapi.vcs.changes.ChangesViewManager.isEditorPreview;
import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.REPOSITORY_GROUPING;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.SHELF;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.getToolWindowFor;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerKt.isCommitToolWindowShown;
import static com.intellij.util.FontUtil.spaceAndThinSpace;
import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

public class ShelvedChangesViewManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(ShelvedChangesViewManager.class);
  private static final String HELP_ID = "ideaInterface.shelf";
  static final @NonNls String SHELF_CONTEXT_MENU = "Vcs.Shelf.ContextMenu";
  private static final String SHELVE_PREVIEW_SPLITTER_PROPORTION = "ShelvedChangesViewManager.DETAILS_SPLITTER_PROPORTION"; //NON-NLS

  static final @NonNls String SHELVED_CHANGES_TOOLBAR = "ShelvedChangesToolbar";

  private final Project myProject;
  private final MergingUpdateQueue myUpdateQueue;
  private final List<Runnable> myPostUpdateEdtActivity = new ArrayList<>();

  private @Nullable ShelfToolWindowPanel myPanel = null;

  public static final DataKey<ChangesTree> SHELVED_CHANGES_TREE =
    DataKey.create("ShelveChangesManager.ShelvedChangesTree");
  public static final DataKey<List<ShelvedChangeList>> SHELVED_CHANGELIST_KEY =
    DataKey.create("ShelveChangesManager.ShelvedChangeListData");
  public static final DataKey<List<ShelvedChangeList>> SHELVED_RECYCLED_CHANGELIST_KEY =
    DataKey.create("ShelveChangesManager.ShelvedRecycledChangeListData");
  public static final DataKey<List<ShelvedChangeList>> SHELVED_DELETED_CHANGELIST_KEY =
    DataKey.create("ShelveChangesManager.ShelvedDeletedChangeListData");
  public static final DataKey<List<ShelvedChange>> SHELVED_CHANGE_KEY = DataKey.create("ShelveChangesManager.ShelvedChange");
  public static final DataKey<List<ShelvedBinaryFile>> SHELVED_BINARY_FILE_KEY = DataKey.create("ShelveChangesManager.ShelvedBinaryFile");

  public static ShelvedChangesViewManager getInstance(Project project) {
    return project.getService(ShelvedChangesViewManager.class);
  }

  public ShelvedChangesViewManager(Project project) {
    myProject = project;
    myUpdateQueue = new MergingUpdateQueue("Update Shelf Content", 200, true, null, myProject, null, true);

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(ShelveChangesManager.SHELF_TOPIC, () -> scheduleTreeUpdate());
  }

  private void scheduleTreeUpdate() {
    myUpdateQueue.queue(Update.create("update", () -> updateTreeModel()));
  }

  @RequiresEdt
  private @NotNull ShelfToolWindowPanel initToolWindowPanel() {
    if (myPanel == null) {
      Activity activity = StartUpMeasurer.startActivity("ShelvedChangesViewManager initialization");
      myPanel = new ShelfToolWindowPanel(myProject);
      Disposer.register(myPanel, () -> {
        // Content is removed from TW
        myPanel = null;
      });
      updateTreeModel();
      activity.end();
    }
    return myPanel;
  }

  public void activateView(final @Nullable ShelvedChangeList list) {
    activateAndUpdate(() -> {
      if (list != null) {
        selectShelvedList(list);
      }
    });
  }

  public static class DisplayNameSupplier implements Supplier<String> {
    @Override
    public String get() {
      return VcsBundle.message("shelf.tab");
    }
  }

  private static @NotNull Set<ShelvedChangeList> getSelectedLists(@NotNull ChangesTree tree,
                                                                  @NotNull Predicate<? super ShelvedChangeList> condition) {
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths == null) return Collections.emptySet();
    return StreamEx.of(selectionPaths)
      .map(path -> TreeUtil.findObjectInPath(path, ShelvedChangeList.class))
      .filter(nonNull())
      .filter(condition)
      .collect(Collectors.toSet());
  }

  static @NotNull ListSelection<ShelvedWrapper> getSelectedChangesOrAll(@NotNull DataContext dataContext) {
    ChangesTree tree = dataContext.getData(SHELVED_CHANGES_TREE);
    if (tree == null) return ListSelection.empty();

    ListSelection<ShelvedWrapper> wrappers = ListSelection.createAt(VcsTreeModelData.selected(tree).userObjects(ShelvedWrapper.class), 0);

    if (wrappers.getList().size() == 1) {
      // return all changes for selected changelist
      ShelvedChangeList changeList = getFirstItem(getSelectedLists(tree, it -> true));
      if (changeList != null) {
        ChangesBrowserNode<?> changeListNode = (ChangesBrowserNode<?>)TreeUtil.findNodeWithObject(tree.getRoot(), changeList);
        if (changeListNode != null) {
          List<ShelvedWrapper> allWrappers = changeListNode.getAllObjectsUnder(ShelvedWrapper.class);
          if (allWrappers.size() > 1) {
            ShelvedWrapper toSelect = getFirstItem(wrappers.getList());
            return ListSelection.create(allWrappers, toSelect);
          }
        }
      }
    }
    return wrappers.asExplicitSelection();
  }

  private void updateTreeIfShown(@NotNull Consumer<? super ShelfTree> treeConsumer) {
    if (myPanel != null) {
      treeConsumer.consume(myPanel.myTree);
    }
  }

  @RequiresEdt
  void updateTreeView() {
    updateTreeIfShown(tree -> {
      tree.rebuildTree();
    });
  }

  @ApiStatus.Internal
  public static boolean hideDefaultShelfTab(@NotNull Project project) {
    AbstractVcs singleVcs = ProjectLevelVcsManager.getInstance(project).getSingleVCS();
    if (singleVcs == null) return false;
    return singleVcs.isWithCustomShelves();
  }

  protected void removeContent(Content content) {
    ChangesViewContentI contentManager = ChangesViewContentManager.getInstance(myProject);
    contentManager.removeContent(content);
    contentManager.selectContent(ChangesViewContentManager.LOCAL_CHANGES);
  }

  protected void addContent(Content content) {
    ChangesViewContentI contentManager = ChangesViewContentManager.getInstance(myProject);
    contentManager.addContent(content);
  }

  protected void activateContent() {
    ChangesViewContentI contentManager = ChangesViewContentManager.getInstance(myProject);
    contentManager.selectContent(SHELF);

    ToolWindow window = getToolWindowFor(myProject, SHELF);
    if (window != null && !window.isVisible()) {
      window.activate(null);
    }
  }

  private static final class MyShelvedTreeModelBuilder extends TreeModelBuilder {
    private MyShelvedTreeModelBuilder(Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
      super(project, grouping);
    }

    public void setShelvedLists(@NotNull List<ShelvedChangeList> shelvedLists) {
      createShelvedListsWithChangesNode(shelvedLists, myRoot);
    }

    public void setDeletedShelvedLists(@NotNull List<ShelvedChangeList> shelvedLists) {
      createShelvedListsWithChangesNode(shelvedLists, createTagNode(VcsBundle.message("shelve.recently.deleted.node")));
    }

    private void createShelvedListsWithChangesNode(@NotNull List<ShelvedChangeList> shelvedLists,
                                                   @NotNull ChangesBrowserNode<?> parentNode) {
      for (ShelvedChangeList changeList : shelvedLists) {
        ShelvedListNode shelvedListNode = new ShelvedListNode(changeList);
        insertSubtreeRoot(shelvedListNode, parentNode);

        List<ShelvedChange> changes = changeList.getChanges();
        if (changes == null) continue;

        List<ShelvedWrapper> shelvedChanges = new ArrayList<>();
        changes.stream().map(change -> new ShelvedWrapper(change, changeList)).forEach(shelvedChanges::add);
        changeList.getBinaryFiles().stream().map(binaryChange -> new ShelvedWrapper(binaryChange, changeList)).forEach(shelvedChanges::add);

        shelvedChanges.sort(comparing(s -> s.getChangeWithLocal(myProject), CHANGE_COMPARATOR));

        for (ShelvedWrapper shelved : shelvedChanges) {
          Change change = shelved.getChangeWithLocal(myProject);
          FilePath filePath = ChangesUtil.getFilePath(change);
          insertChangeNode(change, shelvedListNode, new ShelvedChangeNode(shelved, filePath, change.getOriginText(myProject)));
        }
      }
    }
  }

  @RequiresEdt
  private void updateTreeModel() {
    updateTreeIfShown(tree -> {
      tree.invalidateDataAndRefresh(() -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          myPostUpdateEdtActivity.forEach(Runnable::run);
          myPostUpdateEdtActivity.clear();
        }, ModalityState.nonModal(), myProject.getDisposed());
      });
    });
  }

  @RequiresEdt
  public void startEditing(@NotNull ShelvedChangeList shelvedChangeList) {
    activateAndUpdate(() -> {
      selectShelvedList(shelvedChangeList);
      updateTreeIfShown(tree -> tree.startEditingAtPath(tree.getLeadSelectionPath()));
    });
  }

  public static @NotNull List<ShelvedChangeList> getShelvedLists(final @NotNull DataContext dataContext) {
    List<ShelvedChangeList> shelvedChangeLists = new ArrayList<>();
    shelvedChangeLists.addAll(notNullize(SHELVED_CHANGELIST_KEY.getData(dataContext)));
    shelvedChangeLists.addAll(notNullize(SHELVED_RECYCLED_CHANGELIST_KEY.getData(dataContext)));
    shelvedChangeLists.addAll(notNullize(SHELVED_DELETED_CHANGELIST_KEY.getData(dataContext)));
    return shelvedChangeLists;
  }

  public static @NotNull List<ShelvedChangeList> getExactlySelectedLists(final @NotNull DataContext dataContext) {
    ChangesTree shelvedChangeTree = dataContext.getData(SHELVED_CHANGES_TREE);
    if (shelvedChangeTree == null) return emptyList();
    return VcsTreeModelData.exactlySelected(shelvedChangeTree).iterateUserObjects(ShelvedChangeList.class).toList();
  }

  private void activateAndUpdate(@NotNull Runnable postUpdateRunnable) {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), myProject.getDisposed(), () -> {
      activateContent();
      myUpdateQueue.cancelAllUpdates();
      myPostUpdateEdtActivity.add(postUpdateRunnable);
      updateTreeModel();
    });
  }

  @Override
  public void dispose() {
    myUpdateQueue.cancelAllUpdates();
  }

  public void updateOnVcsMappingsChanged() {
    ApplicationManager.getApplication().invokeLater(() -> {
      updateTreeIfShown(tree -> {
        ChangesGroupingSupport treeGroupingSupport = tree.getGroupingSupport();
        if (treeGroupingSupport.isAvailable(REPOSITORY_GROUPING) && treeGroupingSupport.get(REPOSITORY_GROUPING)) {
          tree.onGroupingChanged();
        }
      });
    }, myProject.getDisposed());
  }

  public void selectShelvedList(@NotNull ShelvedChangeList list) {
    updateTreeIfShown(tree -> {
      DefaultMutableTreeNode treeNode = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)tree.getModel().getRoot(), list);
      if (treeNode == null) {
        LOG.warn(VcsBundle.message("shelve.changelist.not.found", list.DESCRIPTION));
        return;
      }
      TreeUtil.selectNode(tree, treeNode);
    });
  }

  public static @NotNull List<ShelvedChange> getShelveChanges(final @NotNull DataContext dataContext) {
    return notNullize(dataContext.getData(SHELVED_CHANGE_KEY));
  }

  public static @NotNull List<ShelvedBinaryFile> getBinaryShelveChanges(final @NotNull DataContext dataContext) {
    return notNullize(dataContext.getData(SHELVED_BINARY_FILE_KEY));
  }

  public static @NotNull List<String> getSelectedShelvedChangeNames(final @NotNull DataContext dataContext) {
    ChangesTree shelvedChangeTree = dataContext.getData(SHELVED_CHANGES_TREE);
    if (shelvedChangeTree == null) return emptyList();
    return VcsTreeModelData.selected(shelvedChangeTree).iterateUserObjects(ShelvedWrapper.class)
      .map(ShelvedWrapper::getPath).toList();
  }

  private static @NotNull List<ShelvedChange> getChangesNotInLists(@NotNull List<ShelvedChangeList> listsToDelete,
                                                                   @NotNull List<ShelvedChange> shelvedChanges) {
    List<ShelvedChange> result = new ArrayList<>(shelvedChanges);
    // all changes should be loaded because action performed from loaded shelf tab
    listsToDelete.stream().map(list -> requireNonNull(list.getChanges())).forEach(result::removeAll);
    return result;
  }

  private static @NotNull @Nls String constructDeleteSuccessfullyMessage(int fileNum, int listNum, @Nullable ShelvedChangeList first) {
    String filesMessage = fileNum != 0 ? VcsBundle.message("shelve.delete.files.successful.message", fileNum) : "";
    String listDescription = listNum == 1 && first != null ? first.DESCRIPTION : "";
    String changelistsMessage = listNum != 0
                                ? VcsBundle.message("shelve.delete.changelists.message",
                                                    listNum, listDescription, listDescription.isEmpty() ? 0 : 1)
                                : "";
    return StringUtil.capitalize(VcsBundle.message("shelve.delete.successful.message",
                                                   filesMessage, fileNum > 0 && listNum > 0 ? 1 : 0, changelistsMessage));
  }

  public static class ContentPreloader implements ChangesViewContentProvider.Preloader {
    private final @NotNull Project myProject;

    public ContentPreloader(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void preloadTabContent(@NotNull Content content) {
      content.putUserData(Content.TAB_DND_TARGET_KEY, new MyDnDTarget(myProject, content));
    }
  }

  static final class ContentPredicate implements Predicate<Project> {
    @Override
    public boolean test(Project project) {
      if (hideDefaultShelfTab(project)) return false;
      // do not init manager on EDT - wait for ShelveChangesManager.PostStartupActivity
      ShelveChangesManager shelveManager = project.getServiceIfCreated(ShelveChangesManager.class);
      return shelveManager != null && !shelveManager.getAllLists().isEmpty();
    }
  }

  public static class ContentProvider implements ChangesViewContentProvider {
    private final @NotNull Project myProject;

    public ContentProvider(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void initTabContent(@NotNull Content content) {
      ShelfToolWindowPanel panel = getInstance(myProject).initToolWindowPanel();
      content.setHelpId(HELP_ID);
      content.setComponent(panel);
      content.setDisposer(panel);
      content.setPreferredFocusableComponent(panel.myTree);
    }
  }

  static class ChangelistComparator implements Comparator<ShelvedChangeList> {
    private static final ChangelistComparator ourInstance = new ChangelistComparator();

    public static ChangelistComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(ShelvedChangeList o1, ShelvedChangeList o2) {
      return o2.DATE.compareTo(o1.DATE);
    }
  }

  public static void deleteShelves(@NotNull Project project, @NotNull List<ShelvedChangeList> shelvedListsToDelete) {
    deleteShelves(project, shelvedListsToDelete, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  private static void deleteShelves(@NotNull Project project, @NotNull List<ShelvedChangeList> shelvedListsToDelete,
                                    @NotNull List<ShelvedChangeList> shelvedListsFromChanges,
                                    @NotNull List<ShelvedChange> selectedChanges,
                                    @NotNull List<ShelvedBinaryFile> selectedBinaryChanges) {
    List<ShelvedChange> changesToDelete = getChangesNotInLists(shelvedListsToDelete, selectedChanges);
    List<ShelvedBinaryFile> binariesToDelete = getBinariesNotInLists(shelvedListsToDelete, selectedBinaryChanges);

    ShelveChangesManager manager = ShelveChangesManager.getInstance(project);
    int fileListSize = binariesToDelete.size() + changesToDelete.size();
    Map<ShelvedChangeList, Date> createdDeletedListsWithOriginalDates =
      manager.deleteShelves(shelvedListsToDelete, shelvedListsFromChanges, changesToDelete, binariesToDelete);
    if (!createdDeletedListsWithOriginalDates.isEmpty()) {
      showUndoDeleteNotification(project, shelvedListsToDelete, fileListSize, createdDeletedListsWithOriginalDates);
    }
  }

  private static void showUndoDeleteNotification(@NotNull Project project, @NotNull List<ShelvedChangeList> shelvedListsToDelete,
                                                 int fileListSize,
                                                 @NotNull Map<ShelvedChangeList, Date> createdDeletedListsWithOriginalDate) {
    String message = constructDeleteSuccessfullyMessage(fileListSize, shelvedListsToDelete.size(), getFirstItem(shelvedListsToDelete));
    Notification shelfDeletionNotification = new ShelfDeleteNotification(message);
    shelfDeletionNotification.setDisplayId(VcsNotificationIdsHolder.SHELF_UNDO_DELETE);
    shelfDeletionNotification.addAction(new UndoShelfDeletionAction(project, createdDeletedListsWithOriginalDate));
    VcsNotifier.getInstance(project).showNotificationAndHideExisting(shelfDeletionNotification, ShelfDeleteNotification.class);
  }

  private static final class ShelfTree extends AsyncChangesTree {
    private final DeleteProvider myDeleteProvider = new MyShelveDeleteProvider(myProject, this);
    private final ShelfTreeAsyncModel myAsyncTreeModel;

    private ShelfTree(@NotNull Project project) {
      super(project, false, false, false);
      myAsyncTreeModel = new ShelfTreeAsyncModel(project, getScope());

      TreeSpeedSearch.installOn(this, true, ChangesBrowserNode.TO_TEXT_CONVERTER);
      setKeepTreeState(true);
      setDoubleClickHandler(e -> showShelvedChangesDiff());
      setEnterKeyHandler(e -> showShelvedChangesDiff());
    }

    @Override
    protected @NotNull AsyncChangesTreeModel getChangesTreeModel() {
      return myAsyncTreeModel;
    }

    @Override
    public boolean isPathEditable(TreePath path) {
      return isEditable() && getSelectionCount() == 1 && path.getLastPathComponent() instanceof ShelvedListNode;
    }

    @Override
    protected @NotNull ChangesGroupingSupport installGroupingSupport() {
      ChangesGroupingSupport groupingSupport = new ChangesGroupingSupport(myProject, this, false);
      ChangesTree.installGroupingSupport(this, groupingSupport,
                                         () -> ShelveChangesManager.getInstance(myProject).getGrouping(),
                                         (newGrouping) -> ShelveChangesManager.getInstance(myProject).setGrouping(newGrouping));
      return groupingSupport;
    }

    @Override
    public int getToggleClickCount() {
      return 2;
    }

    private boolean showShelvedChangesDiff() {
      if (!hasExactlySelectedChanges()) return false;
      DiffShelvedChangesActionProvider.showShelvedChangesDiff(DataManager.getInstance().getDataContext(this));
      return true;
    }

    private boolean hasExactlySelectedChanges() {
      return VcsTreeModelData.exactlySelected(this).iterateUserObjects(ShelvedWrapper.class).isNotEmpty();
    }

    @Override
    public @Nullable Object getData(@NotNull @NonNls String dataId) {
      if (SHELVED_CHANGES_TREE.is(dataId)) {
        return this;
      }
      else if (SHELVED_CHANGELIST_KEY.is(dataId)) {
        return new ArrayList<>(getSelectedLists(this, l -> !l.isRecycled() && !l.isDeleted()));
      }
      else if (SHELVED_RECYCLED_CHANGELIST_KEY.is(dataId)) {
        return new ArrayList<>(getSelectedLists(this, l -> l.isRecycled() && !l.isDeleted()));
      }
      else if (SHELVED_DELETED_CHANGELIST_KEY.is(dataId)) {
        return new ArrayList<>(getSelectedLists(this, l -> l.isDeleted()));
      }
      else if (SHELVED_CHANGE_KEY.is(dataId)) {
        return VcsTreeModelData.selected(this).iterateUserObjects(ShelvedWrapper.class)
          .map(s -> s.getShelvedChange())
          .filterNotNull().toList();
      }
      else if (SHELVED_BINARY_FILE_KEY.is(dataId)) {
        return VcsTreeModelData.selected(this).iterateUserObjects(ShelvedWrapper.class)
          .map(s -> s.getBinaryFile())
          .filterNotNull().toList();
      }
      else if (VcsDataKeys.CHANGES.is(dataId)) {
        List<ShelvedWrapper> shelvedChanges = VcsTreeModelData.selected(this).userObjects(ShelvedWrapper.class);
        if (!shelvedChanges.isEmpty()) {
          return map2Array(shelvedChanges, Change.class, s -> s.getChangeWithLocal(myProject));
        }
      }
      else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) && !isEditing()) {
        return myDeleteProvider;
      }
      else if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
        List<ShelvedWrapper> shelvedChanges = VcsTreeModelData.selected(this).userObjects(ShelvedWrapper.class);
        final ArrayDeque<Navigatable> navigatables = new ArrayDeque<>();
        for (final ShelvedWrapper shelvedChange : shelvedChanges) {
          if (shelvedChange.getBeforePath() != null && !FileStatus.ADDED.equals(shelvedChange.getFileStatus())) {
            final NavigatableAdapter navigatable = new NavigatableAdapter() {
              @Override
              public void navigate(boolean requestFocus) {
                final VirtualFile vf = shelvedChange.getBeforeVFUnderProject(myProject);
                if (vf != null) {
                  navigate(myProject, vf, true);
                }
              }
            };
            navigatables.add(navigatable);
          }
        }
        return navigatables.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
      }
      else if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
        return HELP_ID;
      }
      return super.getData(dataId);
    }

    public void invalidateDataAndRefresh(@Nullable Runnable onRefreshed) {
      myAsyncTreeModel.invalidateData();
      requestRefresh(onRefreshed);
    }
  }

  private static List<ShelvedBinaryFile> getBinariesNotInLists(@NotNull List<ShelvedChangeList> listsToDelete,
                                                               @NotNull List<ShelvedBinaryFile> binaryFiles) {
    List<ShelvedBinaryFile> result = new ArrayList<>(binaryFiles);
    for (ShelvedChangeList list : listsToDelete) {
      result.removeAll(list.getBinaryFiles());
    }
    return result;
  }

  private static class ShelfTreeAsyncModel extends TwoStepAsyncChangesTreeModel<List<ShelvedChangeList>> {
    private final Project myProject;

    private ShelfTreeAsyncModel(@NotNull Project project, @NotNull CoroutineScope scope) {
      super(scope);
      myProject = project;
    }

    @Override
    public List<ShelvedChangeList> fetchData() {
      List<ShelvedChangeList> lists = ShelveChangesManager.getInstance(myProject).getAllLists();
      lists.forEach(l -> l.loadChangesIfNeeded(myProject));
      return sorted(lists, ChangelistComparator.getInstance());
    }

    @Override
    public @NotNull DefaultTreeModel buildTreeModelSync(@NotNull List<ShelvedChangeList> changeLists,
                                                        @NotNull ChangesGroupingPolicyFactory grouping) {
      boolean showRecycled = ShelveChangesManager.getInstance(myProject).isShowRecycled();
      MyShelvedTreeModelBuilder modelBuilder = new MyShelvedTreeModelBuilder(myProject, grouping);
      modelBuilder.setShelvedLists(filter(changeLists, l -> !l.isDeleted() && (showRecycled || !l.isRecycled())));
      modelBuilder.setDeletedShelvedLists(filter(changeLists, ShelvedChangeList::isDeleted));
      return modelBuilder.build();
    }
  }

  private static final class UndoShelfDeletionAction extends NotificationAction {
    private final @NotNull Project myProject;
    private final @NotNull Map<ShelvedChangeList, Date> myListDateMap;

    private UndoShelfDeletionAction(@NotNull Project project, @NotNull Map<ShelvedChangeList, Date> listDateMap) {
      super(IdeBundle.messagePointer("undo.dialog.title"));
      myProject = project;
      myListDateMap = listDateMap;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      ShelveChangesManager manager = ShelveChangesManager.getInstance(myProject);
      List<ShelvedChangeList> cantRestoreList = findAll(myListDateMap.keySet(), l -> l == null || !manager.getDeletedLists().contains(l));
      myListDateMap.forEach((l, d) -> manager.restoreList(l, d));
      notification.expire();
      if (!cantRestoreList.isEmpty()) {
        VcsNotifier.getInstance(myProject).notifyMinorWarning(SHELVE_DELETION_UNDO,
                                                              VcsBundle.message("shelve.undo.deletion"),
                                                              VcsBundle.message("shelve.changes.restore.error", cantRestoreList.size()));
      }
    }
  }

  private static final class MyShelveDeleteProvider implements DeleteProvider {
    private final @NotNull Project myProject;
    private final @NotNull ShelfTree myTree;

    private MyShelveDeleteProvider(@NotNull Project project, @NotNull ShelfTree tree) {
      myProject = project;
      myTree = tree;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      List<ShelvedChangeList> shelvedListsToDelete = TreeUtil.collectSelectedObjectsOfType(myTree, ShelvedChangeList.class);
      List<ShelvedChangeList> shelvedListsFromChanges = getShelvedLists(dataContext);
      List<ShelvedChange> selectedChanges = getShelveChanges(dataContext);
      List<ShelvedBinaryFile> selectedBinaryChanges = getBinaryShelveChanges(dataContext);

      deleteShelves(myProject, shelvedListsToDelete, shelvedListsFromChanges, selectedChanges, selectedBinaryChanges);
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return !getShelvedLists(dataContext).isEmpty();
    }
  }

  private static final class MyDnDTarget extends VcsToolwindowDnDTarget {
    private MyDnDTarget(@NotNull Project project, @NotNull Content content) {
      super(project, content);
    }

    @Override
    public void drop(DnDEvent event) {
      super.drop(event);
      handleDropEvent(myProject, event);
    }

    @Override
    public boolean isDropPossible(@NotNull DnDEvent event) {
      return canHandleDropEvent(myProject, event);
    }
  }

  private static boolean canHandleDropEvent(@NotNull Project project, @NotNull DnDEvent event) {
    Object attachedObject = event.getAttachedObject();
    if (attachedObject instanceof ChangeListDragBean) {
      List<Change> changes = ((ChangeListDragBean)attachedObject).getChanges();
      return !changes.isEmpty();
    }
    return false;
  }

  private static void handleDropEvent(@NotNull Project project, @NotNull DnDEvent event) {
    Object attachedObject = event.getAttachedObject();
    if (attachedObject instanceof ChangeListDragBean) {
      FileDocumentManager.getInstance().saveAllDocuments();
      List<Change> changes = ((ChangeListDragBean)attachedObject).getChanges();
      ShelveChangesManager.getInstance(project).shelveSilentlyUnderProgress(changes, true);
    }
  }

  private static final class ShelfToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
    private static final @NotNull RegistryValue isOpenEditorDiffPreviewWithSingleClick =
      Registry.get("show.diff.preview.as.editor.tab.with.single.click");

    private final Project myProject;
    private final ShelveChangesManager myShelveChangesManager;
    private final VcsConfiguration myVcsConfiguration;

    private final @NotNull JScrollPane myTreeScrollPane;
    private final ShelfTree myTree;

    private MyShelvedPreviewProcessor myEditorChangeProcessor;
    private MyShelvedPreviewProcessor mySplitterChangeProcessor;
    private EditorTabPreview myEditorDiffPreview;
    private PreviewDiffSplitterComponent mySplitterDiffPreview;

    private ShelfToolWindowPanel(@NotNull Project project) {
      super(true);
      myProject = project;
      myShelveChangesManager = ShelveChangesManager.getInstance(myProject);
      myVcsConfiguration = VcsConfiguration.getInstance(myProject);

      myTree = new ShelfTree(myProject);
      myTree.setEditable(true);
      myTree.setDragEnabled(!ApplicationManager.getApplication().isHeadlessEnvironment());
      myTree.setCellEditor(new ShelveRenameTreeCellEditor());

      final AnAction showDiffAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON);
      showDiffAction.registerCustomShortcutSet(showDiffAction.getShortcutSet(), myTree);
      final EditSourceAction editSourceAction = new EditSourceAction();
      editSourceAction.registerCustomShortcutSet(editSourceAction.getShortcutSet(), myTree);

      DefaultActionGroup actionGroup = new DefaultActionGroup();
      actionGroup.addAll((ActionGroup)ActionManager.getInstance().getAction(SHELVED_CHANGES_TOOLBAR));
      actionGroup.add(Separator.getInstance());
      actionGroup.add(new MyToggleDetailsAction());

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ShelvedChanges", actionGroup, false);
      toolbar.setTargetComponent(myTree);
      myTreeScrollPane = ScrollPaneFactory.createScrollPane(myTree, true);

      setContent(myTreeScrollPane);
      setToolbar(toolbar.getComponent());
      updatePanelLayout();

      setDiffPreview();
      EditorTabDiffPreviewManager.getInstance(project).subscribeToPreviewVisibilityChange(this, this::setDiffPreview);
      isOpenEditorDiffPreviewWithSingleClick.addListener(new RegistryValueListener() {
        @Override
        public void afterValueChanged(@NotNull RegistryValue value) {
          if (myEditorDiffPreview != null) setDiffPreview();
        }
      }, this);
      myProject.getMessageBus().connect(this).subscribe(ChangesViewContentManagerListener.TOPIC, () -> updatePanelLayout());

      PopupHandler.installPopupMenu(myTree, "ShelvedChangesPopupMenu", SHELF_CONTEXT_MENU);
      new MyDnDSupport(myProject, myTree, myTreeScrollPane).install(this);
    }

    @Override
    public void dispose() {
      myTree.shutdown();
    }

    private void updatePanelLayout() {
      setVertical(isCommitToolWindowShown(myProject));
    }

    private void setDiffPreview() {
      boolean isEditorPreview = isEditorPreview(myProject);
      boolean hasSplitterPreview = !isCommitToolWindowShown(myProject);

      //noinspection DoubleNegation
      boolean needUpdatePreviews = isEditorPreview != (myEditorChangeProcessor != null) ||
                                   hasSplitterPreview != (mySplitterChangeProcessor != null);
      if (!needUpdatePreviews) return;

      if (myEditorChangeProcessor != null) Disposer.dispose(myEditorChangeProcessor);
      if (mySplitterChangeProcessor != null) Disposer.dispose(mySplitterChangeProcessor);

      if (isEditorPreview) {
        myEditorChangeProcessor = new MyShelvedPreviewProcessor(myProject, myTree, true);
        Disposer.register(this, myEditorChangeProcessor);
        myEditorDiffPreview = installEditorPreview(myEditorChangeProcessor, hasSplitterPreview);
      }
      else {
        myEditorChangeProcessor = null;
        myEditorDiffPreview = null;
      }

      if (hasSplitterPreview) {
        mySplitterChangeProcessor = new MyShelvedPreviewProcessor(myProject, myTree, false);
        Disposer.register(this, mySplitterChangeProcessor);
        mySplitterDiffPreview = installSplitterPreview(mySplitterChangeProcessor);
      }
      else {
        mySplitterChangeProcessor = null;
        mySplitterDiffPreview = null;
      }
    }

    private @NotNull EditorTabPreview installEditorPreview(@NotNull MyShelvedPreviewProcessor changeProcessor, boolean hasSplitterPreview) {
      return new SimpleTreeEditorDiffPreview(changeProcessor, myTree, myTreeScrollPane,
                                             isOpenEditorDiffPreviewWithSingleClick.asBoolean() && !hasSplitterPreview) {
        @Override
        public void returnFocusToTree() {
          ToolWindow toolWindow = getToolWindowFor(myProject, SHELF);
          if (toolWindow != null) toolWindow.activate(null);
        }

        @Override
        public void updateDiffAction(@NotNull AnActionEvent event) {
          DiffShelvedChangesActionProvider.updateAvailability(event);
        }

        @Override
        protected String getCurrentName() {
          Wrapper myCurrentShelvedElement = changeProcessor.getCurrentChange();
          return myCurrentShelvedElement != null
                 ? VcsBundle.message("shelve.editor.diff.preview.title", myCurrentShelvedElement.getPresentableName())
                 : VcsBundle.message("shelved.version.name");
        }

        @Override
        protected boolean skipPreviewUpdate() {
          if (super.skipPreviewUpdate()) return true;
          if (!myTree.equals(IdeFocusManager.getInstance(myProject).getFocusOwner())) return true;
          if (!isPreviewOpen() && !isEditorPreviewAllowed()) return true;

          return false;
        }
      };
    }

    private @NotNull PreviewDiffSplitterComponent installSplitterPreview(@NotNull MyShelvedPreviewProcessor changeProcessor) {
      PreviewDiffSplitterComponent previewSplitter =
        new PreviewDiffSplitterComponent(changeProcessor, SHELVE_PREVIEW_SPLITTER_PROPORTION);
      previewSplitter.setFirstComponent(myTreeScrollPane);
      DiffPreview.setPreviewVisible(previewSplitter, myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN);

      myTree.addSelectionListener(() -> previewSplitter.updatePreview(false), changeProcessor);

      setContent(previewSplitter);
      Disposer.register(changeProcessor, () -> {
        setContent(myTreeScrollPane);
      });

      return previewSplitter;
    }

    private boolean isEditorPreviewAllowed() {
      return !isOpenEditorDiffPreviewWithSingleClick.asBoolean() || myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN;
    }

    private static class MyDnDSupport implements DnDDropHandler, DnDTargetChecker {
      private final @NotNull Project myProject;
      private final @NotNull ChangesTree myTree;
      private final @NotNull JScrollPane myTreeScrollPane;

      private MyDnDSupport(@NotNull Project project,
                           @NotNull ChangesTree tree,
                           @NotNull JScrollPane treeScrollPane) {
        myProject = project;
        myTree = tree;
        myTreeScrollPane = treeScrollPane;
      }

      public void install(@NotNull Disposable disposable) {
        DnDSupport.createBuilder(myTree)
          .setTargetChecker(this)
          .setDropHandler(this)
          .setImageProvider(this::createDraggedImage)
          .setBeanProvider(this::createDragStartBean)
          .setDisposableParent(disposable)
          .install();
      }

      @Override
      public void drop(DnDEvent aEvent) {
        handleDropEvent(myProject, aEvent);
      }

      @Override
      public boolean update(DnDEvent aEvent) {
        aEvent.hideHighlighter();
        aEvent.setDropPossible(false, "");

        boolean canHandle = canHandleDropEvent(myProject, aEvent);
        if (!canHandle) return true;

        // highlight top of the tree
        Rectangle tableCellRect = new Rectangle(0, 0, JBUI.scale(300), JBUI.scale(12));
        aEvent.setHighlighting(new RelativeRectangle(myTreeScrollPane, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
        aEvent.setDropPossible(true);

        return false;
      }

      private @Nullable DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info) {
        if (info.isMove()) {
          DataContext dc = DataManager.getInstance().getDataContext(myTree);
          return new DnDDragStartBean(new ShelvedChangeListDragBean(getShelveChanges(dc), getBinaryShelveChanges(dc), getShelvedLists(dc)));
        }
        return null;
      }

      private @NotNull DnDImage createDraggedImage(@NotNull DnDActionInfo info) {
        String imageText = VcsBundle.message("unshelve.changes.action");
        Image image = DnDAwareTree.getDragImage(myTree, imageText, null).getFirst();
        return new DnDImage(image, new Point(-image.getWidth(null), -image.getHeight(null)));
      }
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      if (EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW.is(dataId)) {
        return myEditorDiffPreview;
      }
      return super.getData(dataId);
    }

    private class MyToggleDetailsAction extends ShowDiffPreviewAction {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabledAndVisible(mySplitterDiffPreview != null || isOpenEditorDiffPreviewWithSingleClick.asBoolean());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        DiffPreview previewSplitter = ObjectUtils.chooseNotNull(mySplitterDiffPreview, myEditorDiffPreview);
        DiffPreview.setPreviewVisible(previewSplitter, state);
        myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN = state;
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN;
      }
    }

    private class ShelveRenameTreeCellEditor extends DefaultTreeCellEditor implements CellEditorListener {
      ShelveRenameTreeCellEditor() {
        super(myTree, null);
        addCellEditorListener(this);
      }

      @Override
      public boolean isCellEditable(EventObject event) {
        return !(event instanceof MouseEvent) && super.isCellEditable(event);
      }

      @Override
      public void editingStopped(ChangeEvent e) {
        TreeNode node = (TreeNode)myTree.getLastSelectedPathComponent();
        if (node instanceof ShelvedListNode changeListNode &&
            e.getSource() instanceof TreeCellEditor treeCellEditor) {
          String editorValue = treeCellEditor.getCellEditorValue().toString();
          ShelvedChangeList shelvedChangeList = changeListNode.getList();
          myShelveChangesManager.renameChangeList(shelvedChangeList, editorValue);
        }
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
      }
    }
  }

  private static class MyShelvedPreviewProcessor extends ChangeViewDiffRequestProcessor implements DiffPreviewUpdateProcessor {
    private final @NotNull ShelfTree myTree;
    private final boolean myIsInEditor;

    private final @NotNull PatchesPreloader myPreloader;

    MyShelvedPreviewProcessor(@NotNull Project project, @NotNull ShelfTree tree, boolean isInEditor) {
      super(project, DiffPlaces.SHELVE_VIEW);
      myTree = tree;
      myIsInEditor = isInEditor;
      myPreloader = new PatchesPreloader(project);
      putContextUserData(PatchesPreloader.SHELF_PRELOADER, myPreloader);
    }

    @RequiresEdt
    @Override
    public void clear() {
      setCurrentChange(null);
      dropCaches();
    }

    @Override
    protected boolean shouldAddToolbarBottomBorder(@NotNull FrameDiffTool.ToolbarComponents toolbarComponents) {
      return !myIsInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents);
    }

    @Override
    public @NotNull Iterable<? extends Wrapper> iterateSelectedChanges() {
      return VcsTreeModelData.selected(myTree).iterateUserObjects(ShelvedWrapper.class);
    }

    @Override
    public @NotNull Iterable<? extends Wrapper> iterateAllChanges() {
      Set<ShelvedChangeList> changeLists =
        VcsTreeModelData.selected(myTree).iterateUserObjects(ShelvedWrapper.class)
          .map(wrapper -> wrapper.getChangeList())
          .toSet();

      return VcsTreeModelData.all(myTree).iterateRawNodes()
        .filter(node -> node instanceof ShelvedListNode && changeLists.contains(((ShelvedListNode)node).getList()))
        .flatMap(node -> VcsTreeModelData.allUnder(node).iterateUserObjects(ShelvedWrapper.class));
    }

    @Override
    protected void selectChange(@NotNull Wrapper change) {
      if (change instanceof ShelvedWrapper) {
        DefaultMutableTreeNode root = myTree.getRoot();
        DefaultMutableTreeNode changelistNode = TreeUtil.findNodeWithObject(root, ((ShelvedWrapper)change).getChangeList());
        if (changelistNode == null) return;

        DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(changelistNode, change);
        if (node == null) return;
        TreeUtil.selectPath(myTree, TreeUtil.getPathFromRoot(node), false);
      }
    }

    @Override
    protected @Nullable DiffRequest loadRequestFast(@NotNull DiffRequestProducer provider) {
      if (provider instanceof ShelvedWrapperDiffRequestProducer) {
        ShelvedChange shelvedChange = ((ShelvedWrapperDiffRequestProducer)provider).getWrapper().getShelvedChange();
        if (shelvedChange != null && myPreloader.isPatchFileChanged(shelvedChange.getPatchPath())) return null;
      }

      return super.loadRequestFast(provider);
    }
  }

  private static class ShelvedListNode extends ChangesBrowserNode<ShelvedChangeList> {
    private final @NotNull ShelvedChangeList myList;

    ShelvedListNode(@NotNull ShelvedChangeList list) {
      super(list);
      myList = list;
    }

    public @NotNull ShelvedChangeList getList() {
      return myList;
    }

    @Override
    public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
      String listName = myList.DESCRIPTION;
      if (StringUtil.isEmptyOrSpaces(listName)) listName = VcsBundle.message("changes.nodetitle.empty.changelist.name");

      if (myList.isRecycled() || myList.isDeleted()) {
        renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
      else {
        renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }

      appendCount(renderer);
      String date = DateFormatUtil.formatPrettyDateTime(myList.DATE);
      renderer.append(", " + date, SimpleTextAttributes.GRAYED_ATTRIBUTES);

      String loadingError = myList.getChangesLoadingError();
      if (loadingError != null) {
        renderer.append(spaceAndThinSpace() + loadingError, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    @Override
    public @Nls String getTextPresentation() {
      return getUserObject().toString();
    }
  }

  private static class ShelvedChangeNode extends ChangesBrowserNode<ShelvedWrapper> implements Comparable<ShelvedChangeNode> {

    private final @NotNull ShelvedWrapper myShelvedChange;
    private final @NotNull FilePath myFilePath;
    private final @Nullable @Nls String myAdditionalText;

    protected ShelvedChangeNode(@NotNull ShelvedWrapper shelvedChange,
                                @NotNull FilePath filePath,
                                @Nullable @Nls String additionalText) {
      super(shelvedChange);
      myShelvedChange = shelvedChange;
      myFilePath = filePath;
      myAdditionalText = additionalText;
    }

    @Override
    public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
      String path = myShelvedChange.getRequestName();
      String directory = StringUtil.defaultIfEmpty(PathUtil.getParentPath(path), VcsBundle.message("shelve.default.path.rendering"));
      String fileName = StringUtil.defaultIfEmpty(PathUtil.getFileName(path), path);

      renderer.append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, myShelvedChange.getFileStatus().getColor()));
      if (myAdditionalText != null) {
        renderer.append(spaceAndThinSpace() + myAdditionalText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      if (renderer.isShowFlatten()) {
        renderer.append(spaceAndThinSpace() + FileUtil.toSystemDependentName(directory), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      renderer.setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon());
    }

    @Override
    public String getTextPresentation() {
      return PathUtil.getFileName(myShelvedChange.getRequestName());
    }

    @Override
    protected boolean isFile() {
      return true;
    }

    @Override
    public int compareTo(@NotNull ShelvedChangeNode o) {
      return compareFilePaths(myFilePath, o.myFilePath);
    }

    @Override
    public @Nullable Color getBackgroundColor(@NotNull Project project) {
      return getBackgroundColorFor(project, myFilePath);
    }
  }

  public static class MyShelfManagerListener implements ShelveChangesManagerListener {
    private final Project myProject;

    public MyShelfManagerListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void shelvedListsChanged() {
      ApplicationManager.getApplication().invokeLater(() -> {
        BackgroundTaskUtil.syncPublisher(myProject, ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged();
      });
    }
  }
}
