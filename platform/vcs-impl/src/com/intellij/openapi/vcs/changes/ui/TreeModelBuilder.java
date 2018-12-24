// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.function.Function;

import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.createLockedFolders;
import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.DIRECTORY_GROUPING;
import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.NONE_GROUPING;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

@SuppressWarnings("UnusedReturnValue")
public class TreeModelBuilder {
  public static final Key<Function<StaticFilePath, ChangesBrowserNode<?>>> PATH_NODE_BUILDER = Key.create("ChangesTree.PathNodeBuilder");
  public static final NotNullLazyKey<Map<String, ChangesBrowserNode<?>>, ChangesBrowserNode<?>> DIRECTORY_CACHE =
    NotNullLazyKey.create("ChangesTree.DirectoryCache", node -> newHashMap());
  private static final Key<ChangesGroupingPolicy> GROUPING_POLICY = Key.create("ChangesTree.GroupingPolicy");
  // This is used in particular for the case when module contains files from different repositories. So there could be several nodes for
  // the same module in one subtree (for change list), but under different repository nodes. And we should perform node caching not just
  // in subtree root, but further down the tree.
  public static final Key<Boolean> IS_CACHING_ROOT = Key.create("ChangesTree.IsCachingRoot");

  protected final Project myProject;
  @NotNull protected final DefaultTreeModel myModel;
  @NotNull protected final ChangesBrowserNode myRoot;
  @NotNull private final ChangesGroupingPolicyFactory myGroupingPolicyFactory;

  @SuppressWarnings("unchecked")
  private static final Comparator<ChangesBrowserNode> BROWSER_NODE_COMPARATOR = (node1, node2) -> {
    int sortWeightDiff = Comparing.compare(node1.getSortWeight(), node2.getSortWeight());
    if (sortWeightDiff != 0) return sortWeightDiff;

    Class<?> clazz1 = node1.getClass();
    Class<?> clazz2 = node2.getClass();
    if (!clazz1.equals(clazz2)) return Comparing.compare(clazz1.getName(), clazz2.getName());

    if (node1 instanceof Comparable) {
      return ((Comparable)node1).compareTo(node2);
    }
    else {
      return node1.compareUserObjects(node2.getUserObject());
    }
  };

  protected final static Comparator<FilePath> PATH_COMPARATOR = comparingInt(path -> path.getPath().length());
  protected final static Comparator<Change> CHANGE_COMPARATOR = comparing(ChangesUtil::getFilePath, PATH_COMPARATOR);

  /**
   * @deprecated Use {@link #TreeModelBuilder(Project, ChangesGroupingPolicyFactory)}.
   */
  @Deprecated
  public TreeModelBuilder(@NotNull Project project, boolean showFlatten) {
    this(project, ChangesGroupingSupport.getFactory(project, showFlatten ? NONE_GROUPING : DIRECTORY_GROUPING));
  }

  /**
   * Requires non-null Project for local changes.
   */
  public TreeModelBuilder(Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
    myProject = project != null && !project.isDefault() ? project : null;
    myRoot = ChangesBrowserNode.createRoot();
    myModel = new DefaultTreeModel(myRoot);
    myGroupingPolicyFactory = grouping;
  }

  @NotNull
  public static DefaultTreeModel buildEmpty() {
    return new DefaultTreeModel(ChangesBrowserNode.createRoot());
  }

  /**
   * @deprecated Use {@link TreeModelBuilder#buildFromChanges(Project, ChangesGroupingPolicyFactory, Collection, ChangeNodeDecorator)}.
   */
  @Deprecated
  @NotNull
  public static DefaultTreeModel buildFromChanges(@NotNull Project project,
                                                  boolean showFlatten,
                                                  @NotNull Collection<? extends Change> changes,
                                                  @Nullable ChangeNodeDecorator changeNodeDecorator) {
    return new TreeModelBuilder(project, showFlatten)
      .setChanges(changes, changeNodeDecorator)
      .build();
  }

  @NotNull
  public static DefaultTreeModel buildFromChanges(@Nullable Project project,
                                                  @NotNull ChangesGroupingPolicyFactory grouping,
                                                  @NotNull Collection<? extends Change> changes,
                                                  @Nullable ChangeNodeDecorator changeNodeDecorator) {
    return new TreeModelBuilder(project, grouping)
      .setChanges(changes, changeNodeDecorator)
      .build();
  }

