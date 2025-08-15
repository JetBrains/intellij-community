// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.vcs.VcsUtil;
import com.intellij.platform.vcs.changes.ChangesUtil;
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewModelBuilderService;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.function.Function;

import static com.intellij.openapi.vcs.changes.ui.TreeModelBuilderKeys.IS_CACHING_ROOT;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

@SuppressWarnings("UnusedReturnValue")
public class TreeModelBuilder implements ChangesViewModelBuilder {
  public static final Key<Function<StaticFilePath, ChangesBrowserNode<?>>> PATH_NODE_BUILDER = Key.create("ChangesTree.PathNodeBuilder");
  public static final NotNullLazyKey<Map<String, ChangesBrowserNode<?>>, ChangesBrowserNode<?>> DIRECTORY_CACHE =
    TreeModelBuilderKeys.DIRECTORY_CACHE;
  private static final Key<ChangesGroupingPolicy> GROUPING_POLICY = Key.create("ChangesTree.GroupingPolicy");

  /**
   * The helper UserData keys that will be cleaned at the end of the tree building to reduce memory footprint.
   */
  private static final @NotNull List<Key<?>> TEMP_CACHE_KEYS =
    Arrays.asList(DIRECTORY_CACHE, IS_CACHING_ROOT, SimpleChangesGroupingPolicy.GROUP_NODE_CACHE);

  public final @Nullable Project myProject;

