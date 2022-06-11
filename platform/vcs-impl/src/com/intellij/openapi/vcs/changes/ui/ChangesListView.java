// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcs.commit.EditedCommitNode;
import com.intellij.vcsUtil.VcsUtil;
import one.util.streamex.StreamEx;
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
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getPathsCaseSensitive;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.*;
import static com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.findTagNode;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.vcs.commit.ChangesViewCommitPanelKt.subtreeRootObject;

// TODO: Check if we could extend DnDAwareTree here instead of directly implementing DnDAware
public class ChangesListView extends HoverChangesTree implements DataProvider, DnDAware {
  @NonNls public static final String HELP_ID = "ideaInterface.changes";
  @NonNls public static final DataKey<ChangesListView> DATA_KEY = DataKey.create("ChangeListView");
  @NonNls public static final DataKey<Iterable<FilePath>> UNVERSIONED_FILE_PATHS_DATA_KEY = DataKey.create("ChangeListView.UnversionedFiles");
  @NonNls public static final DataKey<Iterable<VirtualFile>> EXACTLY_SELECTED_FILES_DATA_KEY = DataKey.create("ChangeListView.ExactlySelectedFiles");
  @NonNls public static final DataKey<Iterable<FilePath>> IGNORED_FILE_PATHS_DATA_KEY = DataKey.create("ChangeListView.IgnoredFiles");
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

