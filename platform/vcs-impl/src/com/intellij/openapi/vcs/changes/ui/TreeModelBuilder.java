/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.*;

/**
 * @author max
 */
public class TreeModelBuilder {
  @NonNls public static final String ROOT_NODE_VALUE = "root";

  private final Project myProject;
  private final boolean showFlatten;
  private DefaultTreeModel model;
  private final ChangesBrowserNode root;
  private boolean myPolicyInitialized;
  private ChangesGroupingPolicy myPolicy;
  private HashMap<String, ChangesBrowserNode> myFoldersCache;

  public TreeModelBuilder(final Project project, final boolean showFlatten) {
    myProject = project;
    this.showFlatten = showFlatten;
    root = ChangesBrowserNode.create(myProject, ROOT_NODE_VALUE);
    model = new DefaultTreeModel(root);
    myFoldersCache = new HashMap<String, ChangesBrowserNode>();
  }

  public DefaultTreeModel buildModel(final List<Change> changes, final ChangeNodeDecorator changeNodeDecorator) {
    Collections.sort(changes, MyChangePathLengthComparator.getInstance());
    
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (final Change change : changes) {
      insertChangeNode(change, policy, root, new Computable<ChangesBrowserNode>() {
        public ChangesBrowserNode compute() {
          return new ChangesBrowserChangeNode(myProject, change, changeNodeDecorator);
        }
      });
    }

    collapseDirectories(model, root);
    sortNodes();

    return model;
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

  public DefaultTreeModel buildModelFromFiles(final List<VirtualFile> files) {
    buildVirtualFiles(files, null);
    collapseDirectories(model, root);
    sortNodes();
    return model;
  }

  public DefaultTreeModel buildModelFromFilePaths(final Collection<FilePath> files) {
    buildFilePaths(files, root);
    collapseDirectories(model, root);
    sortNodes();
    return model;
  }

  private static class MyChangeNodeUnderChangeListDecorator extends RemoteStatusChangeNodeDecorator {
    private final ChangeListRemoteState.Reporter myReporter;

    private MyChangeNodeUnderChangeListDecorator(final RemoteRevisionsCache remoteRevisionsCache, final ChangeListRemoteState.Reporter reporter) {
      super(remoteRevisionsCache);
      myReporter = reporter;
    }

    @Override
    protected void reportState(boolean state) {
      myReporter.report(state);
    }

    public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
    }
  }

