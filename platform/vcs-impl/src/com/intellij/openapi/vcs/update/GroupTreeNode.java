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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class GroupTreeNode extends AbstractTreeNode implements Disposable {
  private final String myName;
  private final boolean mySupportsDeletion;
  private final List<String> myFilePaths = new ArrayList<String>();
  private final Map<String, String> myErrorsMap;
  private final SimpleTextAttributes myInvalidAttributes;
  private final Project myProject;

  public GroupTreeNode(@NotNull String name,
                       boolean supportsDeletion,
                       @NotNull SimpleTextAttributes invalidAttributes,
                       @NotNull Project project,
                       @NotNull Map<String, String> errorsMap) {
    myName = name;
    mySupportsDeletion = supportsDeletion;
    myInvalidAttributes = invalidAttributes;
    myProject = project;
    myErrorsMap = errorsMap;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    @NonNls String iconName = expanded ? "folderOpen" : "folder";
    return IconLoader.getIcon("/nodes/" + iconName + ".png");
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getVirtualFiles() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (int i = 0; i < getChildCount(); i++) {
      result.addAll(((AbstractTreeNode)getChildAt(i)).getVirtualFiles());
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<File> getFiles() {
    ArrayList<File> result = new ArrayList<File>();
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
    return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
  }

  @Override
  public boolean getSupportsDeletion() {
    return mySupportsDeletion;
  }

  public void addFilePath(@NotNull String filePath) {
    myFilePaths.add(filePath);
  }

  public void rebuild(boolean groupByPackages) {
    if (containsGroups()) {
      rebuildGroups(groupByPackages);
    }
    else {
      rebuildFiles(groupByPackages);
    }
  }

  private void rebuildGroups(boolean groupByPackages) {
    for (int i = 0; i < getChildCount(); i++) {
      ((GroupTreeNode)getChildAt(i)).rebuild(groupByPackages);
    }
  }

  private void rebuildFiles(boolean groupByPackages) {
    for (int i = getChildCount() - 1; i >= 0; i--) {
      final TreeNode node = getChildAt(i);
      if (node instanceof Disposable) {
        Disposer.dispose((Disposable)node);
      }
    }
    removeAllChildren();

    if (groupByPackages) {
      buildPackages();
    }
    else {
      buildFiles();
    }

    setTreeModel(myTreeModel);

    if (myTreeModel != null) {
      myTreeModel.nodeStructureChanged(this);
    }
  }

  private void buildPackages() {
    Collection<File> files = new LinkedHashSet<File>();
    for (final String myFilePath : myFilePaths) {
      files.add(new File(myFilePath));
    }
    GroupByPackages groupByPackages = new GroupByPackages(files);

    List<File> roots = groupByPackages.getRoots();
    addFiles(this, roots, files, groupByPackages, null);
  }

  private void addFiles(@NotNull AbstractTreeNode parentNode,
                        @NotNull List<File> roots,
                        @NotNull final Collection<File> files,
                        @NotNull GroupByPackages groupByPackages,
                        String parentPath) {
    Collections.sort(roots, new Comparator<File>() {
      @Override
      public int compare(File file1, File file2) {
        boolean containsFile1 = files.contains(file1);
        boolean containsFile2 = files.contains(file2);
        if (containsFile1 == containsFile2) {
          return file1.getAbsolutePath().compareToIgnoreCase(file2.getAbsolutePath());
        }
        return containsFile1 ? 1 : -1;
      }
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

  private void buildFiles() {
    Collections.sort(myFilePaths, new Comparator<String>() {
      @Override
      public int compare(String path1, String path2) {
        return path1.compareToIgnoreCase(path2);
      }
    });

    for (final String filePath : myFilePaths) {
      final FileTreeNode child = new FileTreeNode(filePath, myInvalidAttributes, myProject, null);
      final String error = myErrorsMap.get(filePath);
      if (error != null) {
        child.setErrorText(error);
      }
      add(child);
      Disposer.register(this, child);
    }
  }

  private boolean containsGroups() {
    return myFilePaths.isEmpty();
  }

  @Override
  public void dispose() {
  }
}
