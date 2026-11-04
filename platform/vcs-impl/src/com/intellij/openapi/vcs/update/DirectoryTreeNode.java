// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * author: lesya
 */
@ApiStatus.Internal
public class DirectoryTreeNode extends FileOrDirectoryTreeNode{

  public DirectoryTreeNode(@NotNull String path, @NotNull Project project, @Nullable String parentPath) {
    super(path, true, SimpleTextAttributes.ERROR_ATTRIBUTES, false, project, parentPath);
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

}