  @NotNull
  public static DefaultTreeModel buildFromFilePaths(@Nullable Project project,
                                                    @NotNull ChangesGroupingPolicyFactory grouping,
                                                    @NotNull Collection<? extends FilePath> filePaths) {
    return new TreeModelBuilder(project, grouping)
      .setFilePaths(filePaths)
      .build();
  }

  @NotNull
  public static DefaultTreeModel buildFromChangeLists(@NotNull Project project,
                                                      @NotNull ChangesGroupingPolicyFactory grouping,
                                                      @NotNull Collection<? extends ChangeList> changeLists) {
    return buildFromChangeLists(project, grouping, changeLists, false);
  }

  @NotNull
  public static DefaultTreeModel buildFromChangeLists(@NotNull Project project,
                                                      @NotNull ChangesGroupingPolicyFactory grouping,
                                                      @NotNull Collection<? extends ChangeList> changeLists,
                                                      boolean skipSingleDefaultChangelist) {
    return new TreeModelBuilder(project, grouping)
      .setChangeLists(changeLists, skipSingleDefaultChangelist)
      .build();
  }

  @NotNull
  public static DefaultTreeModel buildFromVirtualFiles(@Nullable Project project,
                                                       @NotNull ChangesGroupingPolicyFactory grouping,
                                                       @NotNull Collection<? extends VirtualFile> virtualFiles) {
    return new TreeModelBuilder(project, grouping)
      .setVirtualFiles(virtualFiles, null)
      .build();
  }

  @NotNull
  public TreeModelBuilder setChanges(@NotNull Collection<? extends Change> changes, @Nullable ChangeNodeDecorator changeNodeDecorator) {
    return setChanges(changes, changeNodeDecorator, null);
  }

