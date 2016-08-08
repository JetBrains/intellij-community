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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
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

public class TreeModelBuilder {
  @NonNls public static final String ROOT_NODE_VALUE = "root";
  public static final String LOCALLY_DELETED_NODE = VcsBundle.message("changes.nodetitle.locally.deleted.files");

  private static final int UNVERSIONED_MAX_SIZE = 50;

  @NotNull private final Project myProject;
  private final boolean showFlatten;
  @NotNull private DefaultTreeModel model;
  @NotNull private final ChangesBrowserNode root;
  private boolean myPolicyInitialized;
  @Nullable private ChangesGroupingPolicy myPolicy;
  @NotNull private HashMap<String, ChangesBrowserNode> myFoldersCache;

  public TreeModelBuilder(@NotNull Project project, final boolean showFlatten) {
    myProject = project;
    this.showFlatten = showFlatten;
    root = ChangesBrowserNode.create(myProject, ROOT_NODE_VALUE);
    model = new DefaultTreeModel(root);
    myFoldersCache = new HashMap<>();
  }

  @NotNull
  public DefaultTreeModel buildModel(@NotNull List<Change> changes, @Nullable final ChangeNodeDecorator changeNodeDecorator) {
    return setChanges(changes, changeNodeDecorator).build();
  }

  @NotNull
  public TreeModelBuilder setChanges(@NotNull List<Change> changes, @Nullable final ChangeNodeDecorator changeNodeDecorator) {
    Collections.sort(changes, MyChangePathLengthComparator.getInstance());

    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (final Change change : changes) {
      insertChangeNode(change, policy, root, new Computable<ChangesBrowserNode>() {
        @Override
        public ChangesBrowserNode compute() {
          return new ChangesBrowserChangeNode(myProject, change, changeNodeDecorator);
        }
      });
    }

    return this;
  }

  @NotNull
  public TreeModelBuilder setUnversioned(@NotNull List<VirtualFile> unversionedFiles, @NotNull Couple<Integer> sizes) {
    if (!unversionedFiles.isEmpty()) {
      resetGrouping();

      ChangesBrowserUnversionedFilesNode node = new ChangesBrowserUnversionedFilesNode(
        myProject, sizes.getFirst(), sizes.getSecond(), unversionedFiles.size() > UNVERSIONED_MAX_SIZE);
      model.insertNodeInto(node, root, root.getChildCount());

      if (!node.isManyUnversioned()) {
        insertFilesIntoNode(unversionedFiles, node);
      }
    }

    return this;
  }

  @Nullable
  private ChangesGroupingPolicy createGroupingPolicy() {
    if (! myPolicyInitialized) {
      myPolicyInitialized = true;
      final ChangesGroupingPolicyFactory factory = ChangesGroupingPolicyFactory.getInstance(myProject);
      if (factory != null) {
        myPolicy = factory.createGroupingPolicy(model);
      }
    }
    return myPolicy;
  }

  @NotNull
  public DefaultTreeModel buildModelFromFiles(@NotNull List<VirtualFile> files) {
    buildVirtualFiles(files, null);

    return build();
  }

  @NotNull
  public DefaultTreeModel buildModelFromFilePaths(@NotNull Collection<FilePath> files) {
    buildFilePaths(files, root);

    return build();
  }

  private static class MyChangeNodeUnderChangeListDecorator extends RemoteStatusChangeNodeDecorator {
    @NotNull private final ChangeListRemoteState.Reporter myReporter;

    private MyChangeNodeUnderChangeListDecorator(final RemoteRevisionsCache remoteRevisionsCache,
                                                 @NotNull ChangeListRemoteState.Reporter reporter) {
      super(remoteRevisionsCache);
      myReporter = reporter;
    }

    @Override
    protected void reportState(boolean state) {
      myReporter.report(state);
    }