  @Nullable
  @Override
  public HoverIcon getHoverIcon(@NotNull ChangesBrowserNode<?> node) {
    return null;
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
      return getSelectedChanges().toList().toArray(Change[]::new);
    }
    if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      return getLeadSelection().toList().toArray(Change[]::new);
    }
    if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      return getSelectedChangeLists().toList().toArray(ChangeList[]::new);
    }
    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      return getSelectedFiles().toList().toArray(VirtualFile[]::new);
    }
    if (VcsDataKeys.VIRTUAL_FILES.is(dataId)) {
      return getSelectedFiles();
    }
    if (VcsDataKeys.FILE_PATHS.is(dataId)) {
      return getSelectedFilePaths();
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      VirtualFile file = getNavigatableFiles().single();
      return file != null && !file.isDirectory() ? PsiNavigationSupport.getInstance()
                                                                       .createNavigatable(myProject, file, 0) : null;
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getNavigatableArray(myProject, StreamEx.of(getNavigatableFiles().iterator()));
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return getSelectionObjects().find(userObject -> !(userObject instanceof ChangeList)) != null
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
      return getSelectedModifiedWithoutEditing().toList();
    }
    if (LOCALLY_DELETED_CHANGES.is(dataId)) {
      return getSelectedLocallyDeletedChanges().toList();
    }
    if (MISSING_FILES_DATA_KEY.is(dataId)) {
      return getSelectedMissingFiles().toList();
    }
    if (VcsDataKeys.HAVE_LOCALLY_DELETED.is(dataId)) {
      return getSelectedMissingFiles().isNotEmpty();
    }
    if (VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING.is(dataId)) {
      return getSelectedModifiedWithoutEditing().isNotEmpty();
    }
    if (VcsDataKeys.HAVE_SELECTED_CHANGES.is(dataId)) {
      return getSelectedChanges().isNotEmpty();
    }
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  @NotNull
  public Stream<FilePath> getUnversionedFiles() {
    ChangesBrowserUnversionedFilesNode node = TreeUtil.nodeChildren(getRoot())
      .filter(ChangesBrowserUnversionedFilesNode.class).first();
    if (node == null) return StreamEx.empty();
    return node.getFilePathsUnderStream();
  }

  @NotNull
  static JBIterable<FilePath> getSelectedUnversionedFiles(@NotNull JTree tree) {
    return getSelectedFilePaths(tree, UNVERSIONED_FILES_TAG);
  }

  @NotNull
  public JBIterable<FilePath> getSelectedUnversionedFiles() {
    return getSelectedUnversionedFiles(this);
  }

  @NotNull
  private JBIterable<FilePath> getSelectedIgnoredFiles() {
    return getSelectedFilePaths(IGNORED_FILES_TAG);
  }

  @NotNull
  private JBIterable<VirtualFile> getSelectedModifiedWithoutEditing() {
    return getSelectedVirtualFiles(MODIFIED_WITHOUT_EDITING_TAG);
  }

  @NotNull
  protected JBIterable<VirtualFile> getSelectedVirtualFiles(@Nullable Object tag) {
    return getSelectionNodes(this, tag)
      .flatMap(node -> JBIterable.create(() -> node.getFilesUnderStream().iterator()))
      .unique();
  }

  @NotNull
  protected JBIterable<FilePath> getSelectedFilePaths(@Nullable Object tag) {
    return getSelectedFilePaths(this, tag);
  }

  @NotNull
  private static JBIterable<FilePath> getSelectedFilePaths(@NotNull JTree tree, @Nullable Object tag) {
    return getSelectionNodes(tree, tag)
      .flatMap(node -> JBIterable.create(() -> node.getFilePathsUnderStream().iterator()))
      .unique();
  }

  @NotNull
  static JBIterable<VirtualFile> getExactlySelectedVirtualFiles(@NotNull JTree tree) {
    VcsTreeModelData exactlySelected = VcsTreeModelData.exactlySelected(tree);

    return JBIterable.create(() -> exactlySelected.rawUserObjectsStream().iterator()).map(object -> {
      if (object instanceof VirtualFile) return (VirtualFile)object;
      if (object instanceof FilePath) return ((FilePath)object).getVirtualFile();
      return null;
    }).filter(Objects::nonNull);
  }

  @NotNull
  private JBIterable<ChangesBrowserNode<?>> getSelectionNodes() {
    return getSelectionNodes(this, null);
  }

  @NotNull
  private static JBIterable<ChangesBrowserNode<?>> getSelectionNodes(@NotNull JTree tree, @Nullable Object tag) {
    return JBIterable.of(tree.getSelectionPaths())
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node));
  }

  @NotNull
  private JBIterable<Object> getSelectionObjects() {
    return getSelectionNodes().map(ChangesBrowserNode::getUserObject);
  }

  @NotNull
  static JBIterable<VirtualFile> getVirtualFiles(TreePath @Nullable [] paths, @Nullable Object tag) {
    return JBIterable.of(paths)
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(node -> JBIterable.create(() -> node.getFilesUnderStream().iterator()))
      .unique();
  }

  @NotNull
  static JBIterable<FilePath> getFilePaths(TreePath @Nullable [] paths, @Nullable Object tag) {
    return JBIterable.of(paths)
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(node -> JBIterable.create(() -> node.getFilePathsUnderStream().iterator()))
      .unique();
  }

  static boolean isUnderTag(@NotNull TreePath path, @Nullable Object tag) {
    boolean result = true;

    if (tag != null) {
      result = path.getPathCount() > 1 && ((ChangesBrowserNode<?>)path.getPathComponent(1)).getUserObject() == tag;
    }

    return result;
  }

  @NotNull
  static JBIterable<Change> getChanges(@NotNull Project project, TreePath @Nullable [] paths) {
    JBIterable<Change> changes = JBIterable.of(paths)
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(node -> node.traverseObjectsUnder())
      .filter(Change.class);
    JBIterable<Change> hijackedChanges = getVirtualFiles(paths, MODIFIED_WITHOUT_EDITING_TAG)
      .map(file -> toHijackedChange(project, file))
      .filter(Objects::nonNull);

    return changes.append(hijackedChanges)
      .filter(new DistinctChangePredicate());
  }

  @NotNull
  static JBIterable<ChangesBrowserNode<?>> getChangesNodes(TreePath @Nullable [] paths) {
    return JBIterable.of(paths)
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(node -> node.traverse())
      .unique();
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
    return getSelectionNodes(this, LOCALLY_DELETED_NODE_TAG)
      .flatMap(node -> node.traverseObjectsUnder())
      .filter(LocallyDeletedChange.class)
      .unique();
  }

  @NotNull
  private JBIterable<FilePath> getSelectedMissingFiles() {
    return getSelectedLocallyDeletedChanges().map(LocallyDeletedChange::getPath);
  }

  @NotNull
  private JBIterable<FilePath> getSelectedFilePaths() {
    return JBIterable.<FilePath>empty()
      .append(getSelectedChanges().map(ChangesUtil::getFilePath))
      .append(getSelectedVirtualFiles(null).map(VcsUtil::getFilePath))
      .append(getSelectedFilePaths(null))
      .unique();
  }

  @NotNull
  private JBIterable<VirtualFile> getSelectedFiles() {
    return JBIterable.<VirtualFile>empty()
      .append(getSelectedChanges().filterMap(ChangesUtil::getAfterPath).filterMap(FilePath::getVirtualFile))
      .append(getSelectedVirtualFiles(null))
      .append(getSelectedFilePaths(null).filterMap(FilePath::getVirtualFile))
      .unique();
  }

  @NotNull
  private JBIterable<VirtualFile> getNavigatableFiles() {
    return JBIterable.<VirtualFile>empty()
      .append(getSelectedChanges().flatMap(o -> JBIterable.create(() -> getPathsCaseSensitive(o).iterator())).filterMap(FilePath::getVirtualFile))
      .append(getSelectedVirtualFiles(null))
      .append(getSelectedFilePaths(null).filterMap(FilePath::getVirtualFile))
      .unique();
  }

  @NotNull
  private JBIterable<Change> getLeadSelection() {
    return getSelectionNodes()
      .filter(node -> node instanceof ChangesBrowserChangeNode)
      .map(ChangesBrowserChangeNode.class::cast)
      .map(ChangesBrowserChangeNode::getUserObject)
      .filter(new DistinctChangePredicate());
  }

  @NotNull
  public JBIterable<Change> getChanges() {
    return getRoot().traverseObjectsUnder().filter(Change.class);
  }

  @NotNull
  public JBIterable<ChangesBrowserChangeNode> getChangesNodes() {
    return TreeUtil.treeNodeTraverser(getRoot()).traverse().filter(ChangesBrowserChangeNode.class);
  }

  @Nullable
  public List<Change> getAllChangesFromSameChangelist(@NotNull Change change) {
    return getAllChangesUnder(change, ChangesBrowserChangeListNode.class);
  }

  @Nullable
  public List<Change> getAllChangesFromSameAmendNode(@NotNull Change change) {
    return getAllChangesUnder(change, EditedCommitNode.class);
  }

  @SafeVarargs
  @Nullable
  public final List<Change> getAllChangesUnder(@NotNull Change change, Class<? extends ChangesBrowserNode<?>> @NotNull ... nodeClasses) {
    DefaultMutableTreeNode node = findNodeInTree(change);
    boolean changeListNodeRequested = ArrayUtil.contains(ChangesBrowserChangeListNode.class, nodeClasses);

    while (node != null) {
      if (ArrayUtil.contains(node.getClass(), nodeClasses)) {
        return ((ChangesBrowserNode<?>)node).getAllChangesUnder();
      }
      if (node == getRoot()) {
        if (changeListNodeRequested && (Registry.is("vcs.skip.single.default.changelist") ||
                                        !ChangeListManager.getInstance(myProject).areChangeListsEnabled())) {
          return getRoot().getAllChangesUnder();
        }
      }
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  @NotNull
  public JBIterable<Change> getSelectedChanges() {
    return getChanges(myProject, getSelectionPaths());
  }

  @NotNull
  public JBIterable<ChangesBrowserNode<?>> getSelectedChangesNodes() {
    return getChangesNodes(getSelectionPaths());
  }

  @NotNull
  private JBIterable<ChangeList> getSelectedChangeLists() {
    return getSelectionObjects()
      .filter(userObject -> userObject instanceof ChangeList)
      .map(ChangeList.class::cast)
      .unique();
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
  public void processMouseEvent(final MouseEvent e) {
    if (MouseEvent.MOUSE_RELEASED == e.getID() && !isSelectionEmpty() && !e.isShiftDown() && !e.isControlDown() &&
        !e.isMetaDown() && !e.isPopupTrigger()) {
      if (isOverSelection(e.getPoint())) {
        TreePath path = getPathForLocation(e.getPoint().x, e.getPoint().y);
        setSelectionPath(path);
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
  public DefaultMutableTreeNode findNodeInTree(@Nullable Object userObject) {
    return findNodeInTree(userObject, null);
  }

  @Nullable
  public DefaultMutableTreeNode findNodeInTree(@Nullable Object userObject, @Nullable Object tag) {
    DefaultMutableTreeNode fromNode = tag != null ? notNull(findTagNode(this, tag), getRoot()) : getRoot();

    if (userObject instanceof LocalChangeList) {
      return TreeUtil.nodeChildren(fromNode).filter(DefaultMutableTreeNode.class).find(node -> userObject.equals(node.getUserObject()));
    }
    if (userObject instanceof ChangeListChange) {
      return TreeUtil.findNode(fromNode, node -> ChangeListChange.HASHING_STRATEGY.equals(node.getUserObject(), userObject));
    }
    return TreeUtil.findNodeWithObject(fromNode, userObject);
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

  private static class DistinctChangePredicate extends JBIterable.SCond<Change> {
    private Set<Object> seen;

    @Override
    public boolean value(Change change) {
      if (seen == null) seen = CollectionFactory.createCustomHashingStrategySet(ChangeListChange.HASHING_STRATEGY);
      return seen.add(change);
    }
  }
}