// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.platform.vcs.VcsSharedDataKeys;
import com.intellij.platform.vcs.impl.shared.RdLocalChanges;
import com.intellij.platform.vcs.impl.shared.actions.VcsTransferableDataKeys;
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel;
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSelection;
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitNode;
import com.intellij.ui.PopupHandler;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.IGNORED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.LOCALLY_DELETED_NODE_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserResolvedConflictsNodeKt.RESOLVED_CONFLICTS_NODE_TAG;
import static com.intellij.platform.vcs.changes.ChangesUtil.getNavigatableArray;

// TODO: Check if we could extend DnDAwareTree here instead of directly implementing DnDAware
public abstract class ChangesListView extends ChangesTree implements DnDAware {
  private static final Logger LOG = Logger.getInstance(ChangesListView.class);

  public static final @NonNls String HELP_ID = "ideaInterface.changes";
  // FIXME IJPL-173924 should be replaced with an RD-friendly version
  public static final @NonNls DataKey<ChangesListView> DATA_KEY
    = DataKey.create("ChangeListView");
  public static final @NonNls DataKey<Iterable<FilePath>> UNVERSIONED_FILE_PATHS_DATA_KEY
    = DataKey.create("ChangeListView.UnversionedFiles");
  public static final @NonNls DataKey<Iterable<VirtualFile>> EXACTLY_SELECTED_FILES_DATA_KEY
    = DataKey.create("ChangeListView.ExactlySelectedFiles");
  public static final @NonNls DataKey<Iterable<FilePath>> IGNORED_FILE_PATHS_DATA_KEY
    = DataKey.create("ChangeListView.IgnoredFiles");
  public static final @NonNls DataKey<Iterable<VirtualFile>> MODIFIED_WITHOUT_EDITING_DATA_KEY
    = DataKey.create("ChangeListView.ModifiedWithoutEditing");
  public static final @NonNls DataKey<List<FilePath>> MISSING_FILES_DATA_KEY
    = DataKey.create("ChangeListView.MissingFiles");
  public static final @NonNls DataKey<List<LocallyDeletedChange>> LOCALLY_DELETED_CHANGES
    = DataKey.create("ChangeListView.LocallyDeletedChanges");

  private boolean myBusy = false;

