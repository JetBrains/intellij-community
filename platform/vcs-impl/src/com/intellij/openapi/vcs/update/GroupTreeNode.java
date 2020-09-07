// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class GroupTreeNode extends AbstractTreeNode implements Disposable {
  private final @Nls String myName;
  private final boolean mySupportsDeletion;
  private final List<String> myFilePaths = new ArrayList<>();
  private final Map<@NonNls String, @Nls String> myErrorsMap;
  private final SimpleTextAttributes myInvalidAttributes;
  private final Project myProject;
  private final String myFileGroupId;

  public GroupTreeNode(@Nls @NotNull String name,
                       boolean supportsDeletion,
                       @NotNull SimpleTextAttributes invalidAttributes,
                       @NotNull Project project,
                       @NotNull Map<@NonNls String, @Nls String> errorsMap,
                       @NonNls String id) {
    myName = name;
    mySupportsDeletion = supportsDeletion;
    myInvalidAttributes = invalidAttributes;
    myProject = project;
    myErrorsMap = errorsMap;
    myFileGroupId = id;
  }

  public String getFileGroupId() {
    return myFileGroupId;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.Folder;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getVirtualFiles() {
    ArrayList<VirtualFile> result = new ArrayList<>();
    for (int i = 0; i < getChildCount(); i++) {
      result.addAll(((AbstractTreeNode)getChildAt(i)).getVirtualFiles());
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<File> getFiles() {
    ArrayList<File> result = new ArrayList<>();
    for (int i = 0; i < getChildCount(); i++) {
      result.addAll(((AbstractTreeNode)getChildAt(i)).getFiles());
    }
    return result;
  }

  @Override
  protected int getItemsCount() {
    int result = 0;
    Enumeration children = children();
    while (children.hasMoreElements()) {
      AbstractTreeNode treeNode = (AbstractTreeNode)children.nextElement();
      result += treeNode.getItemsCount();
    }
    return result;
  }

  @Override
  protected boolean showStatistics() {
    return true;
  }

  @NotNull
  @Override
  public SimpleTextAttributes getAttributes() {
    return myFilterAttributes == null ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : myFilterAttributes;
  }

  @Override
  public boolean getSupportsDeletion() {
    return mySupportsDeletion;
  }

  public void addFilePath(@NotNull String filePath) {
    myFilePaths.add(filePath);
  }

  public void rebuild(boolean groupByPackages, @Nullable Pair<PackageSetBase, NamedScopesHolder> filter, boolean showOnlyFilteredItems) {
    myFilterAttributes = null;
    if (containsGroups()) {
      rebuildGroups(groupByPackages, filter, showOnlyFilteredItems);
    }
    else {
      rebuildFiles(groupByPackages, filter, showOnlyFilteredItems);
    }
  }

  private void rebuildGroups(boolean groupByPackages,
                             @Nullable Pair<PackageSetBase, NamedScopesHolder> filter,
                             boolean showOnlyFilteredItems) {
    boolean apply = false;
    for (int i = 0; i < getChildCount(); i++) {
      GroupTreeNode childGroup = (GroupTreeNode)getChildAt(i);
      childGroup.rebuild(groupByPackages, filter, showOnlyFilteredItems);
      apply |= childGroup.myFilterAttributes != null;
    }
    applyFilter(apply);
  }

  private void rebuildFiles(boolean groupByPackages,
                            @Nullable Pair<PackageSetBase, NamedScopesHolder> filter,
                            boolean showOnlyFilteredItems) {
    for (int i = getChildCount() - 1; i >= 0; i--) {
      final TreeNode node = getChildAt(i);
      if (node instanceof Disposable) {
        Disposer.dispose((Disposable)node);
      }
    }
    removeAllChildren();

    if (groupByPackages) {
      buildPackages();
      acceptFilter(filter, showOnlyFilteredItems);
    }
    else {
      buildFiles(filter, showOnlyFilteredItems);
    }

    setTreeModel(myTreeModel);

    if (myTreeModel != null) {
      myTreeModel.nodeStructureChanged(this);
    }
  }

  private void buildPackages() {
    Collection<File> files = new LinkedHashSet<>();
    for (final String myFilePath : myFilePaths) {
      files.add(new File(myFilePath));
    }
    GroupByPackages groupByPackages = new GroupByPackages(files);

    List<File> roots = groupByPackages.getRoots();
    addFiles(this, roots, files, groupByPackages, null);

  }

  private void addFiles(@NotNull AbstractTreeNode parentNode,
                        @NotNull List<? extends File> roots,
                        @NotNull final Collection<? extends File> files,
                        @NotNull GroupByPackages groupByPackages,
                        String parentPath) {
    roots.sort((file1, file2) -> {
      boolean containsFile1 = files.contains(file1);
      boolean containsFile2 = files.contains(file2);
      if (containsFile1 == containsFile2) {
        return file1.getAbsolutePath().compareToIgnoreCase(file2.getAbsolutePath());
      }
      return containsFile1 ? 1 : -1;
    });

    for (final File root : roots) {
      FileOrDirectoryTreeNode child = files.contains(root)
                                      ? new FileTreeNode(root.getAbsolutePath(), myInvalidAttributes, myProject, parentPath)
                                      : new DirectoryTreeNode(root.getAbsolutePath(), myProject, parentPath);
      Disposer.register((Disposable)parentNode, child);
      parentNode.add(child);
      addFiles(child, groupByPackages.getChildren(root), files, groupByPackages, child.getFilePath());
    }
  }

  private void buildFiles(@Nullable Pair<PackageSetBase, NamedScopesHolder> filter, boolean showOnlyFilteredItems) {
    myFilePaths.sort((path1, path2) -> path1.compareToIgnoreCase(path2));

    boolean apply = false;

    for (final String filePath : myFilePaths) {
      final FileTreeNode child = new FileTreeNode(filePath, myInvalidAttributes, myProject, null);
      if (filter != null) {
        if (child.acceptFilter(filter, showOnlyFilteredItems)) {
          apply = true;
        }
        else if (showOnlyFilteredItems) {
          Disposer.dispose(child);
          continue;
        }
      }
      final String error = myErrorsMap.get(filePath);
      if (error != null) {
        child.setErrorText(error);
      }
      add(child);
      Disposer.register(this, child);
    }

    applyFilter(apply);
  }

  private boolean containsGroups() {
    return myFilePaths.isEmpty();
  }

  @Override
  public void dispose() {
  }
}
