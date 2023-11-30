// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcs.commit.EditedCommitNode;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.*;

// TODO: Check if we could extend DnDAwareTree here instead of directly implementing DnDAware
public abstract class ChangesListView extends ChangesTree implements DataProvider, DnDAware {
  private static final Logger LOG = Logger.getInstance(ChangesListView.class);

  @NonNls public static final String HELP_ID = "ideaInterface.changes";
  @NonNls public static final DataKey<ChangesListView> DATA_KEY
    = DataKey.create("ChangeListView");
  @NonNls public static final DataKey<Iterable<FilePath>> UNVERSIONED_FILE_PATHS_DATA_KEY
    = DataKey.create("ChangeListView.UnversionedFiles");
  @NonNls public static final DataKey<Iterable<VirtualFile>> EXACTLY_SELECTED_FILES_DATA_KEY
    = DataKey.create("ChangeListView.ExactlySelectedFiles");
  @NonNls public static final DataKey<Iterable<FilePath>> IGNORED_FILE_PATHS_DATA_KEY
    = DataKey.create("ChangeListView.IgnoredFiles");
  @NonNls public static final DataKey<List<FilePath>> MISSING_FILES_DATA_KEY
    = DataKey.create("ChangeListView.MissingFiles");
  @NonNls public static final DataKey<List<LocallyDeletedChange>> LOCALLY_DELETED_CHANGES
    = DataKey.create("ChangeListView.LocallyDeletedChanges");

  private boolean myBusy = false;