  public ChangesListView(@NotNull Project project, boolean showCheckboxes) {
    super(project, showCheckboxes, true, true, true);
    // setDragEnabled throws an exception in headless mode which leads to a memory leak
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      setDragEnabled(true);
    }
  }

  @Override
  public int getToggleClickCount() {
    return 2;
  }

  @Override
  public void setPaintBusy(boolean paintBusy) {
    myBusy = paintBusy;
    super.setPaintBusy(paintBusy);
  }

  @Override
  protected boolean isEmptyTextVisible() {
    return super.isEmptyTextVisible() && !myBusy;
  }

  @Override
  protected boolean isInclusionVisible(@NotNull ChangesBrowserNode<?> node) {
    ChangesBrowserNode<?> subtreeRoot = getSubtreeRoot(node);
    Object subtreeRootObject = subtreeRoot != null ? subtreeRoot.getUserObject() : null;

    if (subtreeRootObject instanceof LocalChangeList localChangeList) return !localChangeList.getChanges().isEmpty();
    if (subtreeRootObject == UNVERSIONED_FILES_TAG && subtreeRoot.getChildCount() > 0) return true;
    return false;
  }

  @Override
  protected boolean isIncludable(@NotNull ChangesBrowserNode<?> node) {
    if (VcsTreeModelDataExKt.isUnderTag(node, RESOLVED_CONFLICTS_NODE_TAG)) return true;

    return super.isIncludable(node);
  }

  private static @Nullable ChangesBrowserNode<?> getSubtreeRoot(@NotNull ChangesBrowserNode<?> node) {
    TreeNode[] path = node.getPath();
    if (path.length < 2) return null;
    return (ChangesBrowserNode<?>)path[1];
  }

  @Override
  public DefaultTreeModel getModel() {
    return (DefaultTreeModel)super.getModel();
  }

  @Override
  public void rebuildTree() {
    // currently not used in ChangesListView code flow
    LOG.warn("rebuildTree() not implemented in " + this, new Throwable());
  }

  @Override
  @ApiStatus.Internal
  public void updateTreeModel(@NotNull DefaultTreeModel model,
                              @NotNull TreeStateStrategy treeStateStrategy) {
    super.updateTreeModel(model, treeStateStrategy);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(DATA_KEY, this);
    sink.set(VcsSharedDataKeys.CHANGES, getSelectedChanges()
      .toArray(Change.EMPTY_CHANGE_ARRAY));
    sink.set(VcsSharedDataKeys.CHANGE_LEAD_SELECTION, VcsTreeModelData.exactlySelected(this)
      .iterateUserObjects(Change.class)
      .toArray(Change.EMPTY_CHANGE_ARRAY));
    sink.set(VcsSharedDataKeys.CHANGE_LISTS, VcsTreeModelData.exactlySelected(this)
      .iterateRawUserObjects(ChangeList.class)
      .toList().toArray(ChangeList[]::new));
    JBIterable<FilePath> filePaths = VcsTreeModelData.mapToFilePath(VcsTreeModelData.selected(this));
    sink.set(VcsSharedDataKeys.FILE_PATHS, filePaths);
    if (!IdeProductMode.isFrontend() && filePaths.isNotEmpty()) {
      sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new VirtualFileDeleteProvider());
    }
    sink.set(UNVERSIONED_FILE_PATHS_DATA_KEY, getSelectedUnversionedFiles());
    sink.set(IGNORED_FILE_PATHS_DATA_KEY, getSelectedIgnoredFiles());
    sink.set(MODIFIED_WITHOUT_EDITING_DATA_KEY, getSelectedModifiedWithoutEditing());
    sink.set(LOCALLY_DELETED_CHANGES, getSelectedLocallyDeletedChanges().toList());
    sink.set(MISSING_FILES_DATA_KEY, getSelectedLocallyDeletedChanges()
      .map(LocallyDeletedChange::getPath)
      .toList());
    sink.set(PlatformCoreDataKeys.HELP_ID, HELP_ID);

    VcsTreeModelData treeSelection = VcsTreeModelData.selected(this);
    VcsTreeModelData exactSelection = VcsTreeModelData.exactlySelected(this);
    sink.lazy(SelectInContext.DATA_KEY, () -> {
      VirtualFile file = VcsTreeModelData.mapObjectToVirtualFile(exactSelection.iterateRawUserObjects()).first();
      if (file == null) return null;
      return new FileSelectInContext(myProject, file, null);
    });
    sink.lazy(CommonDataKeys.VIRTUAL_FILE, () -> {
      return VcsTreeModelData.findSelectedVirtualFile(this);
    });
    sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY, () -> {
      return VcsTreeModelData.mapToVirtualFile(treeSelection)
        .toArray(VirtualFile.EMPTY_ARRAY);
    });
    sink.lazy(VcsSharedDataKeys.VIRTUAL_FILES, () -> {
      return VcsTreeModelData.mapToVirtualFile(treeSelection);
    });
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      VirtualFile file = VcsTreeModelData.mapToNavigatableFile(treeSelection).single();
      return file != null && !file.isDirectory()
             ? PsiNavigationSupport.getInstance().createNavigatable(myProject, file, 0)
             : null;
    });
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY, () -> {
      return getNavigatableArray(myProject, VcsTreeModelData.mapToNavigatableFile(treeSelection));
    });
    sink.lazy(EXACTLY_SELECTED_FILES_DATA_KEY, () -> {
      return VcsTreeModelData.mapToExactVirtualFile(exactSelection);
    });

    if (RdLocalChanges.isEnabled()) {
      sink.set(VcsTransferableDataKeys.CHANGES_VIEW_SELECTION,
               ChangesViewSelection.create(VcsTreeModelData.selected(this).iterateRawNodes(), this));
    }
  }

  public @NotNull JBIterable<FilePath> getUnversionedFiles() {
    return VcsTreeModelData.allUnderTag(this, UNVERSIONED_FILES_TAG)
      .iterateUserObjects(FilePath.class);
  }

  public @NotNull JBIterable<FilePath> getSelectedUnversionedFiles() {
    return VcsTreeModelData.selectedUnderTag(this, UNVERSIONED_FILES_TAG)
      .iterateUserObjects(FilePath.class);
  }

  private @NotNull JBIterable<FilePath> getSelectedIgnoredFiles() {
    return VcsTreeModelData.selectedUnderTag(this, IGNORED_FILES_TAG)
      .iterateUserObjects(FilePath.class);
  }

  private @NotNull JBIterable<VirtualFile> getSelectedModifiedWithoutEditing() {
    return VcsTreeModelData.selectedUnderTag(this, MODIFIED_WITHOUT_EDITING_TAG)
      .iterateUserObjects(VirtualFile.class)
      .filter(VirtualFile::isValid);
  }

  public @NotNull JBIterable<Change> getSelectedChanges() {
    JBIterable<Change> changes = VcsTreeModelData.selected(this)
      .iterateUserObjects(Change.class);
    JBIterable<Change> hijackedChanges = getSelectedModifiedWithoutEditing()
      .map(file -> toHijackedChange(myProject, file))
      .filterNotNull();

    return changes.append(hijackedChanges);
  }

  public static @Nullable Change toHijackedChange(@NotNull Project project, @NotNull VirtualFile file) {
    return ChangesTreeCompatibilityProvider.getInstance().toHijackedChange(project, file);
  }

  private @NotNull JBIterable<LocallyDeletedChange> getSelectedLocallyDeletedChanges() {
    return VcsTreeModelData.selectedUnderTag(this, LOCALLY_DELETED_NODE_TAG)
      .iterateUserObjects(LocallyDeletedChange.class);
  }

  public @Nullable List<Change> getAllChangesFromSameChangelist(@NotNull Change change) {
    ChangesBrowserNode<?> node = findNodeInTree(change);
    if (node == null) return null;

    ChangesBrowserNode<?> parent;
    if (!ChangeListsViewModel.getInstance(myProject).getAreChangeListsEnabled().getValue()) {
      parent = getRoot();
    }
    else {
      parent = findParentOfType(node, ChangesBrowserChangeListNode.class);
    }
    if (parent == null) return null;

    return parent.traverseObjectsUnder()
      .filter(Change.class)
      .toList();
  }

  public @Nullable List<Change> getAllChangesFromSameAmendNode(@NotNull Change change) {
    ChangesBrowserNode<?> node = findNodeInTree(change);
    if (node == null) return null;

    ChangesBrowserNode<?> parent = findParentOfType(node, EditedCommitNode.class);
    if (parent == null) return null;

    return parent.traverseObjectsUnder()
      .filter(Change.class)
      .toList();
  }

  private static @Nullable ChangesBrowserNode<?> findParentOfType(@NotNull ChangesBrowserNode<?> node,
                                                                  @NotNull Class<? extends ChangesBrowserNode<?>> clazz) {
    ChangesBrowserNode<?> parent = node.getParent();
    while (parent != null) {
      if (clazz.isInstance(parent)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  public @NotNull JBIterable<ChangesBrowserChangeNode> getChangesNodes() {
    return VcsTreeModelData.all(this).iterateNodes().filter(ChangesBrowserChangeNode.class);
  }

  public @NotNull JBIterable<ChangesBrowserNode<?>> getSelectedChangesNodes() {
    return VcsTreeModelData.selected(this).iterateNodes();
  }

  @Override
  public void installPopupHandler(@NotNull ActionGroup group) {
    PopupHandler.installPopupMenu(this, group, ActionPlaces.CHANGES_VIEW_POPUP);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
  }

  @Override
  public boolean isOverSelection(final Point point) {
    return TreeUtil.isOverSelection(this, point);
  }

  @Override
  public void dropSelectionButUnderPoint(final Point point) {
    TreeUtil.dropSelectionButUnderPoint(this, point);
  }

  public @Nullable ChangesBrowserNode<?> findNodeInTree(@Nullable Object userObject) {
    return findNodeInTree(userObject, null);
  }

  public @Nullable ChangesBrowserNode<?> findNodeInTree(@Nullable Object userObject, @Nullable Object tag) {
    if (userObject instanceof LocalChangeList) {
      return getRoot().iterateNodeChildren()
        .find(node -> userObject.equals(node.getUserObject()));
    }

    ChangesBrowserNode<?> fromNode = tag != null ? VcsTreeModelData.findTagNode(this, tag) : getRoot();
    if (fromNode == null) return null;

    if (userObject instanceof ChangeListChange) {
      return VcsTreeModelData.allUnder(fromNode).iterateNodes()
        .find(node -> ChangeListChange.HASHING_STRATEGY.equals(node.getUserObject(), userObject));
    }
    else {
      return VcsTreeModelData.allUnder(fromNode).iterateNodes()
        .find(node -> Objects.equals(node.getUserObject(), userObject));
    }
  }

  public @Nullable TreePath findNodePathInTree(@Nullable Object userObject) {
    return findNodePathInTree(userObject, null);
  }

  public @Nullable TreePath findNodePathInTree(@Nullable Object userObject, @Nullable Object tag) {
    DefaultMutableTreeNode node = findNodeInTree(userObject, tag);
    return node != null ? TreeUtil.getPathFromRoot(node) : null;
  }

  /**
   * Expands node only if its child count is small enough.
   * As expanding node with large child count is a slow operation (and result is not very useful).
   */
  public void expandSafe(@NotNull DefaultMutableTreeNode node) {
    if (node.getChildCount() <= 10000) {
      expandPath(TreeUtil.getPathFromRoot(node));
    }
  }
}
