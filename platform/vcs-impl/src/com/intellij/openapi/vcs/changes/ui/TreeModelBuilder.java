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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.*;

@SuppressWarnings("UnusedReturnValue")
public class TreeModelBuilder {
  @NonNls private static final String ROOT_NODE_VALUE = "root";

  private static final int UNVERSIONED_MAX_SIZE = 50;

  @NotNull protected final Project myProject;
  protected final boolean myShowFlatten;
  @NotNull protected final DefaultTreeModel myModel;
  @NotNull protected final ChangesBrowserNode myRoot;
  @NotNull private final Map<ChangesBrowserNode, ChangesGroupingPolicy> myGroupingPoliciesCache;
  @NotNull private final Map<ChangesBrowserNode, Map<String, ChangesBrowserNode>> myFoldersCache;

  @SuppressWarnings("unchecked")
  private static final Comparator<ChangesBrowserNode> BROWSER_NODE_COMPARATOR = (node1, node2) -> {
    int sortWeightDiff = Comparing.compare(node1.getSortWeight(), node2.getSortWeight());
    if (sortWeightDiff != 0) return sortWeightDiff;

    if (node1 instanceof Comparable && node1.getClass().equals(node2.getClass())) {
      return ((Comparable)node1).compareTo(node2);
    }
    return node1.compareUserObjects(node2.getUserObject());
  };

  protected final static Comparator<Change> PATH_LENGTH_COMPARATOR = (o1, o2) -> {
    FilePath fp1 = ChangesUtil.getFilePath(o1);
    FilePath fp2 = ChangesUtil.getFilePath(o2);

    return Comparing.compare(fp1.getPath().length(), fp2.getPath().length());
  };


  public TreeModelBuilder(@NotNull Project project, boolean showFlatten) {
    myProject = project;
    myShowFlatten = showFlatten;
    myRoot = ChangesBrowserNode.create(myProject, ROOT_NODE_VALUE);
    myModel = new DefaultTreeModel(myRoot);
    myGroupingPoliciesCache = new MyGroupingPolicyFactoryMap(myProject, myModel);
    myFoldersCache = new HashMap<>();
  }


  @NotNull
  public static DefaultTreeModel buildEmpty(@NotNull Project project) {
    return new DefaultTreeModel(ChangesBrowserNode.create(project, ROOT_NODE_VALUE));
  }

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
  public static DefaultTreeModel buildFromFilePaths(@NotNull Project project,
                                                    boolean showFlatten,
                                                    @NotNull Collection<FilePath> filePaths) {
    return new TreeModelBuilder(project, showFlatten)
      .setFilePaths(filePaths)
      .build();
  }

  @NotNull
  public static DefaultTreeModel buildFromChangeLists(@NotNull Project project,
                                                      boolean showFlatten,
                                                      @NotNull Collection<? extends ChangeList> changeLists) {
    return new TreeModelBuilder(project, showFlatten)
      .setChangeLists(changeLists)
      .build();
  }

  @NotNull
  public static DefaultTreeModel buildFromVirtualFiles(@NotNull Project project,
                                                       boolean showFlatten,
                                                       @NotNull Collection<VirtualFile> virtualFiles) {
    return new TreeModelBuilder(project, showFlatten)
      .setVirtualFiles(virtualFiles, null)
      .build();
  }