  public final @NotNull DefaultTreeModel myModel;
  public final @NotNull ChangesBrowserNode<?> myRoot;
  private final @NotNull ChangesGroupingPolicyFactory myGroupingPolicyFactory;

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final Comparator<ChangesBrowserNode> BROWSER_NODE_COMPARATOR = (node1, node2) -> {
    int sortWeightDiff = Integer.compare(node1.getSortWeight(), node2.getSortWeight());
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

  /**
   * Order in which nodes should be added into the tree while using {@link #insertChangeNode(StaticFilePath, ChangesBrowserNode, ChangesBrowserNode)}.
   * This ensures that helper {@link #createPathNode} node will not be created if there is already a 'data' node with the same path,
   * as all 'parents' are processed before their 'children'.
   */
  public static final Comparator<FilePath> PATH_COMPARATOR = comparingInt(path -> path.getPath().length());
  public static final Comparator<Change> CHANGE_COMPARATOR = comparing(ChangesUtil::getFilePath, PATH_COMPARATOR);
  public static final Comparator<VirtualFile> FILE_COMPARATOR = VirtualFileHierarchicalComparator.getInstance();

  /**
   * Requires non-null Project for local changes.
   */
  public TreeModelBuilder(@Nullable Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
    myProject = project != null && !project.isDefault() ? project : null;
    myRoot = ChangesBrowserNode.createRoot();
    myModel = new ChangesTreeModel(myRoot);
    myGroupingPolicyFactory = grouping;
  }

  public static @NotNull DefaultTreeModel buildEmpty() {
    return new ChangesTreeModel(ChangesBrowserNode.createRoot());
  }

  public static @NotNull DefaultTreeModel buildFromChanges(@Nullable Project project,
                                                           @NotNull ChangesGroupingPolicyFactory grouping,
                                                           @NotNull Collection<? extends Change> changes,
                                                           @Nullable ChangeNodeDecorator changeNodeDecorator) {
    return new TreeModelBuilder(project, grouping)
      .setChanges(changes, changeNodeDecorator)
      .build();
  }

  public static @NotNull DefaultTreeModel buildFromFilePaths(@Nullable Project project,
                                                             @NotNull ChangesGroupingPolicyFactory grouping,
                                                             @NotNull Collection<? extends FilePath> filePaths) {
    return new TreeModelBuilder(project, grouping)
      .setFilePaths(filePaths)
      .build();
  }

  public static @NotNull DefaultTreeModel buildFromChangeLists(@NotNull Project project,
                                                               @NotNull ChangesGroupingPolicyFactory grouping,
                                                               @NotNull Collection<? extends ChangeList> changeLists) {
    return buildFromChangeLists(project, grouping, changeLists, false);
  }

  public static @NotNull DefaultTreeModel buildFromChangeLists(@NotNull Project project,
                                                               @NotNull ChangesGroupingPolicyFactory grouping,
                                                               @NotNull Collection<? extends ChangeList> changeLists,
                                                               boolean skipSingleDefaultChangelist) {
    return new TreeModelBuilder(project, grouping)
      .setChangeLists(changeLists, skipSingleDefaultChangelist, null)
      .build();
  }

  public static @NotNull DefaultTreeModel buildFromVirtualFiles(@Nullable Project project,
                                                                @NotNull ChangesGroupingPolicyFactory grouping,
                                                                @NotNull Collection<? extends VirtualFile> virtualFiles) {
    return new TreeModelBuilder(project, grouping)
      .setVirtualFiles(virtualFiles, null)
      .build();
  }

  public @NotNull TreeModelBuilder setChanges(@NotNull Collection<? extends Change> changes,
                                              @Nullable ChangeNodeDecorator changeNodeDecorator) {
    return setChanges(changes, changeNodeDecorator, null);
  }

  public @NotNull TreeModelBuilder setChanges(@NotNull Collection<? extends Change> changes,
                                              @Nullable ChangeNodeDecorator changeNodeDecorator,
                                              @Nullable ChangesBrowserNode.Tag tag) {
    insertChanges(changes, createTagNode(tag), changeNodeDecorator);
    return this;
  }

  @Override
  public void insertChanges(@NotNull Collection<? extends Change> changes, @NotNull ChangesBrowserNode<?> subtreeRoot) {
    insertChanges(changes, subtreeRoot, null);
  }

  public void insertChanges(@NotNull Collection<? extends Change> changes,
                            @NotNull ChangesBrowserNode<?> subtreeRoot,
                            @Nullable ChangeNodeDecorator changeNodeDecorator) {
    for (Change change : sorted(changes, CHANGE_COMPARATOR)) {
      insertChangeNode(change, subtreeRoot, createChangeNode(change, changeNodeDecorator));
    }
  }

  public @NotNull TreeModelBuilder setUnversioned(@Nullable List<? extends FilePath> unversionedFiles) {
    assert myProject != null;
    if (ContainerUtil.isEmpty(unversionedFiles)) return this;
    ChangesBrowserUnversionedFilesNode node = new ChangesBrowserUnversionedFilesNode(myProject, unversionedFiles);
    return insertSpecificFilePathNodeToModel(unversionedFiles, node, FileStatus.UNKNOWN);
  }

  public @NotNull TreeModelBuilder setIgnored(@Nullable List<? extends FilePath> ignoredFiles) {
    assert myProject != null;
    if (ContainerUtil.isEmpty(ignoredFiles)) return this;
    ChangesBrowserIgnoredFilesNode node = new ChangesBrowserIgnoredFilesNode(myProject, ignoredFiles);
    return insertSpecificFilePathNodeToModel(ignoredFiles, node, FileStatus.IGNORED);
  }

  private @NotNull TreeModelBuilder insertSpecificFilePathNodeToModel(@NotNull List<? extends FilePath> specificFiles,
                                                                      @NotNull ChangesBrowserSpecificFilePathsNode<?> node,
                                                                      @NotNull FileStatus status) {
    insertSubtreeRoot(node);
    if (!node.isManyFiles()) {
      node.markAsHelperNode();
      insertLocalFilePathIntoNode(specificFiles, node, status);
    }
    return this;
  }

  @ApiStatus.Internal
  public @NotNull TreeModelBuilder insertSpecificFileNodeToModel(@NotNull List<? extends VirtualFile> specificFiles,
                                                                 @NotNull ChangesBrowserSpecificFilesNode<?> node) {
    insertSubtreeRoot(node);
    if (!node.isManyFiles()) {
      node.markAsHelperNode();
      insertLocalFileIntoNode(specificFiles, node);
    }
    return this;
  }

  public @NotNull TreeModelBuilder setChangeLists(@NotNull Collection<? extends ChangeList> changeLists,
                                                  boolean skipSingleDefaultChangeList,
                                                  @Nullable Function<? super ChangeNodeDecorator, ? extends ChangeNodeDecorator> changeDecoratorProvider) {

    assert myProject != null;
    ChangesViewModelBuilderService.getInstance(myProject)
      .setChangeList(this, changeLists, skipSingleDefaultChangeList, changeDecoratorProvider);
    return this;
  }

  @ApiStatus.Internal
  public static boolean isSingleBlankChangeList(Collection<? extends ChangeList> lists) {
    if (lists.size() != 1) return false;
    ChangeList single = lists.iterator().next();
    if (!(single instanceof LocalChangeList)) return false;
    return ((LocalChangeList)single).isBlank();
  }

  public @NotNull ChangesBrowserNode<?> createChangeNode(@NotNull Change change, @Nullable ChangeNodeDecorator decorator) {
    return new ChangesBrowserChangeNode(myProject, change, decorator);
  }

  private @NotNull TreeModelBuilder setVirtualFiles(@Nullable Collection<? extends VirtualFile> files,
                                                    @Nullable ChangesBrowserNode.Tag tag) {
    if (ContainerUtil.isEmpty(files)) return this;
    insertFilesIntoNode(files, createTagNode(tag));
    return this;
  }

  public @NotNull ChangesBrowserNode<?> createTagNode(@NotNull @Nls String tag) {
    return createTagNode(new ChangesBrowserNode.TagImpl(tag));
  }

  public @NotNull ChangesBrowserNode<?> createTagNode(@Nullable ChangesBrowserNode.Tag tag) {
    return createTagNode(tag, SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
  }

  public @NotNull ChangesBrowserNode<?> createTagNode(@NotNull @Nls String tag, boolean expandByDefault) {
    return createTagNode(tag, SimpleTextAttributes.REGULAR_ATTRIBUTES, expandByDefault);
  }

  public @NotNull ChangesBrowserNode<?> createTagNode(@NotNull @Nls String tag,
                                                      @NotNull SimpleTextAttributes attributes,
                                                      boolean expandByDefault) {
    return createTagNode(new ChangesBrowserNode.TagImpl(tag), attributes, expandByDefault);
  }

  public @NotNull ChangesBrowserNode<?> createTagNode(@Nullable ChangesBrowserNode.Tag tag,
                                                      @NotNull SimpleTextAttributes attributes,
                                                      boolean expandByDefault) {
    if (tag == null) return myRoot;

    ChangesBrowserNode<?> subtreeRoot = new TagChangesBrowserNode(tag, attributes, expandByDefault);
    return insertTagNode(subtreeRoot);
  }

  @NotNull
  public ChangesBrowserNode<?> insertTagNode(@NotNull ChangesBrowserNode<?> subtreeRoot) {
    subtreeRoot.markAsHelperNode();

    insertSubtreeRoot(subtreeRoot);
    return subtreeRoot;
  }

  @Override
  public void insertFilesIntoNode(@NotNull Collection<? extends VirtualFile> files, @NotNull ChangesBrowserNode<?> subtreeRoot) {
    List<VirtualFile> sortedFiles = sorted(files, FILE_COMPARATOR);
    for (VirtualFile file : sortedFiles) {
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.createFile(myProject, file));
    }
  }

  private void insertLocalFilePathIntoNode(@NotNull Collection<? extends FilePath> files,
                                           @NotNull ChangesBrowserNode<?> subtreeRoot,
                                           @NotNull FileStatus status) {
    List<FilePath> sortedFilePaths = sorted(files, PATH_COMPARATOR);
    for (FilePath filePath : sortedFilePaths) {
      insertChangeNode(filePath, subtreeRoot, ChangesBrowserNode.createFilePath(filePath, status));
    }
  }

  private void insertLocalFileIntoNode(@NotNull Collection<? extends VirtualFile> files,
                                       @NotNull ChangesBrowserNode<?> subtreeRoot) {
    List<VirtualFile> sortedFiles = sorted(files, FILE_COMPARATOR);
    for (VirtualFile file : sortedFiles) {
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.createFile(myProject, file));
    }
  }

