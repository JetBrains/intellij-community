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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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

  @NotNull private final Project myProject;
  private final boolean myShowFlatten;
  @NotNull private final DefaultTreeModel myModel;
  @NotNull private final ChangesBrowserNode myRoot;
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

  private final static Comparator<Change> PATH_LENGTH_COMPARATOR = (o1, o2) -> {
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

    for (final Change change : sortedChanges) {
      insertChangeNode(change, myRoot, createChangeNode(change, changeNodeDecorator));
    }

    return this;
  }

  @NotNull
  public TreeModelBuilder setUnversioned(@NotNull List<VirtualFile> unversionedFiles, @NotNull Couple<Integer> sizes) {
    if (ContainerUtil.isEmpty(unversionedFiles)) return this;
    return insertSpecificNodeToModel(unversionedFiles, new ChangesBrowserUnversionedFilesNode(
      myProject, sizes.getFirst(), sizes.getSecond(), unversionedFiles.size() > UNVERSIONED_MAX_SIZE));
  }

  @NotNull
  public TreeModelBuilder setIgnored(@Nullable List<VirtualFile> ignoredFiles, @NotNull Couple<Integer> sizes, boolean updatingMode) {
    if (ContainerUtil.isEmpty(ignoredFiles)) return this;
    return insertSpecificNodeToModel(ignoredFiles,
                                     new ChangesBrowserIgnoredFilesNode(myProject, sizes.getFirst(), sizes.getSecond(),
                                                                        ignoredFiles.size() > UNVERSIONED_MAX_SIZE, updatingMode));
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
  public TreeModelBuilder set(@NotNull List<? extends ChangeList> changeLists,
                              @NotNull List<LocallyDeletedChange> locallyDeletedFiles,
                              @NotNull List<VirtualFile> modifiedWithoutEditing,
                              @NotNull MultiMap<String, VirtualFile> switchedFiles,
                              @Nullable Map<VirtualFile, String> switchedRoots,
                              @Nullable List<VirtualFile> lockedFolders,
                              @Nullable Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    setChangeLists(changeLists);

    if (!modifiedWithoutEditing.isEmpty()) {
      setVirtualFiles(modifiedWithoutEditing, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
    }
    if (switchedRoots != null && ! switchedRoots.isEmpty()) {
      setSwitchedRoots(switchedRoots);
    }
    if (!switchedFiles.isEmpty()) {
      setSwitchedFiles(switchedFiles);
    }
    if (lockedFolders != null && !lockedFolders.isEmpty()) {
      setVirtualFiles(lockedFolders, ChangesBrowserNode.LOCKED_FOLDERS_TAG);
    }
    if (logicallyLockedFiles != null && ! logicallyLockedFiles.isEmpty()) {
      setLogicallyLockedFiles(logicallyLockedFiles);
    }

    if (!locallyDeletedFiles.isEmpty()) {
      setLocallyDeletedPaths(locallyDeletedFiles);
    }

    return this;
  }

  @NotNull
  public DefaultTreeModel build() {
    collapseDirectories(myModel, myRoot);
    sortNodes();

    return myModel;
  }

  @NotNull
  public TreeModelBuilder setChangeLists(@NotNull Collection<? extends ChangeList> changeLists) {
    final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
    for (ChangeList list : changeLists) {
      final List<Change> changes = new ArrayList<>(list.getChanges());
      ChangesBrowserChangeListNode listNode = createChangeListNode(list, changes);
      myModel.insertNodeInto(listNode, myRoot, 0);
      Collections.sort(changes, PATH_LENGTH_COMPARATOR);
      for (int i = 0; i < changes.size(); i++) {
        insertChangeNode(changes.get(i), listNode, createChangeListChild(revisionsCache, listNode, changes, i));
      }
    }
    return this;
  }

  protected ChangesBrowserChangeListNode createChangeListNode(@NotNull ChangeList list, List<Change> changes) {
    final ChangeListRemoteState listRemoteState = new ChangeListRemoteState(changes.size());
    return new ChangesBrowserChangeListNode(myProject, list, listRemoteState);
  }

  protected ChangesBrowserNode createChangeListChild(RemoteRevisionsCache revisionsCache, ChangesBrowserChangeListNode node, List<Change> changes, int i) {
    return createChangeNode(changes.get(i), new RemoteStatusChangeNodeDecorator(revisionsCache) {
      @NotNull private final ChangeListRemoteState.Reporter myReporter =
        new ChangeListRemoteState.Reporter(i, node.getChangeListRemoteState());

      @Override
      protected void reportState(boolean state) {
        myReporter.report(state);
      }

      @Override
      public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten1) {
      }
    });
  }

  protected ChangesBrowserNode createChangeNode(Change change, ChangeNodeDecorator decorator) {
    return new ChangesBrowserChangeNode(myProject, change, decorator);
  }

  @NotNull
  public TreeModelBuilder setVirtualFiles(@NotNull Collection<VirtualFile> files, @Nullable Object tag) {
    insertFilesIntoNode(files, createNode(tag));
    return this;
  }

  @NotNull
  private ChangesBrowserNode createNode(@Nullable Object tag) {
    ChangesBrowserNode result;

    if (tag != null) {
      result = ChangesBrowserNode.create(myProject, tag);
      myModel.insertNodeInto(result, myRoot, myRoot.getChildCount());
    }
    else {
      result = myRoot;
    }

    return result;
  }

  private void insertFilesIntoNode(@NotNull Collection<VirtualFile> files, @NotNull ChangesBrowserNode subtreeRoot) {
    List<VirtualFile> sortedFiles = ContainerUtil.sorted(files, VirtualFileHierarchicalComparator.getInstance());

    for (VirtualFile file : sortedFiles) {
      insertChangeNode(file, subtreeRoot, ChangesBrowserNode.create(myProject, file));
    }
  }

  @NotNull
  public TreeModelBuilder setLocallyDeletedPaths(@NotNull Collection<LocallyDeletedChange> locallyDeletedChanges) {
    ChangesBrowserNode subtreeRoot = ChangesBrowserNode.create(myProject, ChangesBrowserNode.LOCALLY_DELETED_NODE_TAG);
    myModel.insertNodeInto(subtreeRoot, myRoot, myRoot.getChildCount());

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
  public TreeModelBuilder setSwitchedRoots(@NotNull Map<VirtualFile, String> switchedRoots) {
    final ChangesBrowserNode rootsHeadNode = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_ROOTS_TAG);
    rootsHeadNode.setAttributes(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    myModel.insertNodeInto(rootsHeadNode, myRoot, myRoot.getChildCount());

    final List<VirtualFile> files = new ArrayList<>(switchedRoots.keySet());
    Collections.sort(files, VirtualFileHierarchicalComparator.getInstance());

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
    ChangesBrowserNode subtreeRoot = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_FILES_TAG);
    myModel.insertNodeInto(subtreeRoot, myRoot, myRoot.getChildCount());
    for(String branchName: switchedFiles.keySet()) {
      final List<VirtualFile> switchedFileList = new ArrayList<>(switchedFiles.get(branchName));
      if (switchedFileList.size() > 0) {
        ChangesBrowserNode branchNode = ChangesBrowserNode.create(myProject, branchName);
        myModel.insertNodeInto(branchNode, subtreeRoot, subtreeRoot.getChildCount());

        Collections.sort(switchedFileList, VirtualFileHierarchicalComparator.getInstance());
        for (VirtualFile file : switchedFileList) {
          insertChangeNode(file, branchNode, ChangesBrowserNode.create(myProject, file));
        }
      }
    }
    return this;
  }

  @NotNull
  public TreeModelBuilder setLogicallyLockedFiles(@NotNull Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    final ChangesBrowserNode subtreeRoot = createNode(ChangesBrowserNode.LOGICALLY_LOCKED_TAG);

    final List<VirtualFile> keys = new ArrayList<>(logicallyLockedFiles.keySet());
    Collections.sort(keys, VirtualFileHierarchicalComparator.getInstance());

    for (final VirtualFile file : keys) {
      final LogicalLock lock = logicallyLockedFiles.get(file);
      final ChangesBrowserLogicallyLockedFile obj = new ChangesBrowserLogicallyLockedFile(myProject, file, lock);
      insertChangeNode(obj, subtreeRoot, ChangesBrowserNode.create(myProject, obj));
    }
    return this;
  }

  protected void insertChangeNode(@NotNull Object change,
                                  @NotNull ChangesBrowserNode subtreeRoot,
                                  @NotNull ChangesBrowserNode node) {
    final StaticFilePath pathKey = getKey(change);
    ChangesBrowserNode parentNode = getParentNodeFor(pathKey, subtreeRoot);
    myModel.insertNodeInto(node, parentNode, myModel.getChildCount(parentNode));

    if (pathKey.isDirectory()) {
      getFolderCache(subtreeRoot).put(pathKey.getKey(), node);
    }
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
      return staticFrom(ChangesUtil.getFilePath((Change) o));
    }
    else if (o instanceof VirtualFile) {
      return staticFrom((VirtualFile) o);
    }
    else if (o instanceof FilePath) {
      return staticFrom((FilePath) o);
    } else if (o instanceof ChangesBrowserLogicallyLockedFile) {
      return staticFrom(((ChangesBrowserLogicallyLockedFile) o).getUserObject());
    } else if (o instanceof LocallyDeletedChange) {
      return staticFrom(((LocallyDeletedChange) o).getPath());
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
      return VcsUtil.getFilePath((VirtualFile) o);
    }
    else if (o instanceof FilePath) {
      return (FilePath)o;
    } else if (o instanceof ChangesBrowserLogicallyLockedFile) {
      return VcsUtil.getFilePath(((ChangesBrowserLogicallyLockedFile) o).getUserObject());
    } else if (o instanceof LocallyDeletedChange) {
      return ((LocallyDeletedChange) o).getPath();
    }

    throw new IllegalArgumentException("Unknown type - " + o.getClass());
  }

  @NotNull
  private ChangesBrowserNode getParentNodeFor(@NotNull StaticFilePath nodePath,
                                              @NotNull ChangesBrowserNode subtreeRoot) {
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

    final StaticFilePath parentPath = nodePath.getParent();
    if (parentPath == null) {
      return subtreeRoot;
    }

    ChangesBrowserNode parentNode = getFolderCache(subtreeRoot).get(parentPath.getKey());
    if (parentNode == null) {
      parentNode = createPathNode(parentPath);
      if (parentNode != null) {
        ChangesBrowserNode grandPa = getParentNodeFor(parentPath, subtreeRoot);
        myModel.insertNodeInto(parentNode, grandPa, grandPa.getChildCount());
        getFolderCache(subtreeRoot).put(parentPath.getKey(), parentNode);
      }
    }

    return ObjectUtils.chooseNotNull(parentNode, subtreeRoot);
  }

  @Nullable
  protected ChangesBrowserNode createPathNode(StaticFilePath parentPath) {
    FilePath filePath = parentPath.getVf() == null ? VcsUtil.getFilePath(parentPath.getPath(), true) : VcsUtil.getFilePath(parentPath.getVf());
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
