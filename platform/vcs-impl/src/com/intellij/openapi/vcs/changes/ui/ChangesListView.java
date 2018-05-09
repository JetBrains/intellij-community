// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getAfterRevisionsFiles;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getFiles;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.*;
import static com.intellij.util.containers.UtilKt.getIfSingle;
import static com.intellij.util.containers.UtilKt.stream;
import static java.util.stream.Collectors.toList;

// TODO: Check if we could extend DnDAwareTree here instead of directly implementing DnDAware
public class ChangesListView extends Tree implements TypeSafeDataProvider, DnDAware {
  private final Project myProject;
  private final CopyProvider myCopyProvider;
  @NotNull private final ChangesGroupingSupport myGroupingSupport;

  @NonNls public static final String HELP_ID = "ideaInterface.changes";
  @NonNls public static final DataKey<ChangesListView> DATA_KEY = DataKey.create("ChangeListView");
  @NonNls public static final DataKey<Stream<VirtualFile>> UNVERSIONED_FILES_DATA_KEY = DataKey.create("ChangeListView.UnversionedFiles");
  @NonNls public static final DataKey<Stream<VirtualFile>> IGNORED_FILES_DATA_KEY = DataKey.create("ChangeListView.IgnoredFiles");
  @NonNls public static final DataKey<List<FilePath>> MISSING_FILES_DATA_KEY = DataKey.create("ChangeListView.MissingFiles");
  @NonNls public static final DataKey<List<LocallyDeletedChange>> LOCALLY_DELETED_CHANGES = DataKey.create("ChangeListView.LocallyDeletedChanges");

  public ChangesListView(@NotNull Project project) {
    myProject = project;
    myGroupingSupport = new ChangesGroupingSupport(myProject, this, true);

    setModel(TreeModelBuilder.buildEmpty(project));

    setShowsRootHandles(true);
    setRootVisible(false);
    setDragEnabled(true);

    myCopyProvider = new ChangesBrowserNodeCopyProvider(this);

    ChangesBrowserNodeRenderer renderer = new ChangesBrowserNodeRenderer(project, this::isShowFlatten, true);
    setCellRenderer(renderer);

    new TreeSpeedSearch(this, TO_TEXT_CONVERTER);
    SmartExpander.installOn(this);
    new TreeLinkMouseListener(renderer).installOn(this);
  }

  @Override
  public DefaultTreeModel getModel() {
    return (DefaultTreeModel)super.getModel();
  }

  public void addGroupingChangeListener(@NotNull PropertyChangeListener listener) {
    myGroupingSupport.addPropertyChangeListener(listener);
  }

  public void removeGroupingChangeListener(@NotNull PropertyChangeListener listener) {
    myGroupingSupport.removePropertyChangeListener(listener);
  }

  @NotNull
  public ChangesGroupingSupport getGroupingSupport() {
    return myGroupingSupport;
  }

  @NotNull
  public ChangesGroupingPolicyFactory getGrouping() {
    return myGroupingSupport.getGrouping();
  }

  public boolean isShowFlatten() {
    return !myGroupingSupport.isDirectory();
  }

  public void updateModel(@NotNull DefaultTreeModel newModel) {
    TreeState state = TreeState.createOn(this, getRoot());
    state.setScrollToSelection(false);
    ChangesBrowserNode oldRoot = getRoot();
    setModel(newModel);
    ChangesBrowserNode newRoot = getRoot();
    expandPath(new TreePath(newRoot.getPath()));
    state.applyTo(this, newRoot);
    expandDefaultChangeList(oldRoot, newRoot);
  }