  public @NotNull TreeModelBuilder setFilePaths(@NotNull Collection<? extends FilePath> filePaths) {
    return setFilePaths(filePaths, myRoot);
  }

  public @NotNull TreeModelBuilder setFilePaths(@NotNull Collection<? extends FilePath> filePaths,
                                                @NotNull ChangesBrowserNode<?> subtreeRoot) {
    for (FilePath file : sorted(filePaths, PATH_COMPARATOR)) {
      assert file != null;
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.createFilePath(file));
    }
    return this;
  }

  @Override
  public @NotNull TreeModelBuilder insertSubtreeRoot(@NotNull ChangesBrowserNode<?> node) {
    insertSubtreeRoot(node, myRoot);
    return this;
  }

  public @NotNull TreeModelBuilder insertSubtreeRoot(@NotNull ChangesBrowserNode<?> node, @NotNull ChangesBrowserNode<?> subtreeRoot) {
    myModel.insertNodeInto(node, subtreeRoot, subtreeRoot.getChildCount());
    return this;
  }

  public void insertChangeNode(@NotNull FilePath nodePath,
                               @NotNull ChangesBrowserNode<?> subtreeRoot,
                               @NotNull ChangesBrowserNode<?> node) {
    insertChangeNode(staticFrom(nodePath), subtreeRoot, node);
  }

  public void insertChangeNode(@NotNull VirtualFile nodePath,
                               @NotNull ChangesBrowserNode<?> subtreeRoot,
                               @NotNull ChangesBrowserNode<?> node) {
    insertChangeNode(staticFrom(nodePath), subtreeRoot, node);
  }

  public void insertChangeNode(@NotNull Change nodePath,
                               @NotNull ChangesBrowserNode<?> subtreeRoot,
                               @NotNull ChangesBrowserNode<?> node) {
    insertChangeNode(staticFrom(nodePath), subtreeRoot, node);
  }

  public void insertChangeNode(@NotNull StaticFilePath pathKey,
                               @NotNull ChangesBrowserNode<?> subtreeRoot,
                               @NotNull ChangesBrowserNode<?> node) {
    insertChangeNode(pathKey, subtreeRoot, node, TreeModelBuilder::createPathNode);
  }

  protected void insertChangeNode(@NotNull StaticFilePath pathKey,
                                  @NotNull ChangesBrowserNode<?> subtreeRoot,
                                  @NotNull ChangesBrowserNode<?> node,
                                  @NotNull Function<StaticFilePath, ChangesBrowserNode<?>> nodeBuilder) {
    ProgressManager.checkCanceled();

    PATH_NODE_BUILDER.set(subtreeRoot, nodeBuilder);
    if (!GROUPING_POLICY.isIn(subtreeRoot)) {
      ChangesGroupingPolicy policy = myProject != null ? myGroupingPolicyFactory.createGroupingPolicy(myProject, myModel)
                                                       : NoneChangesGroupingPolicy.INSTANCE;
      GROUPING_POLICY.set(subtreeRoot, policy);
    }

    ChangesBrowserNode<?> parentNode = ReadAction.compute(
      () -> notNull(GROUPING_POLICY.getRequired(subtreeRoot).getParentNodeFor(pathKey, node, subtreeRoot), subtreeRoot));
    ChangesBrowserNode<?> cachingRoot = BaseChangesGroupingPolicy.getCachingRoot(parentNode, subtreeRoot);

    myModel.insertNodeInto(node, parentNode, myModel.getChildCount(parentNode));

    if (pathKey.isDirectory()) {
      DIRECTORY_CACHE.getValue(cachingRoot).put(pathKey.getKey(), node);
    }
  }

  public @NotNull DefaultTreeModel build() {
    return build(false);
  }

  @ApiStatus.Experimental
  public @NotNull DefaultTreeModel build(boolean forcePrepareCaches) {
    TreeUtil.sort(myModel, BROWSER_NODE_COMPARATOR);
    collapseDirectories(myModel, myRoot);

    if (myProject != null && !ApplicationManager.getApplication().isDispatchThread()) {
      // Pre-fill background colors for small trees to reduce blinking
      if (!TreeUtil.hasManyNodes(myRoot, 1000) || forcePrepareCaches) {
        //noinspection deprecation
        ReadAction.nonBlocking(() -> {
          precalculateFileColors(myProject, myRoot);
        }).executeSynchronously();
      }
    }

    myRoot.traverse().forEach(node -> {
      for (Key<?> key : TEMP_CACHE_KEYS) {
        node.putUserData(key, null);
      }
    });

    myModel.nodeStructureChanged((TreeNode)myModel.getRoot());
    return myModel;
  }

  /**
   * Calculating file background color is a costly operation, so it should be done in background.
   * (Ex: it requires project file index to detect whether a file in test sources or not)
   */
  @RequiresReadLock
  private static void precalculateFileColors(@NotNull Project project, @NotNull ChangesBrowserNode<?> root) {
    root.traverse().forEach(node -> {
      node.cacheBackgroundColor(project);
      // Allow to interrupt read lock
      ProgressManager.checkCanceled();
    });
  }

  private static void collapseDirectories(@NotNull DefaultTreeModel model, @NotNull ChangesBrowserNode<?> node) {
    ChangesBrowserNode<?> collapsedNode = node;
    while (collapsedNode.getChildCount() == 1) {
      ChangesBrowserNode<?> child = (ChangesBrowserNode<?>)collapsedNode.getChildAt(0);

      ChangesBrowserNode<?> collapsed = collapseParentWithOnlyChild(collapsedNode, child);
      if (collapsed == null) break;

      collapsedNode = collapsed;
    }

    if (collapsedNode != node) {
      ChangesBrowserNode<?> parent = node.getParent();
      final int idx = parent.getIndex(node);
      model.removeNodeFromParent(node);
      model.insertNodeInto(collapsedNode, parent, idx);

      node = collapsedNode;
    }

    node.iterateNodeChildren().forEach(child -> {
      collapseDirectories(model, child);
    });
  }

  private static @Nullable ChangesBrowserNode<?> collapseParentWithOnlyChild(@NotNull ChangesBrowserNode<?> parent,
                                                                             @NotNull ChangesBrowserNode<?> child) {
    if (child.isLeaf()) return null;

    Object parentUserObject = parent.getUserObject();
    Object childUserObject = child.getUserObject();

    if (parentUserObject instanceof FilePath &&
        childUserObject instanceof FilePath) {
      appendCollapsedParent(child, parent);
      return child;
    }

    if (parent instanceof ChangesBrowserNode.NodeWithFilePath parentWithPath &&
        childUserObject instanceof FilePath childPath) {
      FilePath parentPath = parentWithPath.getNodeFilePath();
      if (!parentPath.equals(childPath)) return null;

      parent.remove(0);

      List<ChangesBrowserNode<?>> children = child.iterateNodeChildren().toList(); // defensive copy
      for (ChangesBrowserNode<?> childNode : children) {
        parent.add(childNode);
        appendCollapsedParent(childNode, child);
      }

      return parent;
    }

    return null;
  }

  private static void appendCollapsedParent(@NotNull ChangesBrowserNode<?> child, ChangesBrowserNode<?> parent) {
    if (child instanceof ChangesBrowserNode.NodeWithCollapsedParents childWithCollapsedParents) {
      childWithCollapsedParents.addCollapsedParent(parent);
    }
  }

  public static @NotNull StaticFilePath staticFrom(@NotNull FilePath fp) {
    return new StaticFilePath(fp);
  }

  public static @NotNull StaticFilePath staticFrom(@NotNull VirtualFile vf) {
    return new StaticFilePath(VcsUtil.getFilePath(vf));
  }

  public static @NotNull StaticFilePath staticFrom(@NotNull Change change) {
    return staticFrom(ChangesUtil.getFilePath(change));
  }

  public static @NotNull FilePath getPathForObject(@NotNull Object o) {
    if (o instanceof Change change) {
      return ChangesUtil.getFilePath(change);
    }
    else if (o instanceof VirtualFile virtualFile) {
      return VcsUtil.getFilePath(virtualFile);
    }
    else if (o instanceof FilePath) {
      return (FilePath)o;
    }
    else if (o instanceof LocallyDeletedChange locallyDeletedChange) {
      return locallyDeletedChange.getPath();
    }

    throw new IllegalArgumentException("Unknown type - " + o.getClass());
  }

  private static @NotNull ChangesBrowserNode<?> createPathNode(@NotNull StaticFilePath path) {
    return ChangesBrowserNode.createFilePath(path.getFilePath());
  }

  public boolean isEmpty() {
    return myModel.getChildCount(myRoot) == 0;
  }
}
