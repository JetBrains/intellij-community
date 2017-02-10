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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
public class DirectoryTreeNode extends FileOrDirectoryTreeNode{

  public DirectoryTreeNode(String path, Project project, String parentPath) {
    super(path, SimpleTextAttributes.ERROR_ATTRIBUTES, project, parentPath);
  }

  protected int getItemsCount() {
    int result = 0;
    for (int i = 0;  i < getChildCount(); i++){
       result += ((FileOrDirectoryTreeNode)getChildAt(i)).getItemsCount();
    }
    return result;
  }

  protected boolean showStatistics() {
    return true;
  }

  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.Folder;
  }

  @NotNull
  public Collection<VirtualFile> getVirtualFiles() {
    Collection<VirtualFile> result = new ArrayList<>();
    for (int i = 0;  i < getChildCount(); i++){
      FileOrDirectoryTreeNode child = (FileOrDirectoryTreeNode)getChildAt(i);
      result.addAll(child.getVirtualFiles());
    }
    return result;
  }

  @NotNull
  public Collection<File> getFiles() {
    Collection<File> result = new ArrayList<>();
    for (int i = 0;  i < getChildCount(); i++){
      FileOrDirectoryTreeNode child = (FileOrDirectoryTreeNode)getChildAt(i);
      result.addAll(child.getFiles());
    }
    return result;
  }

}