  @NotNull
  public TreeModelBuilder setChanges(@NotNull Collection<? extends Change> changes,
                                     @Nullable ChangeNodeDecorator changeNodeDecorator,
                                     @Nullable Object tag) {
    ChangesBrowserNode<?> parentNode = createTagNode(tag);

    List<? extends Change> sortedChanges = sorted(changes, CHANGE_COMPARATOR);
    for (Change change : sortedChanges) {
      insertChangeNode(change, parentNode, createChangeNode(change, changeNodeDecorator));
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setUnversioned(@Nullable List<VirtualFile> unversionedFiles) {
    assert myProject != null;
    if (ContainerUtil.isEmpty(unversionedFiles)) return this;
    ChangesBrowserUnversionedFilesNode node = new ChangesBrowserUnversionedFilesNode(myProject, unversionedFiles);
    return insertSpecificNodeToModel(unversionedFiles, node);
  }

  @NotNull
  public TreeModelBuilder setIgnored(@Nullable List<VirtualFile> ignoredFiles, boolean updatingMode) {
    assert myProject != null;
    if (ContainerUtil.isEmpty(ignoredFiles)) return this;
    ChangesBrowserIgnoredFilesNode node = new ChangesBrowserIgnoredFilesNode(myProject, ignoredFiles, updatingMode);
    return insertSpecificNodeToModel(ignoredFiles, node);
  }

  @NotNull
  private TreeModelBuilder insertSpecificNodeToModel(@NotNull List<? extends VirtualFile> specificFiles,
                                                     @NotNull ChangesBrowserSpecificFilesNode node) {
    myModel.insertNodeInto(node, myRoot, myRoot.getChildCount());
    if (!node.isManyFiles()) {
      node.markAsHelperNode();
      insertFilesIntoNode(specificFiles, node);
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setChangeLists(@NotNull Collection<? extends ChangeList> changeLists, boolean skipSingleDefaultChangeList) {
    assert myProject != null;
    final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
    boolean skipChangeListNode = skipSingleDefaultChangeList && isSingleBlankChangeList(changeLists);
    for (ChangeList list : changeLists) {
      List<Change> changes = sorted(list.getChanges(), CHANGE_COMPARATOR);
      ChangeListRemoteState listRemoteState = new ChangeListRemoteState(changes.size());

      ChangesBrowserNode changesParent;
      if (!skipChangeListNode) {
        ChangesBrowserChangeListNode listNode = new ChangesBrowserChangeListNode(myProject, list, listRemoteState);
        listNode.markAsHelperNode();

        myModel.insertNodeInto(listNode, myRoot, 0);
        changesParent = listNode;
      }
      else {
        changesParent = myRoot;
      }

      for (int i = 0; i < changes.size(); i++) {
        Change change = changes.get(i);
        RemoteStatusChangeNodeDecorator decorator = new RemoteStatusChangeNodeDecorator(revisionsCache, listRemoteState, i);
        insertChangeNode(change, changesParent, createChangeNode(change, decorator));
      }
    }
    return this;
  }

  private static boolean isSingleBlankChangeList(Collection<? extends ChangeList> lists) {
    if (lists.size() != 1) return false;
    ChangeList single = lists.iterator().next();
    if (!(single instanceof LocalChangeList)) return false;
    return ((LocalChangeList) single).isBlank();
  }

  protected ChangesBrowserNode createChangeNode(Change change, ChangeNodeDecorator decorator) {
    return new ChangesBrowserChangeNode(myProject, change, decorator);
  }

  @NotNull
  public TreeModelBuilder setLockedFolders(@Nullable List<? extends VirtualFile> lockedFolders) {
    assert myProject != null;
    if (ContainerUtil.isEmpty(lockedFolders)) return this;
    insertFilesIntoNode(lockedFolders, createLockedFolders(myProject));
    return this;
  }

  @NotNull
  public TreeModelBuilder setModifiedWithoutEditing(@NotNull List<? extends VirtualFile> modifiedWithoutEditing) {
    return setVirtualFiles(modifiedWithoutEditing, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
  }

  @NotNull
  private TreeModelBuilder setVirtualFiles(@Nullable Collection<? extends VirtualFile> files, @Nullable Object tag) {
    if (ContainerUtil.isEmpty(files)) return this;
    insertFilesIntoNode(files, createTagNode(tag));
    return this;
  }

  @NotNull
  protected ChangesBrowserNode createTagNode(@Nullable Object tag) {
    if (tag == null) return myRoot;

    ChangesBrowserNode subtreeRoot = ChangesBrowserNode.createObject(tag);
    subtreeRoot.markAsHelperNode();

    myModel.insertNodeInto(subtreeRoot, myRoot, myRoot.getChildCount());
    return subtreeRoot;
  }

  private void insertFilesIntoNode(@NotNull Collection<? extends VirtualFile> files, @NotNull ChangesBrowserNode subtreeRoot) {
    List<VirtualFile> sortedFiles = sorted(files, VirtualFileHierarchicalComparator.getInstance());
    for (VirtualFile file : sortedFiles) {
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.createFile(myProject, file));
    }
  }

  @NotNull
  public TreeModelBuilder setLocallyDeletedPaths(@Nullable Collection<? extends LocallyDeletedChange> locallyDeletedChanges) {
    if (ContainerUtil.isEmpty(locallyDeletedChanges)) return this;
    ChangesBrowserNode subtreeRoot = createTagNode(ChangesBrowserNode.LOCALLY_DELETED_NODE_TAG);

    for (LocallyDeletedChange change : sorted(locallyDeletedChanges, comparing(LocallyDeletedChange::getPath, PATH_COMPARATOR))) {
      insertChangeNode(change, subtreeRoot, ChangesBrowserNode.createLocallyDeleted(change));
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setFilePaths(@NotNull Collection<? extends FilePath> filePaths) {
    return setFilePaths(filePaths, myRoot);
  }

  @NotNull
  private TreeModelBuilder setFilePaths(@NotNull Collection<? extends FilePath> filePaths, @NotNull ChangesBrowserNode subtreeRoot) {
    for (FilePath file : sorted(filePaths, PATH_COMPARATOR)) {
      assert file != null;
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.createFilePath(file));
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setSwitchedRoots(@Nullable Map<VirtualFile, String> switchedRoots) {
    if (ContainerUtil.isEmpty(switchedRoots)) return this;
    final ChangesBrowserNode rootsHeadNode = createTagNode(ChangesBrowserNode.SWITCHED_ROOTS_TAG);
    rootsHeadNode.setAttributes(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);

    List<VirtualFile> files = sorted(switchedRoots.keySet(), VirtualFileHierarchicalComparator.getInstance());

    for (VirtualFile vf : files) {
      final ContentRevision cr = new CurrentContentRevision(VcsUtil.getFilePath(vf));
      final Change change = new Change(cr, cr, FileStatus.NOT_CHANGED);
      final String branchName = switchedRoots.get(vf);
      insertChangeNode(vf, rootsHeadNode, createChangeNode(change, new ChangeNodeDecorator() {
        @Override
        public void decorate(Change change1, SimpleColoredComponent component, boolean isShowFlatten) {
        }

        @Override
        public void preDecorate(Change change1, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
          renderer.append("[" + branchName + "] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
        }
      }));
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setSwitchedFiles(@NotNull MultiMap<String, VirtualFile> switchedFiles) {
    if (switchedFiles.isEmpty()) return this;
    ChangesBrowserNode subtreeRoot = createTagNode(ChangesBrowserNode.SWITCHED_FILES_TAG);
    for(String branchName: switchedFiles.keySet()) {
      List<VirtualFile> switchedFileList = sorted(switchedFiles.get(branchName), VirtualFileHierarchicalComparator.getInstance());
      if (switchedFileList.size() > 0) {
        ChangesBrowserNode branchNode = ChangesBrowserNode.createObject(branchName);
        branchNode.markAsHelperNode();

        myModel.insertNodeInto(branchNode, subtreeRoot, subtreeRoot.getChildCount());

        for (VirtualFile file : switchedFileList) {
          insertChangeNode(file, branchNode, ChangesBrowserNode.createFile(myProject, file));
        }
      }
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setLogicallyLockedFiles(@Nullable Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    if (ContainerUtil.isEmpty(logicallyLockedFiles)) return this;
    final ChangesBrowserNode subtreeRoot = createTagNode(ChangesBrowserNode.LOGICALLY_LOCKED_TAG);

    List<VirtualFile> keys = sorted(logicallyLockedFiles.keySet(), VirtualFileHierarchicalComparator.getInstance());

    for (VirtualFile file : keys) {
      final LogicalLock lock = logicallyLockedFiles.get(file);
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.createLogicallyLocked(myProject, file, lock));
    }
    return this;
  }

  protected void insertChangeNode(@NotNull Object change,
                                  @NotNull ChangesBrowserNode subtreeRoot,
                                  @NotNull ChangesBrowserNode node) {
    insertChangeNode(change, subtreeRoot, node, TreeModelBuilder::createPathNode);
  }

  protected void insertChangeNode(@NotNull Object change,
                                  @NotNull ChangesBrowserNode subtreeRoot,
                                  @NotNull ChangesBrowserNode node,
                                  @NotNull Function<StaticFilePath, ChangesBrowserNode<?>> nodeBuilder) {
    PATH_NODE_BUILDER.set(subtreeRoot, nodeBuilder);
    if (!GROUPING_POLICY.isIn(subtreeRoot)) {
      GROUPING_POLICY.set(subtreeRoot, myGroupingPolicyFactory.createGroupingPolicy(myModel));
    }

    StaticFilePath pathKey = getKey(change);
    ChangesBrowserNode<?> parentNode = ReadAction.compute(
      () -> notNull(GROUPING_POLICY.getRequired(subtreeRoot).getParentNodeFor(pathKey, subtreeRoot), subtreeRoot));
    ChangesBrowserNode<?> cachingRoot = BaseChangesGroupingPolicy.getCachingRoot(parentNode, subtreeRoot);

    myModel.insertNodeInto(node, parentNode, myModel.getChildCount(parentNode));

    if (pathKey.isDirectory()) {
      DIRECTORY_CACHE.getValue(cachingRoot).put(pathKey.getKey(), node);
    }
  }

  @NotNull
  public DefaultTreeModel build() {
    collapseDirectories(myModel, myRoot);
    sortNodes();
    return myModel;
  }

  private void sortNodes() {
    TreeUtil.sort(myModel, BROWSER_NODE_COMPARATOR);

    myModel.nodeStructureChanged((TreeNode)myModel.getRoot());
  }

  private static void collapseDirectories(@NotNull DefaultTreeModel model, @NotNull ChangesBrowserNode node) {
    ChangesBrowserNode collapsedNode = node;
    while (collapsedNode.getChildCount() == 1) {
      ChangesBrowserNode child = (ChangesBrowserNode)collapsedNode.getChildAt(0);

      ChangesBrowserNode collapsed = collapseParentWithOnlyChild(collapsedNode, child);
      if (collapsed == null) break;

      collapsedNode = collapsed;
    }

    if (collapsedNode != node) {
      ChangesBrowserNode parent = (ChangesBrowserNode)node.getParent();
      final int idx = parent.getIndex(node);
      model.removeNodeFromParent(node);
      model.insertNodeInto(collapsedNode, parent, idx);

      node = collapsedNode;
    }

    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      ChangesBrowserNode child = (ChangesBrowserNode)children.nextElement();
      collapseDirectories(model, child);
    }
  }

  @Nullable
  private static ChangesBrowserNode collapseParentWithOnlyChild(@NotNull ChangesBrowserNode parent, @NotNull ChangesBrowserNode child) {
    if (child.isLeaf()) return null;

    Object parentUserObject = parent.getUserObject();
    Object childUserObject = child.getUserObject();

    if (parentUserObject instanceof FilePath &&
        childUserObject instanceof FilePath) {
      return child;
    }

    if (parent instanceof ChangesBrowserModuleNode &&
        childUserObject instanceof FilePath) {
      FilePath parentPath = ((ChangesBrowserModuleNode)parent).getModuleRoot();
      FilePath childPath = (FilePath)childUserObject;
      if (!parentPath.equals(childPath)) return null;

      parent.remove(0);

      //noinspection unchecked
      Enumeration<ChangesBrowserNode> children = child.children();
      for (ChangesBrowserNode childNode : toList(children)) {
        parent.add(childNode);
      }

      return parent;
    }

    return null;
  }

  @NotNull
  private static StaticFilePath getKey(@NotNull Object o) {
    if (o instanceof Change) {
      return staticFrom(ChangesUtil.getFilePath((Change)o));
    }
    else if (o instanceof VirtualFile) {
      return staticFrom((VirtualFile)o);
    }
    else if (o instanceof FilePath) {
      return staticFrom((FilePath)o);
    }
    else if (o instanceof LocallyDeletedChange) {
      return staticFrom(((LocallyDeletedChange)o).getPath());
    }

    throw new IllegalArgumentException("Unknown type - " + o.getClass());
  }

  @NotNull
  public static StaticFilePath staticFrom(@NotNull FilePath fp) {
    return new StaticFilePath(fp.isDirectory(), fp.getPath(), fp.getVirtualFile());
  }

  @NotNull
  public static StaticFilePath staticFrom(@NotNull VirtualFile vf) {
    return new StaticFilePath(vf.isDirectory(), vf.getPath(), vf);
  }

  @NotNull
  public static FilePath getPathForObject(@NotNull Object o) {
    if (o instanceof Change) {
      return ChangesUtil.getFilePath((Change)o);
    }
    else if (o instanceof VirtualFile) {
      return VcsUtil.getFilePath((VirtualFile)o);
    }
    else if (o instanceof FilePath) {
      return (FilePath)o;
    }
    else if (o instanceof LocallyDeletedChange) {
      return ((LocallyDeletedChange)o).getPath();
    }

    throw new IllegalArgumentException("Unknown type - " + o.getClass());
  }

  @NotNull
  private static ChangesBrowserNode createPathNode(@NotNull StaticFilePath path) {
    FilePath filePath = VcsUtil.getFilePath(path.getPath(), path.isDirectory());
    return ChangesBrowserNode.createFilePath(filePath);
  }

  public boolean isEmpty() {
    return myModel.getChildCount(myRoot) == 0;
  }

  @NotNull
  @Deprecated
  public DefaultTreeModel buildModel(@NotNull List<? extends Change> changes, @Nullable ChangeNodeDecorator changeNodeDecorator) {
    return setChanges(changes, changeNodeDecorator).build();
  }
}
