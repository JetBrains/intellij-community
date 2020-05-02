// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.UtilKt;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ChangesUtil.*;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.*;
import static com.intellij.util.containers.UtilKt.*;
import static com.intellij.vcs.commit.ChangesViewCommitPanelKt.subtreeRootObject;
import static java.util.stream.Collectors.toList;

// TODO: Check if we could extend DnDAwareTree here instead of directly implementing DnDAware
public class ChangesListView extends ChangesTree implements DataProvider, DnDAware {
  @NonNls public static final String HELP_ID = "ideaInterface.changes";
  @NonNls public static final DataKey<ChangesListView> DATA_KEY = DataKey.create("ChangeListView");
  @NonNls public static final DataKey<Stream<FilePath>> UNVERSIONED_FILE_PATHS_DATA_KEY = DataKey.create("ChangeListView.UnversionedFiles");
  @NonNls public static final DataKey<Stream<VirtualFile>> EXACTLY_SELECTED_FILES_DATA_KEY = DataKey.create("ChangeListView.ExactlySelectedFiles");
  @NonNls public static final DataKey<Stream<FilePath>> IGNORED_FILE_PATHS_DATA_KEY = DataKey.create("ChangeListView.IgnoredFiles");
  @NonNls public static final DataKey<List<FilePath>> MISSING_FILES_DATA_KEY = DataKey.create("ChangeListView.MissingFiles");
  @NonNls public static final DataKey<List<LocallyDeletedChange>> LOCALLY_DELETED_CHANGES = DataKey.create("ChangeListView.LocallyDeletedChanges");

  public ChangesListView(@NotNull Project project, boolean showCheckboxes) {
    super(project, showCheckboxes, true);

    setDragEnabled(true);
  }

  @NotNull
  @Override
  protected ChangesGroupingSupport installGroupingSupport() {
    return new ChangesGroupingSupport(myProject, this, true);
  }

  @Override
  public int getToggleClickCount() {
    return 2;
  }

  @Override
  protected boolean isInclusionVisible(@NotNull ChangesBrowserNode<?> node) {
    Object subtreeRootObject = subtreeRootObject(node);

    if (subtreeRootObject instanceof LocalChangeList) return !((LocalChangeList)subtreeRootObject).getChanges().isEmpty();
    if (subtreeRootObject == UNVERSIONED_FILES_TAG) return true;
    return false;
  }

  @Override
  public DefaultTreeModel getModel() {
    return (DefaultTreeModel)super.getModel();
  }

  public void updateModel(@NotNull DefaultTreeModel newModel) {
    TreeState state = TreeState.createOn(this, getRoot());
    state.setScrollToSelection(false);
    ChangesBrowserNode<?> oldRoot = getRoot();
    setModel(newModel);
    ChangesBrowserNode<?> newRoot = getRoot();
    state.applyTo(this, newRoot);

    initTreeStateIfNeeded(oldRoot, newRoot);
  }

  @Override
  public void rebuildTree() {
    // currently not used in ChangesListView code flow
  }

  private void initTreeStateIfNeeded(ChangesBrowserNode<?> oldRoot, ChangesBrowserNode<?> newRoot) {
    ChangesBrowserNode<?> defaultListNode = getDefaultChangelistNode(newRoot);
    if (defaultListNode == null) return;

    if (getSelectionCount() == 0) {
      TreeUtil.selectNode(this, defaultListNode);
    }

    if (oldRoot.getFileCount() == 0 && TreeUtil.collectExpandedPaths(this).size() == 0) {
      expandSafe(defaultListNode);
    }
  }