  @NotNull
  public TreeModelBuilder setChanges(@NotNull Collection<? extends Change> changes, @Nullable ChangeNodeDecorator changeNodeDecorator) {
    List<? extends Change> sortedChanges = ContainerUtil.sorted(changes, PATH_LENGTH_COMPARATOR);
    for (Change change : sortedChanges) {
      insertChangeNode(change, myRoot, createChangeNode(change, changeNodeDecorator));
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setUnversioned(@Nullable List<VirtualFile> unversionedFiles) {
    if (ContainerUtil.isEmpty(unversionedFiles)) return this;
    int dirsCount = ContainerUtil.count(unversionedFiles, it -> it.isDirectory());
    int filesCount = unversionedFiles.size() - dirsCount;
    boolean manyFiles = unversionedFiles.size() > UNVERSIONED_MAX_SIZE;
    ChangesBrowserUnversionedFilesNode node = new ChangesBrowserUnversionedFilesNode(myProject, filesCount, dirsCount, manyFiles);
    return insertSpecificNodeToModel(unversionedFiles, node);
  }

  @NotNull
  public TreeModelBuilder setIgnored(@Nullable List<VirtualFile> ignoredFiles, boolean updatingMode) {
    if (ContainerUtil.isEmpty(ignoredFiles)) return this;
    int dirsCount = ContainerUtil.count(ignoredFiles, it -> it.isDirectory());
    int filesCount = ignoredFiles.size() - dirsCount;
    boolean manyFiles = ignoredFiles.size() > UNVERSIONED_MAX_SIZE;
    ChangesBrowserIgnoredFilesNode node = new ChangesBrowserIgnoredFilesNode(myProject, filesCount, dirsCount, manyFiles, updatingMode);
    return insertSpecificNodeToModel(ignoredFiles, node);
  }

  @NotNull
  private TreeModelBuilder insertSpecificNodeToModel(@NotNull List<VirtualFile> specificFiles,
                                                     @NotNull ChangesBrowserSpecificFilesNode node) {
    myModel.insertNodeInto(node, myRoot, myRoot.getChildCount());
    if (!node.isManyFiles()) {
      insertFilesIntoNode(specificFiles, node);
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setChangeLists(@NotNull Collection<? extends ChangeList> changeLists) {
    final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
    for (ChangeList list : changeLists) {
      List<Change> changes = ContainerUtil.sorted(list.getChanges(), PATH_LENGTH_COMPARATOR);
      ChangeListRemoteState listRemoteState = new ChangeListRemoteState(changes.size());
      ChangesBrowserChangeListNode listNode = new ChangesBrowserChangeListNode(myProject, list, listRemoteState);
      myModel.insertNodeInto(listNode, myRoot, 0);

      for (int i = 0; i < changes.size(); i++) {
        Change change = changes.get(i);
        RemoteStatusChangeNodeDecorator decorator = new RemoteStatusChangeNodeDecorator(revisionsCache, listRemoteState, i);
        insertChangeNode(change, listNode, createChangeNode(change, decorator));
      }
    }
    return this;
  }

  protected ChangesBrowserNode createChangeNode(Change change, ChangeNodeDecorator decorator) {
    return new ChangesBrowserChangeNode(myProject, change, decorator);
  }

  @NotNull
  public TreeModelBuilder setLockedFolders(@Nullable List<VirtualFile> lockedFolders) {
    return setVirtualFiles(lockedFolders, ChangesBrowserNode.LOCKED_FOLDERS_TAG);
  }

  @NotNull
  public TreeModelBuilder setModifiedWithoutEditing(@NotNull List<VirtualFile> modifiedWithoutEditing) {
    return setVirtualFiles(modifiedWithoutEditing, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
  }

  @NotNull
  private TreeModelBuilder setVirtualFiles(@Nullable Collection<VirtualFile> files, @Nullable Object tag) {
    if (ContainerUtil.isEmpty(files)) return this;
    insertFilesIntoNode(files, createTagNode(tag));
    return this;
  }

  @NotNull
  private ChangesBrowserNode createTagNode(@Nullable Object tag) {
    if (tag == null) return myRoot;

    ChangesBrowserNode subtreeRoot = ChangesBrowserNode.create(myProject, tag);
    myModel.insertNodeInto(subtreeRoot, myRoot, myRoot.getChildCount());
    return subtreeRoot;
  }

  private void insertFilesIntoNode(@NotNull Collection<VirtualFile> files, @NotNull ChangesBrowserNode subtreeRoot) {
    List<VirtualFile> sortedFiles = ContainerUtil.sorted(files, VirtualFileHierarchicalComparator.getInstance());
    for (VirtualFile file : sortedFiles) {
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.create(myProject, file));
    }
  }

  @NotNull
  public TreeModelBuilder setLocallyDeletedPaths(@Nullable Collection<LocallyDeletedChange> locallyDeletedChanges) {
    if (ContainerUtil.isEmpty(locallyDeletedChanges)) return this;
    ChangesBrowserNode subtreeRoot = createTagNode(ChangesBrowserNode.LOCALLY_DELETED_NODE_TAG);

    for (LocallyDeletedChange change : locallyDeletedChanges) {
      // whether a folder does not matter
      final StaticFilePath key = new StaticFilePath(false, change.getPresentableUrl(), change.getPath().getVirtualFile());
      ChangesBrowserNode oldNode = getFolderCache(subtreeRoot).get(key.getKey());
      if (oldNode == null) {
        ChangesBrowserNode node = ChangesBrowserNode.create(change);
        ChangesBrowserNode parent = getParentNodeFor(key, subtreeRoot);
        myModel.insertNodeInto(node, parent, parent.getChildCount());
        getFolderCache(subtreeRoot).put(key.getKey(), node);
      }
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setFilePaths(@NotNull Collection<FilePath> filePaths) {
    return setFilePaths(filePaths, myRoot);
  }

  @NotNull
  private TreeModelBuilder setFilePaths(@NotNull Collection<FilePath> filePaths, @NotNull ChangesBrowserNode subtreeRoot) {
    for (FilePath file : filePaths) {
      assert file != null;
      // whether a folder does not matter
      final String path = file.getPath();
      final StaticFilePath pathKey = ! FileUtil.isAbsolute(path) || VcsUtil.isPathRemote(path) ?
                                     new StaticFilePath(false, path, null) :
                                     new StaticFilePath(false, new File(file.getIOFile().getPath().replace('\\', '/')).getAbsolutePath(), file.getVirtualFile());
      ChangesBrowserNode oldNode = getFolderCache(subtreeRoot).get(pathKey.getKey());
      if (oldNode == null) {
        final ChangesBrowserNode node = ChangesBrowserNode.create(myProject, file);
        final ChangesBrowserNode parentNode = getParentNodeFor(pathKey, subtreeRoot);
        myModel.insertNodeInto(node, parentNode, 0);
        // we could also ask whether a file or directory, though for deleted files not a good idea
        getFolderCache(subtreeRoot).put(pathKey.getKey(), node);
      }
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setSwitchedRoots(@Nullable Map<VirtualFile, String> switchedRoots) {
    if (ContainerUtil.isEmpty(switchedRoots)) return this;
    final ChangesBrowserNode rootsHeadNode = createTagNode(ChangesBrowserNode.SWITCHED_ROOTS_TAG);
    rootsHeadNode.setAttributes(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);

    List<VirtualFile> files = ContainerUtil.sorted(switchedRoots.keySet(), VirtualFileHierarchicalComparator.getInstance());

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
      List<VirtualFile> switchedFileList = ContainerUtil.sorted(switchedFiles.get(branchName),
                                                                VirtualFileHierarchicalComparator.getInstance());
      if (switchedFileList.size() > 0) {
        ChangesBrowserNode branchNode = ChangesBrowserNode.create(myProject, branchName);
        myModel.insertNodeInto(branchNode, subtreeRoot, subtreeRoot.getChildCount());

        for (VirtualFile file : switchedFileList) {
          insertChangeNode(file, branchNode, ChangesBrowserNode.create(myProject, file));
        }
      }
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setLogicallyLockedFiles(@Nullable Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    if (ContainerUtil.isEmpty(logicallyLockedFiles)) return this;
    final ChangesBrowserNode subtreeRoot = createTagNode(ChangesBrowserNode.LOGICALLY_LOCKED_TAG);

    List<VirtualFile> keys = ContainerUtil.sorted(logicallyLockedFiles.keySet(), VirtualFileHierarchicalComparator.getInstance());

    for (VirtualFile file : keys) {
      final LogicalLock lock = logicallyLockedFiles.get(file);
      final ChangesBrowserLogicallyLockedFile obj = new ChangesBrowserLogicallyLockedFile(myProject, file, lock);
      insertChangeNode(obj, subtreeRoot, ChangesBrowserNode.create(myProject, obj));
    }
    return this;
  }

  protected void insertChangeNode(@NotNull Object change,
                                  @NotNull ChangesBrowserNode subtreeRoot,
                                  @NotNull ChangesBrowserNode node) {
    insertChangeNode(change, subtreeRoot, node, this::createPathNode);
  }

  protected void insertChangeNode(@NotNull Object change,
                                  @NotNull ChangesBrowserNode subtreeRoot,
                                  @NotNull ChangesBrowserNode node,
                                  @NotNull Convertor<StaticFilePath, ChangesBrowserNode> nodeBuilder) {
    final StaticFilePath pathKey = getKey(change);
    ChangesBrowserNode parentNode = getParentNodeFor(pathKey, subtreeRoot, nodeBuilder);
    myModel.insertNodeInto(node, parentNode, myModel.getChildCount(parentNode));

    if (pathKey.isDirectory()) {
      getFolderCache(subtreeRoot).put(pathKey.getKey(), node);
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
    if (node.getUserObject() instanceof FilePath && node.getChildCount() == 1) {
      final ChangesBrowserNode child = (ChangesBrowserNode)node.getChildAt(0);
      if (child.getUserObject() instanceof FilePath && !child.isLeaf()) {
        ChangesBrowserNode parent = (ChangesBrowserNode)node.getParent();
        final int idx = parent.getIndex(node);
        model.removeNodeFromParent(node);
        model.removeNodeFromParent(child);
        model.insertNodeInto(child, parent, idx);
        collapseDirectories(model, parent);
      }
    }
    else {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        ChangesBrowserNode child = (ChangesBrowserNode)children.nextElement();
        collapseDirectories(model, child);
      }
    }
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
    else if (o instanceof ChangesBrowserLogicallyLockedFile) {
      return staticFrom(((ChangesBrowserLogicallyLockedFile)o).getUserObject());
    }
    else if (o instanceof LocallyDeletedChange) {
      return staticFrom(((LocallyDeletedChange)o).getPath());
    }

    throw new IllegalArgumentException("Unknown type - " + o.getClass());
  }

  @NotNull
  private static StaticFilePath staticFrom(@NotNull FilePath fp) {
    final String path = fp.getPath();
    if (fp.isNonLocal() && (! FileUtil.isAbsolute(path) || VcsUtil.isPathRemote(path))) {
      return new StaticFilePath(fp.isDirectory(), fp.getIOFile().getPath().replace('\\', '/'), fp.getVirtualFile());
    }
    return new StaticFilePath(fp.isDirectory(), new File(fp.getIOFile().getPath().replace('\\', '/')).getAbsolutePath(), fp.getVirtualFile());
  }

  @NotNull
  private static StaticFilePath staticFrom(@NotNull VirtualFile vf) {
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
    else if (o instanceof ChangesBrowserLogicallyLockedFile) {
      return VcsUtil.getFilePath(((ChangesBrowserLogicallyLockedFile)o).getUserObject());
    }
    else if (o instanceof LocallyDeletedChange) {
      return ((LocallyDeletedChange)o).getPath();
    }

    throw new IllegalArgumentException("Unknown type - " + o.getClass());
  }

  @NotNull
  protected ChangesBrowserNode getParentNodeFor(@NotNull StaticFilePath nodePath,
                                                @NotNull ChangesBrowserNode subtreeRoot) {
    return getParentNodeFor(nodePath, subtreeRoot, this::createPathNode);
  }

  @NotNull
  protected ChangesBrowserNode getParentNodeFor(@NotNull StaticFilePath nodePath,
                                                @NotNull ChangesBrowserNode subtreeRoot,
                                                @NotNull Convertor<StaticFilePath, ChangesBrowserNode> nodeBuilder) {
    if (myShowFlatten) {
      return subtreeRoot;
    }

    ChangesGroupingPolicy policy = myGroupingPoliciesCache.get(subtreeRoot);
    if (policy != null) {
      ChangesBrowserNode nodeFromPolicy = policy.getParentNodeFor(nodePath, subtreeRoot);
      if (nodeFromPolicy != null) {
        return nodeFromPolicy;
      }
    }

    StaticFilePath parentPath = nodePath.getParent();
    while (parentPath != null) {
      ChangesBrowserNode oldParentNode = getFolderCache(subtreeRoot).get(parentPath.getKey());
      if (oldParentNode != null) return oldParentNode;

      ChangesBrowserNode parentNode = nodeBuilder.convert(parentPath);
      if (parentNode != null) {
        ChangesBrowserNode grandPa = getParentNodeFor(parentPath, subtreeRoot, nodeBuilder);
        myModel.insertNodeInto(parentNode, grandPa, grandPa.getChildCount());
        getFolderCache(subtreeRoot).put(parentPath.getKey(), parentNode);
        return parentNode;
      }

      parentPath = parentPath.getParent();
    }

    return subtreeRoot;
  }

  @Nullable
  private ChangesBrowserNode createPathNode(@NotNull StaticFilePath path) {
    FilePath filePath = path.getVf() == null ? VcsUtil.getFilePath(path.getPath(), true) : VcsUtil.getFilePath(path.getVf());
    return ChangesBrowserNode.create(myProject, filePath);
  }

  @NotNull
  private Map<String, ChangesBrowserNode> getFolderCache(@NotNull ChangesBrowserNode subtreeRoot) {
    return myFoldersCache.computeIfAbsent(subtreeRoot, (key) -> new HashMap<>());
  }

  public boolean isEmpty() {
    return myModel.getChildCount(myRoot) == 0;
  }

  @NotNull
  @Deprecated
  public DefaultTreeModel buildModel(@NotNull List<Change> changes, @Nullable ChangeNodeDecorator changeNodeDecorator) {
    return setChanges(changes, changeNodeDecorator).build();
  }


  private static class MyGroupingPolicyFactoryMap extends FactoryMap<ChangesBrowserNode, ChangesGroupingPolicy> {
    @NotNull private final Project myProject;
    @NotNull private final DefaultTreeModel myModel;

    public MyGroupingPolicyFactoryMap(@NotNull Project project, @NotNull DefaultTreeModel model) {
      myProject = project;
      myModel = model;
    }

    @Nullable
    @Override
    protected ChangesGroupingPolicy create(ChangesBrowserNode key) {
      ChangesGroupingPolicyFactory factory = ChangesGroupingPolicyFactory.getInstance(myProject);
      return factory != null ? factory.createGroupingPolicy(myModel) : null;
    }
  }
}
