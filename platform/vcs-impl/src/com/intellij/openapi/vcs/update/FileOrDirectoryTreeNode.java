// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * A row for a path reported by an update. The node holds only the path: it never creates VFS pointers and never resolves
 * the path through VFS when the tree is built or painted (IJPL-257565). VFS is consulted from its cache when painting,
 * and fully only for the selected rows, see {@link UpdateInfoTree#uiDataSnapshot}. Local changes after the update are not tracked.
 */
@ApiStatus.Internal
public abstract class FileOrDirectoryTreeNode extends AbstractTreeNode {
  private static final Map<FileStatus, SimpleTextAttributes> myFileStatusToAttributeMap = new HashMap<>();
  private final SimpleTextAttributes myInvalidAttributes;
  private final boolean myCanBeAbsent;
  private final @NotNull Project myProject;
  protected final @NotNull FilePath myPath;
  private final @NlsSafe String myName;

  FileOrDirectoryTreeNode(@NotNull String path,
                          boolean isDirectory,
                          @NotNull SimpleTextAttributes invalidAttributes,
                          boolean canBeAbsent,
                          @NotNull Project project,
                          @Nullable String parentPath) {
    myPath = VcsUtil.getFilePath(path, isDirectory);
    myInvalidAttributes = invalidAttributes;
    myCanBeAbsent = canBeAbsent;
    myProject = project;
    myName = parentPath == null ? myPath.getPresentableUrl() : myPath.getName();
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  public @NotNull FilePath getFilePath() {
    return myPath;
  }

  @Override
  public @NotNull SimpleTextAttributes getAttributes() {
    SimpleTextAttributes attributes;
    VirtualFile cached = LocalFileSystem.getInstance().findFileByPathIfCached(myPath.getPath());
    if (cached != null) {
      attributes = getAttributesFor(FileStatusManager.getInstance(myProject).getStatus(cached));
    }
    else if (myCanBeAbsent) {
      return myInvalidAttributes;
    }
    else {
      // not in the VFS cache: may exist on disk under a never-loaded directory; never resolve it here
      attributes = getAttributesFor(FileStatus.NOT_CHANGED);
    }
    return myFilterAttributes == null ? attributes : SimpleTextAttributes.merge(myFilterAttributes, attributes);
  }

  private static @NotNull SimpleTextAttributes getAttributesFor(@NotNull FileStatus status) {
    Color color = status.getColor();
    if (color == null) color = UIUtil.getListForeground();

    if (!myFileStatusToAttributeMap.containsKey(status)) {
      myFileStatusToAttributeMap.put(status, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color));
    }
    return myFileStatusToAttributeMap.get(status);
  }

  @Override
  public boolean getSupportsDeletion() {
    AbstractTreeNode parent = (AbstractTreeNode)getParent();
    return parent != null && parent.getSupportsDeletion();
  }

  public @NotNull Project getProject() {
    return myProject;
  }
}