  @Nullable
  private static ChangesBrowserNode<?> getDefaultChangelistNode(@NotNull ChangesBrowserNode<?> root) {
    @SuppressWarnings({"unchecked", "rawtypes"})
    Enumeration<ChangesBrowserNode<?>> children = (Enumeration)root.children();
    Iterator<ChangesBrowserNode<?>> nodes = ContainerUtil.iterate(children);
    return ContainerUtil.find(nodes, node -> {
      if (node instanceof ChangesBrowserChangeListNode) {
        ChangeList list = ((ChangesBrowserChangeListNode)node).getUserObject();
        return list instanceof LocalChangeList && ((LocalChangeList)list).isDefault();
      }
      return false;
    });
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (DATA_KEY.is(dataId)) {
      return this;
    }
    if (VcsDataKeys.CHANGES.is(dataId)) {
      return getSelectedChanges().toArray(Change[]::new);
    }
    if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      return getLeadSelection().toArray(Change[]::new);
    }
    if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      return getSelectedChangeLists().toArray(ChangeList[]::new);
    }
    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      return getSelectedFiles().toArray(VirtualFile[]::new);
    }
    if (VcsDataKeys.VIRTUAL_FILE_STREAM.is(dataId)) {
      return getSelectedFiles();
    }
    if (VcsDataKeys.FILE_PATH_STREAM.is(dataId)) {
      return getSelectedFilePaths();
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      VirtualFile file = getIfSingle(getNavigatableFiles());
      return file != null && !file.isDirectory() ? PsiNavigationSupport.getInstance()
                                                                       .createNavigatable(myProject, file, 0) : null;
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getNavigatableArray(myProject, getNavigatableFiles());
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return getSelectionObjectsStream().anyMatch(userObject -> !(userObject instanceof ChangeList))
             ? new VirtualFileDeleteProvider()
             : null;
    }
    if (UNVERSIONED_FILE_PATHS_DATA_KEY.is(dataId)) {
      return getSelectedUnversionedFiles();
    }
    if (EXACTLY_SELECTED_FILES_DATA_KEY.is(dataId)) {
      return getExactlySelectedVirtualFiles(this);
    }
    if (IGNORED_FILE_PATHS_DATA_KEY.is(dataId)) {
      return getSelectedIgnoredFiles();
    }
    if (VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY.is(dataId)) {
      return getSelectedModifiedWithoutEditing().collect(toList());
    }
    if (LOCALLY_DELETED_CHANGES.is(dataId)) {
      return getSelectedLocallyDeletedChanges().collect(toList());
    }
    if (MISSING_FILES_DATA_KEY.is(dataId)) {
      return getSelectedMissingFiles().collect(toList());
    }
    if (VcsDataKeys.HAVE_LOCALLY_DELETED.is(dataId)) {
      return getSelectedMissingFiles().findAny().isPresent();
    }
    if (VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING.is(dataId)) {
      return getSelectedModifiedWithoutEditing().findAny().isPresent();
    }
    if (VcsDataKeys.HAVE_SELECTED_CHANGES.is(dataId)) {
      return !UtilKt.isEmpty(getSelectedChanges());
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  @NotNull
  public Stream<FilePath> getUnversionedFiles() {
    @SuppressWarnings({"unchecked", "rawtypes"})
    Enumeration<ChangesBrowserNode<?>> nodes = (Enumeration)getRoot().children();
    ChangesBrowserUnversionedFilesNode node = ContainerUtil.findInstance(ContainerUtil.iterate(nodes),
                                                                         ChangesBrowserUnversionedFilesNode.class);
    if (node == null) return Stream.empty();
    return node.getFilePathsUnderStream();
  }

  @NotNull
  static Stream<FilePath> getSelectedUnversionedFiles(@NotNull JTree tree) {
    return getSelectedFilePaths(tree, UNVERSIONED_FILES_TAG);
  }

  @NotNull
  public Stream<FilePath> getSelectedUnversionedFiles() {
    return getSelectedUnversionedFiles(this);
  }

  @NotNull
  private Stream<FilePath> getSelectedIgnoredFiles() {
    return getSelectedFilePaths(IGNORED_FILES_TAG);
  }

  @NotNull
  private Stream<VirtualFile> getSelectedModifiedWithoutEditing() {
    return getSelectedVirtualFiles(MODIFIED_WITHOUT_EDITING_TAG);
  }

  @NotNull
  protected Stream<VirtualFile> getSelectedVirtualFiles(@Nullable Object tag) {
    return getSelectionNodesStream(this, tag)
      .flatMap(ChangesBrowserNode::getFilesUnderStream)
      .distinct();
  }

  @NotNull
  protected Stream<FilePath> getSelectedFilePaths(@Nullable Object tag) {
    return getSelectedFilePaths(this, tag);
  }

  @NotNull
  private static Stream<FilePath> getSelectedFilePaths(@NotNull JTree tree, @Nullable Object tag) {
    return getSelectionNodesStream(tree, tag)
      .flatMap(ChangesBrowserNode::getFilePathsUnderStream)
      .distinct();
  }

  @NotNull
  static Stream<VirtualFile> getExactlySelectedVirtualFiles(@NotNull JTree tree) {
    VcsTreeModelData exactlySelected = VcsTreeModelData.exactlySelected(tree);

    return exactlySelected.rawUserObjectsStream().map(object -> {
      if (object instanceof VirtualFile) return (VirtualFile)object;
      if (object instanceof FilePath) return ((FilePath)object).getVirtualFile();
      return null;
    }).filter(Objects::nonNull);
  }

  @NotNull
  private Stream<ChangesBrowserNode<?>> getSelectionNodesStream() {
    return getSelectionNodesStream(this, null);
  }

  @NotNull
  private static Stream<ChangesBrowserNode<?>> getSelectionNodesStream(@NotNull JTree tree, @Nullable Object tag) {
    return stream(tree.getSelectionPaths())
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node));
  }

  @NotNull
  private Stream<Object> getSelectionObjectsStream() {
    return getSelectionNodesStream().map(ChangesBrowserNode::getUserObject);
  }

  @NotNull
  static Stream<VirtualFile> getVirtualFiles(TreePath @Nullable [] paths, @Nullable Object tag) {
    return stream(paths)
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(ChangesBrowserNode::getFilesUnderStream)
      .distinct();
  }

  @NotNull
  static Stream<FilePath> getFilePaths(TreePath @Nullable [] paths, @Nullable Object tag) {
    return stream(paths)
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(ChangesBrowserNode::getFilePathsUnderStream)
      .distinct();
  }

  static boolean isUnderTag(@NotNull TreePath path, @Nullable Object tag) {
    boolean result = true;

    if (tag != null) {
      result = path.getPathCount() > 1 && ((ChangesBrowserNode<?>)path.getPathComponent(1)).getUserObject() == tag;
    }

    return result;
  }

  @NotNull
  static Stream<Change> getChanges(@NotNull Project project, TreePath @Nullable [] paths) {
    Stream<Change> changes = stream(paths)
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(node -> node.getObjectsUnderStream(Change.class))
      .map(Change.class::cast);
    Stream<Change> hijackedChanges = getVirtualFiles(paths, MODIFIED_WITHOUT_EDITING_TAG)
      .map(file -> toHijackedChange(project, file))
      .filter(Objects::nonNull);

    return Stream.concat(changes, hijackedChanges)
      .filter(new DistinctChangePredicate());
  }

  @Nullable
  private static Change toHijackedChange(@NotNull Project project, @NotNull VirtualFile file) {
    VcsCurrentRevisionProxy before = VcsCurrentRevisionProxy.create(file, project);
    if (before != null) {
      ContentRevision afterRevision = new CurrentContentRevision(VcsUtil.getFilePath(file));
      return new Change(before, afterRevision, FileStatus.HIJACKED);
    }
    return null;
  }

  @NotNull
  private Stream<LocallyDeletedChange> getSelectedLocallyDeletedChanges() {
    return getSelectionNodesStream(this, LOCALLY_DELETED_NODE_TAG)
      .flatMap(node -> node.getObjectsUnderStream(LocallyDeletedChange.class))
      .distinct();
  }

  @NotNull
  private Stream<FilePath> getSelectedMissingFiles() {
    return getSelectedLocallyDeletedChanges().map(LocallyDeletedChange::getPath);
  }

  @NotNull
  private Stream<FilePath> getSelectedFilePaths() {
    return concat(
      getSelectedChanges().map(ChangesUtil::getFilePath),
      getSelectedVirtualFiles(null).map(VcsUtil::getFilePath),
      getSelectedFilePaths(null)
    ).distinct();
  }

  @NotNull
  private Stream<VirtualFile> getSelectedFiles() {
    return concat(
      getAfterRevisionsFiles(getSelectedChanges()),
      getSelectedVirtualFiles(null),
      getFilesFromPaths(getSelectedFilePaths(null))
    ).distinct();
  }

  @NotNull
  private Stream<VirtualFile> getNavigatableFiles() {
    return concat(
      getFiles(getSelectedChanges()),
      getSelectedVirtualFiles(null),
      getFilesFromPaths(getSelectedFilePaths(null))
    ).distinct();
  }

  @NotNull
  private Stream<Change> getLeadSelection() {
    return getSelectionNodesStream()
      .filter(node -> node instanceof ChangesBrowserChangeNode)
      .map(ChangesBrowserChangeNode.class::cast)
      .map(ChangesBrowserChangeNode::getUserObject)
      .filter(new DistinctChangePredicate());
  }

  @NotNull
  public Stream<Change> getChanges() {
    return getRoot().getObjectsUnderStream(Change.class);
  }

  @Nullable
  public List<Change> getAllChangesFromSameChangelist(@NotNull Change change) {
    DefaultMutableTreeNode node = findNodeInTree(change);
    while (node != null) {
      if (node instanceof ChangesBrowserChangeListNode) {
        return ((ChangesBrowserChangeListNode)node).getAllChangesUnder();
      }
      if (node == getRoot() && Registry.is("vcs.skip.single.default.changelist")) {
        return getRoot().getAllChangesUnder();
      }
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  @NotNull
  public Stream<Change> getSelectedChanges() {
    return getChanges(myProject, getSelectionPaths());
  }

  @NotNull
  private Stream<ChangeList> getSelectedChangeLists() {
    return getSelectionObjectsStream()
      .filter(userObject -> userObject instanceof ChangeList)
      .map(ChangeList.class::cast)
      .distinct();
  }

  @Override
  public void installPopupHandler(@NotNull ActionGroup group) {
    PopupHandler.installPopupHandler(this, group, ActionPlaces.CHANGES_VIEW_POPUP, ActionManager.getInstance());
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void processMouseEvent(final MouseEvent e) {
    if (MouseEvent.MOUSE_RELEASED == e.getID() && !isSelectionEmpty() && !e.isShiftDown() && !e.isControlDown()  &&
        !e.isMetaDown() && !e.isPopupTrigger()) {
      if (isOverSelection(e.getPoint())) {
        clearSelection();
        final TreePath path = getPathForLocation(e.getPoint().x, e.getPoint().y);
        if (path != null) {
          setSelectionPath(path);
        }
      }
    }


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
  public DefaultMutableTreeNode findNodeInTree(Object userObject) {
    if (userObject instanceof LocalChangeList) {
      return TreeUtil.nodeChildren(getRoot()).filter(DefaultMutableTreeNode.class).find(node -> userObject.equals(node.getUserObject()));
    }
    if (userObject instanceof ChangeListChange) {
      return TreeUtil.findNode(getRoot(), node -> ChangeListChange.HASHING_STRATEGY.equals(node.getUserObject(), userObject));
    }
    return TreeUtil.findNodeWithObject(getRoot(), userObject);
  }

  @Nullable
  public TreePath findNodePathInTree(Object userObject) {
    DefaultMutableTreeNode node = findNodeInTree(userObject);
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

  private static class DistinctChangePredicate implements Predicate<Change> {
    private final Set<Object> seen = new THashSet<>(ChangeListChange.HASHING_STRATEGY);

    @Override
    public boolean test(Change change) {
      return seen.add(change);
    }
  }
}