    @Override
    public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
    }
  }

  @NotNull
  public TreeModelBuilder set(@NotNull List<? extends ChangeList> changeLists,
                              @NotNull List<LocallyDeletedChange> locallyDeletedFiles,
                              @NotNull List<VirtualFile> modifiedWithoutEditing,
                              @NotNull MultiMap<String, VirtualFile> switchedFiles,
                              @Nullable Map<VirtualFile, String> switchedRoots,
                              @Nullable List<VirtualFile> ignoredFiles,
                              @Nullable List<VirtualFile> lockedFolders,
                              @Nullable Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    resetGrouping();
    buildModel(changeLists);

    if (!modifiedWithoutEditing.isEmpty()) {
      resetGrouping();
      buildVirtualFiles(modifiedWithoutEditing, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
    }
    if (switchedRoots != null && ! switchedRoots.isEmpty()) {
      resetGrouping();
      buildSwitchedRoots(switchedRoots);
    }
    if (!switchedFiles.isEmpty()) {
      resetGrouping();
      buildSwitchedFiles(switchedFiles);
    }
    if (ignoredFiles != null && !ignoredFiles.isEmpty()) {
      resetGrouping();
      buildVirtualFiles(ignoredFiles, ChangesBrowserNode.IGNORED_FILES_TAG);
    }
    if (lockedFolders != null && !lockedFolders.isEmpty()) {
      resetGrouping();
      buildVirtualFiles(lockedFolders, ChangesBrowserNode.LOCKED_FOLDERS_TAG);
    }
    if (logicallyLockedFiles != null && ! logicallyLockedFiles.isEmpty()) {
      resetGrouping();
      buildLogicallyLockedFiles(logicallyLockedFiles);
    }

    if (!locallyDeletedFiles.isEmpty()) {
      resetGrouping();
      ChangesBrowserNode locallyDeletedNode = ChangesBrowserNode.create(myProject, LOCALLY_DELETED_NODE);
      model.insertNodeInto(locallyDeletedNode, root, root.getChildCount());
      buildLocallyDeletedPaths(locallyDeletedFiles, locallyDeletedNode);
    }

    return this;
  }

  @NotNull
  public DefaultTreeModel build() {
    collapseDirectories(model, root);
    sortNodes();

    return model;
  }

  private void resetGrouping() {
    myFoldersCache = new HashMap<>();
    myPolicyInitialized = false;
  }

  @NotNull
  public DefaultTreeModel buildModel(@NotNull List<? extends ChangeList> changeLists) {
    final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
    for (ChangeList list : changeLists) {
      final List<Change> changes = new ArrayList<>(list.getChanges());
      final ChangeListRemoteState listRemoteState = new ChangeListRemoteState(changes.size());
      ChangesBrowserNode listNode = new ChangesBrowserChangeListNode(myProject, list, listRemoteState);
      model.insertNodeInto(listNode, root, 0);
      resetGrouping();
      final ChangesGroupingPolicy policy = createGroupingPolicy();
      int i = 0;
      Collections.sort(changes, MyChangePathLengthComparator.getInstance());
      for (final Change change : changes) {
        final MyChangeNodeUnderChangeListDecorator decorator =
          new MyChangeNodeUnderChangeListDecorator(revisionsCache, new ChangeListRemoteState.Reporter(i, listRemoteState));
        insertChangeNode(change, policy, listNode, new Computable<ChangesBrowserNode>() {
          @Override
          public ChangesBrowserNode compute() {
            return new ChangesBrowserChangeNode(myProject, change, decorator);
          }
        });
        ++ i;
      }
    }
    return model;
  }

  @NotNull
  public TreeModelBuilder setChangeLists(@NotNull List<? extends ChangeList> changeLists) {
    buildModel(changeLists);
    return this;
  }

  private static class MyChangePathLengthComparator implements Comparator<Change> {
    private final static MyChangePathLengthComparator ourInstance = new MyChangePathLengthComparator();

    @NotNull
    public static MyChangePathLengthComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(@NotNull Change o1, @NotNull Change o2) {
      FilePath fp1 = ChangesUtil.getFilePath(o1);
      FilePath fp2 = ChangesUtil.getFilePath(o2);

      return Comparing.compare(fp1.getPath().length(), fp2.getPath().length());
    }
  }

  private void buildVirtualFiles(@NotNull List<VirtualFile> files, @Nullable Object tag) {
    insertFilesIntoNode(files, createNode(tag));
  }

  @NotNull
  private ChangesBrowserNode createNode(@Nullable Object tag) {
    ChangesBrowserNode result;

    if (tag != null) {
      result = ChangesBrowserNode.create(myProject, tag);
      model.insertNodeInto(result, root, root.getChildCount());
    }
    else {
      result = root;
    }

    return result;
  }

  private void insertFilesIntoNode(@NotNull List<VirtualFile> files, @NotNull ChangesBrowserNode baseNode) {
    ChangesGroupingPolicy policy = createGroupingPolicy();
    Collections.sort(files, VirtualFileHierarchicalComparator.getInstance());
    
    for (VirtualFile file : files) {
      insertChangeNode(file, policy, baseNode, defaultNodeCreator(file));
    }
  }

  private void buildLocallyDeletedPaths(@NotNull Collection<LocallyDeletedChange> locallyDeletedChanges,
                                        @NotNull ChangesBrowserNode baseNode) {
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (LocallyDeletedChange change : locallyDeletedChanges) {
      // whether a folder does not matter
      final StaticFilePath key = new StaticFilePath(false, change.getPresentableUrl(), change.getPath().getVirtualFile());
      ChangesBrowserNode oldNode = myFoldersCache.get(key.getKey());
      if (oldNode == null) {
        ChangesBrowserNode node = ChangesBrowserNode.create(change);
        ChangesBrowserNode parent = getParentNodeFor(key, policy, baseNode);
        model.insertNodeInto(node, parent, parent.getChildCount());
        myFoldersCache.put(key.getKey(), node);
      }
    }
  }

  private void buildFilePaths(@NotNull Collection<FilePath> filePaths, @NotNull ChangesBrowserNode baseNode) {
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (FilePath file : filePaths) {
      assert file != null;
      // whether a folder does not matter
      final String path = file.getPath();
      final StaticFilePath pathKey = ! FileUtil.isAbsolute(path) || VcsUtil.isPathRemote(path) ?
                                     new StaticFilePath(false, path, null) :
                                     new StaticFilePath(false, new File(file.getIOFile().getPath().replace('\\', '/')).getAbsolutePath(), file.getVirtualFile());
      ChangesBrowserNode oldNode = myFoldersCache.get(pathKey.getKey());
      if (oldNode == null) {
        final ChangesBrowserNode node = ChangesBrowserNode.create(myProject, file);
        final ChangesBrowserNode parentNode = getParentNodeFor(pathKey, policy, baseNode);
        model.insertNodeInto(node, parentNode, 0);
        // we could also ask whether a file or directory, though for deleted files not a good idea
        myFoldersCache.put(pathKey.getKey(), node);
      }
    }
  }

  private void buildSwitchedRoots(@NotNull Map<VirtualFile, String> switchedRoots) {
    final ChangesBrowserNode rootsHeadNode = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_ROOTS_TAG);
    rootsHeadNode.setAttributes(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    model.insertNodeInto(rootsHeadNode, root, root.getChildCount());

    final List<VirtualFile> files = new ArrayList<>(switchedRoots.keySet());
    Collections.sort(files, VirtualFileHierarchicalComparator.getInstance());
    
    for (VirtualFile vf : files) {
      final ChangesGroupingPolicy policy = createGroupingPolicy();
      final ContentRevision cr = new CurrentContentRevision(VcsUtil.getFilePath(vf));
      final Change change = new Change(cr, cr, FileStatus.NOT_CHANGED);
      final String branchName = switchedRoots.get(vf);
      insertChangeNode(vf, policy, rootsHeadNode, new Computable<ChangesBrowserNode>() {
        @Override
        public ChangesBrowserNode compute() {
          return new ChangesBrowserChangeNode(myProject, change, new ChangeNodeDecorator() {
            @Override
            public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
            }
            @Override
            public List<Pair<String, Stress>> stressPartsOfFileName(Change change, String parentPath) {
              return null;
            }
            @Override
            public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
              renderer.append("[" + branchName + "] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
            }
          });
        }
      });
    }
  }

  private void buildSwitchedFiles(@NotNull MultiMap<String, VirtualFile> switchedFiles) {
    ChangesBrowserNode baseNode = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_FILES_TAG);
    model.insertNodeInto(baseNode, root, root.getChildCount());
    for(String branchName: switchedFiles.keySet()) {
      final List<VirtualFile> switchedFileList = new ArrayList<>(switchedFiles.get(branchName));
      if (switchedFileList.size() > 0) {
        ChangesBrowserNode branchNode = ChangesBrowserNode.create(myProject, branchName);
        model.insertNodeInto(branchNode, baseNode, baseNode.getChildCount());

        final ChangesGroupingPolicy policy = createGroupingPolicy();
        Collections.sort(switchedFileList, VirtualFileHierarchicalComparator.getInstance());
        for (VirtualFile file : switchedFileList) {
          insertChangeNode(file, policy, branchNode, defaultNodeCreator(file));
        }
      }
    }
  }

  private void buildLogicallyLockedFiles(@NotNull Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    final ChangesBrowserNode baseNode = createNode(ChangesBrowserNode.LOGICALLY_LOCKED_TAG);

    final ChangesGroupingPolicy policy = createGroupingPolicy();
    final List<VirtualFile> keys = new ArrayList<>(logicallyLockedFiles.keySet());
    Collections.sort(keys, VirtualFileHierarchicalComparator.getInstance());

    for (final VirtualFile file : keys) {
      final LogicalLock lock = logicallyLockedFiles.get(file);
      final ChangesBrowserLogicallyLockedFile obj = new ChangesBrowserLogicallyLockedFile(myProject, file, lock);
      insertChangeNode(obj, policy, baseNode, defaultNodeCreator(obj));
    }
  }

  @NotNull
  private Computable<ChangesBrowserNode> defaultNodeCreator(@NotNull final Object change) {
    return new Computable<ChangesBrowserNode>() {
      @Override
      public ChangesBrowserNode compute() {
        return ChangesBrowserNode.create(myProject, change);
      }
    };
  }

  private void insertChangeNode(@NotNull Object change,
                                @Nullable ChangesGroupingPolicy policy,
                                @NotNull ChangesBrowserNode listNode,
                                @NotNull Computable<ChangesBrowserNode> nodeCreator) {
    final StaticFilePath pathKey = getKey(change);
    final ChangesBrowserNode node = nodeCreator.compute();
    ChangesBrowserNode parentNode = getParentNodeFor(pathKey, policy, listNode);
    model.insertNodeInto(node, parentNode, model.getChildCount(parentNode));

    if (pathKey.isDirectory()) {
      myFoldersCache.put(pathKey.getKey(), node);
    }
  }

  private void sortNodes() {
    TreeUtil.sort(model, MyChangesBrowserNodeComparator.getInstance());

    model.nodeStructureChanged((TreeNode)model.getRoot());
  }

  private static class MyChangesBrowserNodeComparator implements Comparator<ChangesBrowserNode> {
    private static final MyChangesBrowserNodeComparator ourInstance = new MyChangesBrowserNodeComparator();

    @NotNull
    public static MyChangesBrowserNodeComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(@NotNull ChangesBrowserNode node1, @NotNull ChangesBrowserNode node2) {
      int sortWeightDiff = Comparing.compare(node1.getSortWeight(), node2.getSortWeight());
      if (sortWeightDiff != 0) return sortWeightDiff;

      if (node1 instanceof Comparable && node1.getClass().equals(node2.getClass())) {
        return ((Comparable)node1).compareTo(node2);
      }
      return node1.compareUserObjects(node2.getUserObject());
    }
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
                                              @Nullable ChangesGroupingPolicy policy,
                                              @NotNull ChangesBrowserNode rootNode) {
    if (showFlatten) {
      return rootNode;
    }

    if (policy != null) {
      ChangesBrowserNode nodeFromPolicy = policy.getParentNodeFor(nodePath, rootNode);
      if (nodeFromPolicy != null) {
        return nodeFromPolicy;
      }
    }

    final StaticFilePath parentPath = nodePath.getParent();
    if (parentPath == null) {
      return rootNode;
    }

    ChangesBrowserNode parentNode = myFoldersCache.get(parentPath.getKey());
    if (parentNode == null) {
      FilePath filePath = parentPath.getVf() == null ? VcsUtil.getFilePath(parentPath.getPath(), true) : VcsUtil.getFilePath(parentPath.getVf());
      parentNode = ChangesBrowserNode.create(myProject, filePath);
      ChangesBrowserNode grandPa = getParentNodeFor(parentPath, policy, rootNode);
      model.insertNodeInto(parentNode, grandPa, grandPa.getChildCount());
      myFoldersCache.put(parentPath.getKey(), parentNode);
    }

    return parentNode;
  }

  @NotNull
  public DefaultTreeModel clearAndGetModel() {
    root.removeAllChildren();
    model = new DefaultTreeModel(root);
    resetGrouping();
    return model;
  }

  public boolean isEmpty() {
    return model.getChildCount(root) == 0;
  }
}