  private void expandDefaultChangeList(ChangesBrowserNode oldRoot, ChangesBrowserNode root) {
    if (oldRoot.getFileCount() != 0) return;
    if (TreeUtil.collectExpandedPaths(this).size() != 1) return;

    //noinspection unchecked
    Iterator<ChangesBrowserNode> nodes = ContainerUtil.<ChangesBrowserNode>iterate(root.children());
    ChangesBrowserNode defaultListNode = ContainerUtil.find(nodes, node -> {
      if (node instanceof ChangesBrowserChangeListNode) {
        ChangeList list = ((ChangesBrowserChangeListNode)node).getUserObject();
        return list instanceof LocalChangeList && ((LocalChangeList)list).isDefault();
      }
      return false;
    });

    if (defaultListNode == null) return;
    if (defaultListNode.getChildCount() == 0) return;
    if (defaultListNode.getChildCount() > 10000) return; // expanding lots of nodes is a slow operation (and result is not very useful)

    expandPath(new TreePath(new Object[]{root, defaultListNode}));
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (key == DATA_KEY) {
      sink.put(DATA_KEY, this);
    }
    else if (key == VcsDataKeys.CHANGES) {
      sink.put(VcsDataKeys.CHANGES, getSelectedChanges().toArray(Change[]::new));
    }
    else if (key == VcsDataKeys.CHANGE_LEAD_SELECTION) {
      sink.put(VcsDataKeys.CHANGE_LEAD_SELECTION, getLeadSelection().toArray(Change[]::new));
    }
    else if (key == VcsDataKeys.CHANGE_LISTS) {
      sink.put(VcsDataKeys.CHANGE_LISTS, getSelectedChangeLists().toArray(ChangeList[]::new));
    }
    else if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles().toArray(VirtualFile[]::new));
    }
    else if (key == VcsDataKeys.VIRTUAL_FILE_STREAM) {
      sink.put(VcsDataKeys.VIRTUAL_FILE_STREAM, getSelectedFiles());
    }
    else if (key == CommonDataKeys.NAVIGATABLE) {
      VirtualFile file = getIfSingle(getNavigatableFiles());
      if (file != null && !file.isDirectory()) {
        sink.put(CommonDataKeys.NAVIGATABLE, new OpenFileDescriptor(myProject, file, 0));
      }
    }
    else if (key == CommonDataKeys.NAVIGATABLE_ARRAY) {
      sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, ChangesUtil.getNavigatableArray(myProject, getNavigatableFiles()));
    }
    else if (key == PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
      if (getSelectionObjectsStream().anyMatch(userObject -> !(userObject instanceof ChangeList))) {
        sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new VirtualFileDeleteProvider());
      }
    }
    else if (key == PlatformDataKeys.COPY_PROVIDER) {
      sink.put(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
    }
    else if (key == UNVERSIONED_FILES_DATA_KEY) {
      sink.put(UNVERSIONED_FILES_DATA_KEY, getSelectedUnversionedFiles());
    }
    else if (key == IGNORED_FILES_DATA_KEY) {
      sink.put(IGNORED_FILES_DATA_KEY, getSelectedIgnoredFiles());
    }
    else if (key == VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY) {
      sink.put(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY, getSelectedModifiedWithoutEditing().collect(toList()));
    }
    else if (key == LOCALLY_DELETED_CHANGES) {
      sink.put(LOCALLY_DELETED_CHANGES, getSelectedLocallyDeletedChanges().collect(toList()));
    }
    else if (key == MISSING_FILES_DATA_KEY) {
      sink.put(MISSING_FILES_DATA_KEY, getSelectedMissingFiles().collect(toList()));
    }
    else if (VcsDataKeys.HAVE_LOCALLY_DELETED == key) {
      sink.put(VcsDataKeys.HAVE_LOCALLY_DELETED, getSelectedMissingFiles().findAny().isPresent());
    }
    else if (VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING == key) {
      sink.put(VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING, getSelectedModifiedWithoutEditing().findAny().isPresent());
    }
    else if (VcsDataKeys.HAVE_SELECTED_CHANGES == key) {
      sink.put(VcsDataKeys.HAVE_SELECTED_CHANGES, haveSelectedChanges());
    }
    else if (key == PlatformDataKeys.HELP_ID) {
      sink.put(PlatformDataKeys.HELP_ID, HELP_ID);
    }
    else if (key == ChangesGroupingSupport.KEY) {
      sink.put(ChangesGroupingSupport.KEY, myGroupingSupport);
    }
  }

  @NotNull
  public Stream<VirtualFile> getUnversionedFiles() {
    //noinspection unchecked
    Enumeration<ChangesBrowserNode> nodes = getRoot().children();
    ChangesBrowserUnversionedFilesNode node = ContainerUtil.findInstance(ContainerUtil.iterate(nodes),
                                                                         ChangesBrowserUnversionedFilesNode.class);
    if (node == null) return Stream.empty();
    return node.getFilesUnderStream();
  }

  @NotNull
  public Stream<VirtualFile> getSelectedUnversionedFiles() {
    return getSelectedVirtualFiles(UNVERSIONED_FILES_TAG);
  }

  @NotNull
  private Stream<VirtualFile> getSelectedIgnoredFiles() {
    return getSelectedVirtualFiles(IGNORED_FILES_TAG);
  }

  @NotNull
  private Stream<VirtualFile> getSelectedModifiedWithoutEditing() {
    return getSelectedVirtualFiles(MODIFIED_WITHOUT_EDITING_TAG);
  }

  @NotNull
  protected Stream<VirtualFile> getSelectedVirtualFiles(@Nullable Object tag) {
    return getSelectionNodesStream(tag)
      .flatMap(ChangesBrowserNode::getFilesUnderStream)
      .distinct();
  }

  @NotNull
  private Stream<ChangesBrowserNode<?>> getSelectionNodesStream() {
    return getSelectionNodesStream(null);
  }

  @NotNull
  private Stream<ChangesBrowserNode<?>> getSelectionNodesStream(@Nullable Object tag) {
    return stream(getSelectionPaths())
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node));
  }

  @NotNull
  private Stream<Object> getSelectionObjectsStream() {
    return getSelectionNodesStream().map(ChangesBrowserNode::getUserObject);
  }

  @NotNull
  static Stream<VirtualFile> getVirtualFiles(@Nullable TreePath[] paths, @Nullable Object tag) {
    return stream(paths)
      .filter(path -> isUnderTag(path, tag))
      .map(TreePath::getLastPathComponent)
      .map(node -> ((ChangesBrowserNode<?>)node))
      .flatMap(ChangesBrowserNode::getFilesUnderStream)
      .distinct();
  }

  static boolean isUnderTag(@NotNull TreePath path, @Nullable Object tag) {
    boolean result = true;

    if (tag != null) {
      result = path.getPathCount() > 1 && ((ChangesBrowserNode)path.getPathComponent(1)).getUserObject() == tag;
    }

    return result;
  }

  @NotNull
  static Stream<Change> getChanges(@NotNull Project project, @Nullable TreePath[] paths) {
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
    return getSelectionNodesStream(LOCALLY_DELETED_NODE_TAG)
      .flatMap(node -> node.getObjectsUnderStream(LocallyDeletedChange.class))
      .distinct();
  }

  @NotNull
  private Stream<FilePath> getSelectedMissingFiles() {
    return getSelectedLocallyDeletedChanges().map(LocallyDeletedChange::getPath);
  }

  @NotNull
  private Stream<VirtualFile> getSelectedFiles() {
    return Stream.concat(
      getAfterRevisionsFiles(getSelectedChanges()),
      getSelectedVirtualFiles(null)
    ).distinct();
  }

  @NotNull
  private Stream<VirtualFile> getNavigatableFiles() {
    return Stream.concat(
      getFiles(getSelectedChanges()),
      getSelectedVirtualFiles(null)
    ).distinct();
  }

  // TODO: Does not correspond to getSelectedChanges() - for instance, hijacked changes are not tracked here
  private boolean haveSelectedChanges() {
    return getSelectionNodesStream().anyMatch(
      node -> node instanceof ChangesBrowserChangeNode || node instanceof ChangesBrowserChangeListNode && node.getChildCount() > 0);
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
  public ChangesBrowserNode<?> getRoot() {
    return (ChangesBrowserNode<?>)getModel().getRoot();
  }

  @NotNull
  public Stream<Change> getChanges() {
    return getRoot().getObjectsUnderStream(Change.class);
  }

  @Nullable
  public List<Change> getAllChangesFromSameChangelist(@NotNull Change change) {
    DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(getRoot(), change);
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

  public void setMenuActions(final ActionGroup menuGroup) {
    PopupHandler.installPopupHandler(this, menuGroup, ActionPlaces.CHANGES_VIEW_POPUP, ActionManager.getInstance());
    editSourceRegistration();
  }

  protected void editSourceRegistration() {
    EditSourceOnDoubleClickHandler.install(this);
    EditSourceOnEnterKeyHandler.install(this);
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

  private static class DistinctChangePredicate implements Predicate<Change> {
    private final Set<Object> seen = ContainerUtil.newTroveSet(ChangeListChange.HASHING_STRATEGY);

    @Override
    public boolean test(Change change) {
      return seen.add(change);
    }
  }
}