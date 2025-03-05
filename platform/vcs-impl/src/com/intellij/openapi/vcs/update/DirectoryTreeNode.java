// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
@ApiStatus.Internal
public class DirectoryTreeNode extends FileOrDirectoryTreeNode{

  public DirectoryTreeNode(String path, Project project, String parentPath) {
    super(path, SimpleTextAttributes.ERROR_ATTRIBUTES, project, parentPath);
  }

  @Override
  protected int getItemsCount() {
    int result = 0;
    for (int i = 0;  i < getChildCount(); i++){
       result += ((FileOrDirectoryTreeNode)getChildAt(i)).getItemsCount();
    }
    return result;
  }

  @Override
  protected boolean showStatistics() {
    return true;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.Folder;
  }

  @Override
  public @NotNull Collection<VirtualFile> getVirtualFiles() {
    Collection<VirtualFile> result = new ArrayList<>();
    for (int i = 0;  i < getChildCount(); i++){
      FileOrDirectoryTreeNode child = (FileOrDirectoryTreeNode)getChildAt(i);
      result.addAll(child.getVirtualFiles());
    }
    return result;
  }

  @Override
  public @NotNull Collection<File> getFiles() {
    Collection<File> result = new ArrayList<>();
    for (int i = 0;  i < getChildCount(); i++){
      FileOrDirectoryTreeNode child = (FileOrDirectoryTreeNode)getChildAt(i);
      result.addAll(child.getFiles());
    }
    return result;
  }

}