  public ChangesListView(@NotNull Project project, boolean showCheckboxes) {
    super(project, showCheckboxes, true);
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

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (DATA_KEY.is(dataId)) {
      return this;
    }
    if (VcsDataKeys.CHANGES.is(dataId)) {
      return getSelectedChanges()
        .toArray(Change.EMPTY_CHANGE_ARRAY);
    }
    if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      return VcsTreeModelData.exactlySelected(this)
        .iterateUserObjects(Change.class)
        .toArray(Change.EMPTY_CHANGE_ARRAY);
    }
    if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      return VcsTreeModelData.exactlySelected(this)
        .iterateRawUserObjects(ChangeList.class)
        .toList().toArray(ChangeList[]::new);
    }
    if (VcsDataKeys.FILE_PATHS.is(dataId)) {
      return VcsTreeModelData.mapToFilePath(VcsTreeModelData.selected(this));
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      // don't try to delete files when only a changelist node is selected
      boolean hasSelection = VcsTreeModelData.exactlySelected(this)
        .iterateRawUserObjects()
        .filter(userObject -> !(userObject instanceof ChangeList))
        .isNotEmpty();
      return hasSelection
             ? new VirtualFileDeleteProvider()
             : null;
    }
    if (UNVERSIONED_FILE_PATHS_DATA_KEY.is(dataId)) {
      return getSelectedUnversionedFiles();
    }
    if (IGNORED_FILE_PATHS_DATA_KEY.is(dataId)) {
      return getSelectedIgnoredFiles();
    }
    if (VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY.is(dataId)) {
      return getSelectedModifiedWithoutEditing().toList();
    }
    if (LOCALLY_DELETED_CHANGES.is(dataId)) {
      return getSelectedLocallyDeletedChanges().toList();
    }
    if (MISSING_FILES_DATA_KEY.is(dataId)) {
      return getSelectedLocallyDeletedChanges()
        .map(LocallyDeletedChange::getPath)
        .toList();
    }
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      DataProvider superProvider = (DataProvider)super.getData(dataId);
      VcsTreeModelData treeSelection = VcsTreeModelData.selected(this);
      VcsTreeModelData exactSelection = VcsTreeModelData.exactlySelected(this);
      return CompositeDataProvider.compose(slowId -> getSlowData(myProject, treeSelection, exactSelection, slowId), superProvider);
    }
    return super.getData(dataId);
  }

  @Nullable
  private static Object getSlowData(@NotNull Project project,
                                    @NotNull VcsTreeModelData treeSelection,
                                    @NotNull VcsTreeModelData exactSelection,
                                    @NotNull String slowId) {
    if (SelectInContext.DATA_KEY.is(slowId)) {
      VirtualFile file = VcsTreeModelData.mapObjectToVirtualFile(exactSelection.iterateRawUserObjects()).first();
      if (file == null) return null;
      return new FileSelectInContext(project, file, null);
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(slowId)) {
      return VcsTreeModelData.mapToVirtualFile(treeSelection)
        .toArray(VirtualFile.EMPTY_ARRAY);
    }
    if (VcsDataKeys.VIRTUAL_FILES.is(slowId)) {
      return VcsTreeModelData.mapToVirtualFile(treeSelection);
    }
    if (CommonDataKeys.NAVIGATABLE.is(slowId)) {
      VirtualFile file = VcsTreeModelData.mapToNavigatableFile(treeSelection).single();
      return file != null && !file.isDirectory()
             ? PsiNavigationSupport.getInstance().createNavigatable(project, file, 0)
             : null;
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(slowId)) {
      return getNavigatableArray(project, VcsTreeModelData.mapToNavigatableFile(treeSelection));
    }
    if (EXACTLY_SELECTED_FILES_DATA_KEY.is(slowId)) {
      return VcsTreeModelData.mapToExactVirtualFile(exactSelection);
    }
    return null;
  }

  @NotNull
  public JBIterable<FilePath> getUnversionedFiles() {
    return VcsTreeModelData.allUnderTag(this, UNVERSIONED_FILES_TAG)
      .iterateUserObjects(FilePath.class);
  }

  @NotNull
  public JBIterable<FilePath> getSelectedUnversionedFiles() {
    return VcsTreeModelData.selectedUnderTag(this, UNVERSIONED_FILES_TAG)
      .iterateUserObjects(FilePath.class);
  }

  @NotNull
  private JBIterable<FilePath> getSelectedIgnoredFiles() {
    return VcsTreeModelData.selectedUnderTag(this, IGNORED_FILES_TAG)
      .iterateUserObjects(FilePath.class);
  }

  @NotNull
  private JBIterable<VirtualFile> getSelectedModifiedWithoutEditing() {
    return VcsTreeModelData.selectedUnderTag(this, MODIFIED_WITHOUT_EDITING_TAG)
      .iterateUserObjects(VirtualFile.class)
      .filter(VirtualFile::isValid);
  }

  @NotNull
  public JBIterable<Change> getSelectedChanges() {
    JBIterable<Change> changes = VcsTreeModelData.selected(this)
      .iterateUserObjects(Change.class);
    JBIterable<Change> hijackedChanges = getSelectedModifiedWithoutEditing()
      .map(file -> toHijackedChange(myProject, file))
      .filterNotNull();

    return changes.append(hijackedChanges);
  }

  @Nullable
  public static Change toHijackedChange(@NotNull Project project, @NotNull VirtualFile file) {
    VcsCurrentRevisionProxy before = VcsCurrentRevisionProxy.create(file, project);
    if (before != null) {
      ContentRevision afterRevision = new CurrentContentRevision(VcsUtil.getFilePath(file));
      return new Change(before, afterRevision, FileStatus.HIJACKED);
    }
    return null;
  }

  @NotNull
  private JBIterable<LocallyDeletedChange> getSelectedLocallyDeletedChanges() {
    return VcsTreeModelData.selectedUnderTag(this, LOCALLY_DELETED_NODE_TAG)
      .iterateUserObjects(LocallyDeletedChange.class);
  }

  @Nullable
  public List<Change> getAllChangesFromSameChangelist(@NotNull Change change) {
    ChangesBrowserNode<?> node = findNodeInTree(change);
    if (node == null) return null;

    ChangesBrowserNode<?> parent;
    if (Registry.is("vcs.skip.single.default.changelist") ||
        !ChangeListManager.getInstance(myProject).areChangeListsEnabled()) {
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

  @Nullable
  public List<Change> getAllChangesFromSameAmendNode(@NotNull Change change) {
    ChangesBrowserNode<?> node = findNodeInTree(change);
    if (node == null) return null;

    ChangesBrowserNode<?> parent = findParentOfType(node, EditedCommitNode.class);
    if (parent == null) return null;

    return parent.traverseObjectsUnder()
      .filter(Change.class)
      .toList();
  }

  @Nullable
  private static ChangesBrowserNode<?> findParentOfType(@NotNull ChangesBrowserNode<?> node,
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

  @NotNull
  public JBIterable<ChangesBrowserChangeNode> getChangesNodes() {
    return VcsTreeModelData.all(this).iterateNodes().filter(ChangesBrowserChangeNode.class);
  }

  @NotNull
  public JBIterable<ChangesBrowserNode<?>> getSelectedChangesNodes() {
    return VcsTreeModelData.selected(this).iterateNodes();
  }

  @Override
  public void installPopupHandler(@NotNull ActionGroup group) {
    PopupHandler.installPopupMenu(this, group, ActionPlaces.CHANGES_VIEW_POPUP);
  }

  @Override
  @NotNull
  public JComponent getComponent() {
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

  @Nullable
  public ChangesBrowserNode<?> findNodeInTree(@Nullable Object userObject) {
    return findNodeInTree(userObject, null);
  }

  @Nullable
  public ChangesBrowserNode<?> findNodeInTree(@Nullable Object userObject, @Nullable Object tag) {
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

  @Nullable
  public TreePath findNodePathInTree(@Nullable Object userObject) {
    return findNodePathInTree(userObject, null);
  }

  @Nullable
  public TreePath findNodePathInTree(@Nullable Object userObject, @Nullable Object tag) {
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