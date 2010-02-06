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
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * @author max
 */
public class TreeModelBuilder {
  @NonNls public static final String ROOT_NODE_VALUE = "root";

  private final Project myProject;
  private final boolean showFlatten;
  private final DefaultTreeModel model;
  private final ChangesBrowserNode root;

  public TreeModelBuilder(final Project project, final boolean showFlatten) {
    myProject = project;
    this.showFlatten = showFlatten;
    root = ChangesBrowserNode.create(myProject, ROOT_NODE_VALUE);
    model = new DefaultTreeModel(root);
  }

  public DefaultTreeModel buildModel(final List<Change> changes, final ChangeNodeDecorator changeNodeDecorator) {
    final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (final Change change : changes) {
      insertChangeNode(change, foldersCache, policy, root, new Computable<ChangesBrowserNode>() {
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
    final ChangesGroupingPolicyFactory factory = ChangesGroupingPolicyFactory.getInstance(myProject);
    if (factory != null) {
      return factory.createGroupingPolicy(model);
    }
    return null;
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
                                     final List<VirtualFile> unversionedFiles,
                                     final List<LocallyDeletedChange> locallyDeletedFiles,
                                     final List<VirtualFile> modifiedWithoutEditing,
                                     final MultiMap<String, VirtualFile> switchedFiles,
                                     @Nullable Map<VirtualFile, String> switchedRoots,
                                     @Nullable final List<VirtualFile> ignoredFiles, @Nullable final List<VirtualFile> lockedFolders,
                                     @Nullable final Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    buildModel(changeLists);

    if (!modifiedWithoutEditing.isEmpty()) {
      buildVirtualFiles(modifiedWithoutEditing, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
    }
    if (!unversionedFiles.isEmpty()) {
      buildVirtualFiles(unversionedFiles, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    }
    if (switchedRoots != null && (! switchedRoots.isEmpty())) {
      buildSwitchedRoots(switchedRoots);
    }
    if (!switchedFiles.isEmpty()) {
      buildSwitchedFiles(switchedFiles);
    }
    if (ignoredFiles != null && !ignoredFiles.isEmpty()) {
      buildVirtualFiles(ignoredFiles, ChangesBrowserNode.IGNORED_FILES_TAG);
    }
    if (lockedFolders != null && !lockedFolders.isEmpty()) {
      buildVirtualFiles(lockedFolders, ChangesBrowserNode.LOCKED_FOLDERS_TAG);
    }
    if (logicallyLockedFiles != null && (! logicallyLockedFiles.isEmpty())) {
      buildLogicallyLockedFiles(logicallyLockedFiles);
    }

    if (!locallyDeletedFiles.isEmpty()) {
      ChangesBrowserNode locallyDeletedNode = ChangesBrowserNode.create(myProject, VcsBundle.message("changes.nodetitle.locally.deleted.files"));
      model.insertNodeInto(locallyDeletedNode, root, root.getChildCount());
      buildLocallyDeletedPaths(locallyDeletedFiles, locallyDeletedNode);
    }

    collapseDirectories(model, root);
    sortNodes();

    return model;
  }

  public DefaultTreeModel buildModel(List<? extends ChangeList> changeLists) {
    final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
    for (ChangeList list : changeLists) {
      final Collection<Change> changes = list.getChanges();
      final ChangeListRemoteState listRemoteState = new ChangeListRemoteState(changes.size());
      ChangesBrowserNode listNode = new ChangesBrowserChangeListNode(myProject, list, listRemoteState);
      model.insertNodeInto(listNode, root, 0);
      final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
      final ChangesGroupingPolicy policy = createGroupingPolicy();
      int i = 0;
      for (final Change change : changes) {
        final MyChangeNodeUnderChangeListDecorator decorator =
          new MyChangeNodeUnderChangeListDecorator(revisionsCache, new ChangeListRemoteState.Reporter(i, listRemoteState));
        insertChangeNode(change, foldersCache, policy, listNode, new Computable<ChangesBrowserNode>() {
          public ChangesBrowserNode compute() {
            return new ChangesBrowserChangeNode(myProject, change, decorator);
          }
        });
        ++ i;
      }
    }
    return model;
  }

  private void buildVirtualFiles(final Iterator<FilePath> iterator, @Nullable final Object tag) {
    final ChangesBrowserNode baseNode = createNode(tag);
    final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (; ; iterator.hasNext()) {
      final FilePath path = iterator.next();
      insertChangeNode(path.getVirtualFile(), foldersCache, policy, baseNode, defaultNodeCreator(path.getVirtualFile()));
    }
  }

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

  private void insertFilesIntoNode(List<VirtualFile> files, ChangesBrowserNode baseNode) {
    final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (VirtualFile file : files) {
      insertChangeNode(file, foldersCache, policy, baseNode, defaultNodeCreator(file));
    }
  }

  private void buildLocallyDeletedPaths(final Collection<LocallyDeletedChange> locallyDeletedChanges, final ChangesBrowserNode baseNode) {
    final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (LocallyDeletedChange change : locallyDeletedChanges) {
      ChangesBrowserNode oldNode = foldersCache.get(change.getPresentableUrl());
      if (oldNode == null) {
        final ChangesBrowserNode node = ChangesBrowserNode.create(myProject, change);
        final ChangesBrowserNode parent = getParentNodeFor(node, foldersCache, policy, baseNode);
        model.insertNodeInto(node, parent, parent.getChildCount());
        foldersCache.put(change.getPresentableUrl(), node);
      }
    }
  }

  private void buildFilePaths(final Collection<FilePath> filePaths, final ChangesBrowserNode baseNode) {
    final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (FilePath file : filePaths) {
      assert file != null;
      ChangesBrowserNode oldNode = foldersCache.get(file);
      if (oldNode == null) {
        final ChangesBrowserNode node = ChangesBrowserNode.create(myProject, file);
        model.insertNodeInto(node, getParentNodeFor(node, foldersCache, policy, baseNode), 0);
        foldersCache.put(file.getIOFile().getAbsolutePath(), node);
      }
    }
  }

  private void buildSwitchedRoots(final Map<VirtualFile, String> switchedRoots) {
    final ChangesBrowserNode rootsHeadNode = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_ROOTS_TAG);
    rootsHeadNode.setAttributes(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    model.insertNodeInto(rootsHeadNode, root, root.getChildCount());

    for (VirtualFile vf : switchedRoots.keySet()) {
      final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
      final ChangesGroupingPolicy policy = createGroupingPolicy();
      final ContentRevision cr = new CurrentContentRevision(new FilePathImpl(vf));
      final Change change = new Change(cr, cr, FileStatus.NOT_CHANGED);
      final String branchName = switchedRoots.get(vf);
      insertChangeNode(vf, foldersCache, policy, rootsHeadNode, new Computable<ChangesBrowserNode>() {
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
      final Collection<VirtualFile> switchedFileList = switchedFiles.get(branchName);
      if (switchedFileList.size() > 0) {
        ChangesBrowserNode branchNode = ChangesBrowserNode.create(myProject, branchName);
        model.insertNodeInto(branchNode, baseNode, baseNode.getChildCount());

        final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
        final ChangesGroupingPolicy policy = createGroupingPolicy();
        for (VirtualFile file : switchedFileList) {
          insertChangeNode(file, foldersCache, policy, branchNode, defaultNodeCreator(file));
        }
      }
    }
  }

  private void buildLogicallyLockedFiles(final Map<VirtualFile, LogicalLock> logicallyLockedFiles) {
    final ChangesBrowserNode baseNode = createNode(ChangesBrowserNode.LOGICALLY_LOCKED_TAG);

    final HashMap<String, ChangesBrowserNode> foldersCache = new HashMap<String, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (Map.Entry<VirtualFile, LogicalLock> entry : logicallyLockedFiles.entrySet()) {
      final VirtualFile file = entry.getKey();
      final LogicalLock lock = entry.getValue();
      final ChangesBrowserLogicallyLockedFile obj = new ChangesBrowserLogicallyLockedFile(myProject, file, lock);
      insertChangeNode(obj, foldersCache, policy, baseNode,
                       defaultNodeCreator(obj));
    }
  }
  
  private Computable<ChangesBrowserNode> defaultNodeCreator(final Object change) {
    return new Computable<ChangesBrowserNode>() {
      public ChangesBrowserNode compute() {
        return ChangesBrowserNode.create(myProject, change);
      }
    };
  }

  private void insertChangeNode(final Object change, final HashMap<String, ChangesBrowserNode> foldersCache,
                                final ChangesGroupingPolicy policy,
                                final ChangesBrowserNode listNode, final Computable<ChangesBrowserNode> nodeCreator) {
    final FilePath nodePath = getPathForObject(change);
    ChangesBrowserNode oldNode = (nodePath == null) ? null : foldersCache.get(nodePath.getIOFile().getAbsolutePath());
    ChangesBrowserNode node = nodeCreator.compute();
    if (oldNode != null) {
      for(int i=oldNode.getChildCount()-1; i >= 0; i--) {
        MutableTreeNode child = (MutableTreeNode) model.getChild(oldNode, i);
        model.removeNodeFromParent(child);
        model.insertNodeInto(child, node, model.getChildCount(node));
      }
      final MutableTreeNode parent = (MutableTreeNode)oldNode.getParent();
      int index = model.getIndexOfChild(parent, oldNode);
      model.removeNodeFromParent(oldNode);
      model.insertNodeInto(node, parent, index);
      foldersCache.put(nodePath.getIOFile().getAbsolutePath(), node);
    }
    else {
      ChangesBrowserNode parentNode = getParentNodeFor(node, foldersCache, policy, listNode);
      model.insertNodeInto(node, parentNode, model.getChildCount(parentNode));
      // ?
      if (nodePath != null) {
        foldersCache.put(nodePath.getIOFile().getAbsolutePath(), node);
      }
    }
  }

  private void sortNodes() {
    TreeUtil.sort(model, new Comparator() {
      public int compare(final Object n1, final Object n2) {
        final ChangesBrowserNode node1 = (ChangesBrowserNode)n1;
        final ChangesBrowserNode node2 = (ChangesBrowserNode)n2;

        final int classdiff = node1.getSortWeight() - node2.getSortWeight();
        if (classdiff != 0) return classdiff;

        return node1.compareUserObjects(node2.getUserObject());
      }
   });

    model.nodeStructureChanged((TreeNode)model.getRoot());
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

  private ChangesBrowserNode getParentNodeFor(ChangesBrowserNode node,
                                Map<String, ChangesBrowserNode> folderNodesCache,
                                @Nullable ChangesGroupingPolicy policy,
                                ChangesBrowserNode rootNode) {
    if (showFlatten) {
      return rootNode;
    }

    final FilePath path = getPathForObject(node.getUserObject());

    if (policy != null) {
      ChangesBrowserNode nodeFromPolicy = policy.getParentNodeFor(node, rootNode);
      if (nodeFromPolicy != null) {
        return nodeFromPolicy;
      }
    }

    FilePath parentPath = path.getParentPath();
    if (parentPath == null) {
      return rootNode;
    }

    ChangesBrowserNode parentNode = folderNodesCache.get(parentPath.getIOFile().getAbsolutePath());
    if (parentNode == null) {
      parentNode = ChangesBrowserNode.create(myProject, parentPath);
      ChangesBrowserNode grandPa = getParentNodeFor(parentNode, folderNodesCache, policy, rootNode);
      model.insertNodeInto(parentNode, grandPa, grandPa.getChildCount());
      folderNodesCache.put(parentPath.getIOFile().getAbsolutePath(), parentNode);
    }

    return parentNode;
  }
}