  public DefaultTreeModel buildModel(final List<? extends ChangeList> changeLists,
                                     final Trinity<List<VirtualFile>, Integer, Integer> unversionedFiles,
                                     final List<LocallyDeletedChange> locallyDeletedFiles,
                                     final List<VirtualFile> modifiedWithoutEditing,
                                     final MultiMap<String, VirtualFile> switchedFiles,
                                     @Nullable Map<VirtualFile, String> switchedRoots,
                                     @Nullable final List<VirtualFile> ignoredFiles, @Nullable final List<VirtualFile> lockedFolders,
                                     @Nullable final Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    resetGrouping();
    buildModel(changeLists);

    if (!modifiedWithoutEditing.isEmpty()) {
      resetGrouping();
      buildVirtualFiles(modifiedWithoutEditing, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
    }
    final boolean manyUnversioned = unversionedFiles.getSecond() > unversionedFiles.getFirst().size();
    if (manyUnversioned || (! unversionedFiles.getFirst().isEmpty())) {
      resetGrouping();

      if (manyUnversioned) {
        final ChangesBrowserNode baseNode = new ChangesBrowserManyUnversionedFilesNode(myProject, unversionedFiles.getSecond(), unversionedFiles.getThird());
        model.insertNodeInto(baseNode, root, root.getChildCount());
      } else {
        buildVirtualFiles(unversionedFiles.getFirst(), ChangesBrowserNode.UNVERSIONED_FILES_TAG);
      }
    }
    if (switchedRoots != null && (! switchedRoots.isEmpty())) {
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
    if (logicallyLockedFiles != null && (! logicallyLockedFiles.isEmpty())) {
      resetGrouping();
      buildLogicallyLockedFiles(logicallyLockedFiles);
    }

    if (!locallyDeletedFiles.isEmpty()) {
      resetGrouping();
      ChangesBrowserNode locallyDeletedNode = ChangesBrowserNode.create(myProject, VcsBundle.message("changes.nodetitle.locally.deleted.files"));
      model.insertNodeInto(locallyDeletedNode, root, root.getChildCount());
      buildLocallyDeletedPaths(locallyDeletedFiles, locallyDeletedNode);
    }

    collapseDirectories(model, root);
    sortNodes();

    return model;
  }

  private void resetGrouping() {
    myFoldersCache = new HashMap<String, ChangesBrowserNode>();
    myPolicyInitialized = false;
  }

  public DefaultTreeModel buildModel(List<? extends ChangeList> changeLists) {
    final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
    for (ChangeList list : changeLists) {
      final List<Change> changes = new ArrayList<Change>(list.getChanges());
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
          public ChangesBrowserNode compute() {
            return new ChangesBrowserChangeNode(myProject, change, decorator);
          }
        });
        ++ i;
      }
    }
    return model;
  }

  private static class MyChangePathLengthComparator implements Comparator<Change> {
    private final static MyChangePathLengthComparator ourInstance = new MyChangePathLengthComparator();

    public static MyChangePathLengthComparator getInstance() {
      return ourInstance;
    }

    public int compare(Change o1, Change o2) {
      final FilePath fp1 = ChangesUtil.getFilePath(o1);
      final FilePath fp2 = ChangesUtil.getFilePath(o2);

      final int diff = fp1.getPath().length() - fp2.getPath().length();
      return diff == 0 ? 0 : (diff < 0 ? -1 : 1);
    }
  }

  /*private void buildVirtualFiles(final Iterator<FilePath> iterator, @Nullable final Object tag) {
    final ChangesBrowserNode baseNode = createNode(tag);
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (; ; iterator.hasNext()) {
      final FilePath path = iterator.next();
      insertChangeNode(path.getVirtualFile(), policy, baseNode, defaultNodeCreator(path.getVirtualFile()));
    }
  } */

  private void buildVirtualFiles(final List<VirtualFile> files, @Nullable final Object tag) {
    final ChangesBrowserNode baseNode = createNode(tag);
    insertFilesIntoNode(files, baseNode);
  }

  private ChangesBrowserNode createNode(Object tag) {
    ChangesBrowserNode baseNode;
    if (tag != null) {
      baseNode = ChangesBrowserNode.create(myProject, tag);
      model.insertNodeInto(baseNode, root, root.getChildCount());
    }
    else {
      baseNode = root;
    }
    return baseNode;
  }

  private void insertFilesIntoNode(final List<VirtualFile> files, ChangesBrowserNode baseNode) {
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    Collections.sort(files, VirtualFileHierarchicalComparator.getInstance());
    
    for (VirtualFile file : files) {
      insertChangeNode(file, policy, baseNode, defaultNodeCreator(file));
    }
  }

  private void buildLocallyDeletedPaths(final Collection<LocallyDeletedChange> locallyDeletedChanges, final ChangesBrowserNode baseNode) {
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (LocallyDeletedChange change : locallyDeletedChanges) {
      // whether a folder does not matter
      final StaticFilePath key = new StaticFilePath(false, change.getPresentableUrl(), change.getPath().getVirtualFile());
      ChangesBrowserNode oldNode = myFoldersCache.get(key.getKey());
      if (oldNode == null) {
        final ChangesBrowserNode node = ChangesBrowserNode.create(myProject, change);
        final ChangesBrowserNode parent = getParentNodeFor(key, policy, baseNode);
        model.insertNodeInto(node, parent, parent.getChildCount());
        myFoldersCache.put(key.getKey(), node);
      }
    }
  }

  private void buildFilePaths(final Collection<FilePath> filePaths, final ChangesBrowserNode baseNode) {
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (FilePath file : filePaths) {
      assert file != null;
      // whether a folder does not matter
      final StaticFilePath pathKey = new StaticFilePath(false, file.getIOFile().getAbsolutePath(), file.getVirtualFile());
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

  private void buildSwitchedRoots(final Map<VirtualFile, String> switchedRoots) {
    final ChangesBrowserNode rootsHeadNode = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_ROOTS_TAG);
    rootsHeadNode.setAttributes(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    model.insertNodeInto(rootsHeadNode, root, root.getChildCount());

    final List<VirtualFile> files = new ArrayList<VirtualFile>(switchedRoots.keySet());
    Collections.sort(files, VirtualFileHierarchicalComparator.getInstance());
    
    for (VirtualFile vf : files) {
      final ChangesGroupingPolicy policy = createGroupingPolicy();
      final ContentRevision cr = new CurrentContentRevision(new FilePathImpl(vf));
      final Change change = new Change(cr, cr, FileStatus.NOT_CHANGED);
      final String branchName = switchedRoots.get(vf);
      insertChangeNode(vf, policy, rootsHeadNode, new Computable<ChangesBrowserNode>() {
        public ChangesBrowserNode compute() {
          return new ChangesBrowserChangeNode(myProject, change, new ChangeNodeDecorator() {
            public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
            }
            public List<Pair<String, Stress>> stressPartsOfFileName(Change change, String parentPath) {
              return null;
            }
            public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
              renderer.append("[" + branchName + "] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
            }
          });
        }
      });
    }
  }

  private void buildSwitchedFiles(final MultiMap<String, VirtualFile> switchedFiles) {
    ChangesBrowserNode baseNode = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_FILES_TAG);
    model.insertNodeInto(baseNode, root, root.getChildCount());
    for(String branchName: switchedFiles.keySet()) {
      final List<VirtualFile> switchedFileList = new ArrayList<VirtualFile>(switchedFiles.get(branchName));
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

  private void buildLogicallyLockedFiles(final Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    final ChangesBrowserNode baseNode = createNode(ChangesBrowserNode.LOGICALLY_LOCKED_TAG);

    final ChangesGroupingPolicy policy = createGroupingPolicy();
    final List<VirtualFile> keys = new ArrayList<VirtualFile>(logicallyLockedFiles.keySet());
    Collections.sort(keys, VirtualFileHierarchicalComparator.getInstance());

    for (final VirtualFile file : keys) {
      final LogicalLock lock = logicallyLockedFiles.get(file);
      final ChangesBrowserLogicallyLockedFile obj = new ChangesBrowserLogicallyLockedFile(myProject, file, lock);
      insertChangeNode(obj, policy, baseNode, defaultNodeCreator(obj));
    }
  }

  private Computable<ChangesBrowserNode> defaultNodeCreator(final Object change) {
    return new Computable<ChangesBrowserNode>() {
      public ChangesBrowserNode compute() {
        return ChangesBrowserNode.create(myProject, change);
      }
    };
  }

  private void insertChangeNode(final Object change, final ChangesGroupingPolicy policy,
                                final ChangesBrowserNode listNode, final Computable<ChangesBrowserNode> nodeCreator) {
    final StaticFilePath pathKey = getKey(change);
    final ChangesBrowserNode node = nodeCreator.compute();
    ChangesBrowserNode parentNode = getParentNodeFor(pathKey, policy, listNode);
    model.insertNodeInto(node, parentNode, model.getChildCount(parentNode));

    if (pathKey != null && pathKey.isDirectory()) {
      myFoldersCache.put(pathKey.getKey(), node);
    }
  }

  private void sortNodes() {
    TreeUtil.sort(model, MyChangesBrowserNodeComparator.getInstance());

    model.nodeStructureChanged((TreeNode)model.getRoot());
  }

  private static class MyChangesBrowserNodeComparator implements Comparator<ChangesBrowserNode> {
    private static final MyChangesBrowserNodeComparator ourInstance = new MyChangesBrowserNodeComparator();

    public static MyChangesBrowserNodeComparator getInstance() {
      return ourInstance;
    }

    public int compare(ChangesBrowserNode node1, ChangesBrowserNode node2) {
      final int classdiff = node1.getSortWeight() - node2.getSortWeight();
      if (classdiff != 0) return classdiff;
      return node1.compareUserObjects(node2.getUserObject());
    }
  }

  private static void collapseDirectories(DefaultTreeModel model, ChangesBrowserNode node) {
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

  private static StaticFilePath getKey(final Object o) {
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

    return null;
  }

  private static StaticFilePath staticFrom(final FilePath fp) {
    return new StaticFilePath(fp.isDirectory(), fp.getIOFile().getAbsolutePath(), fp.getVirtualFile());
  }
  
  private static StaticFilePath staticFrom(final VirtualFile vf) {
    return new StaticFilePath(vf.isDirectory(), vf.getPath(), vf);
  }

  public static FilePath getPathForObject(Object o) {
    if (o instanceof Change) {
      return ChangesUtil.getFilePath((Change)o);
    }
    else if (o instanceof VirtualFile) {
      return new FilePathImpl((VirtualFile) o);
    }
    else if (o instanceof FilePath) {
      return (FilePath)o;
    } else if (o instanceof ChangesBrowserLogicallyLockedFile) {
      return new FilePathImpl(((ChangesBrowserLogicallyLockedFile) o).getUserObject());
    } else if (o instanceof LocallyDeletedChange) {
      return ((LocallyDeletedChange) o).getPath();
    }

    return null;
  }

  private ChangesBrowserNode getParentNodeFor(final StaticFilePath nodePath, @Nullable ChangesGroupingPolicy policy, ChangesBrowserNode rootNode) {
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
      parentNode = ChangesBrowserNode.create(myProject, new FilePathImpl(new File(parentPath.getPath()), true));
      ChangesBrowserNode grandPa = getParentNodeFor(parentPath, policy, rootNode);
      model.insertNodeInto(parentNode, grandPa, grandPa.getChildCount());
      myFoldersCache.put(parentPath.getKey(), parentNode);
    }

    return parentNode;
  }

  public DefaultTreeModel clearAndGetModel() {
    root.removeAllChildren();
    model = new DefaultTreeModel(root);
    myFoldersCache = new HashMap<String, ChangesBrowserNode>();
    myPolicyInitialized = false;
    return model;
  }

  public boolean isEmpty() {
    return model.getChildCount(root) == 0;
  }
}
