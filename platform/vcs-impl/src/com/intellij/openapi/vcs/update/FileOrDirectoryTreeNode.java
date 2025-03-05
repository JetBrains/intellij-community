// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * author: lesya
 */
@ApiStatus.Internal
public abstract class FileOrDirectoryTreeNode extends AbstractTreeNode implements VirtualFilePointerListener, Disposable {
  private static final Map<FileStatus, SimpleTextAttributes> myFileStatusToAttributeMap = new HashMap<>();
  private final SimpleTextAttributes myInvalidAttributes;
  private final @NotNull Project myProject;
  protected final File myFile;
  private final @NlsSafe String myName;

  FileOrDirectoryTreeNode(@NotNull String path,
                          @NotNull SimpleTextAttributes invalidAttributes,
                          @NotNull Project project,
                          @Nullable String parentPath) {
    String preparedPath = path.replace(File.separatorChar, '/');
    String url = VirtualFileManager.constructUrl(LocalFileSystem.getInstance().getProtocol(), preparedPath);
    setUserObject(VirtualFilePointerManager.getInstance().create(url, this, this));
    myFile = new File(getFilePath());
    myInvalidAttributes = invalidAttributes;
    myProject = project;
    myName = parentPath == null ? myFile.getAbsolutePath() : myFile.getName();
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  protected String getFilePath() {
    return getFilePointer().getPresentableUrl();
  }

  @Override
  public void validityChanged(VirtualFilePointer @NotNull [] pointers) {
    if (!getFilePointer().isValid()) {
      AbstractTreeNode parent = (AbstractTreeNode)getParent();
      if (parent != null && parent.getSupportsDeletion()) {
        getTreeModel().removeNodeFromParent(this);
      }
      else {
        if (getTree() != null) {
          getTree().repaint();
        }
      }
    }
  }

  @Override
  public void setUserObject(final Object userObject) {
    final Object oldObject = getUserObject();
    try {
      super.setUserObject(userObject);
    }
    finally {
      if (oldObject instanceof VirtualFilePointer pointer) {
        Disposer.dispose((Disposable)pointer);
      }
    }
  }

  public VirtualFilePointer getFilePointer() {
    return (VirtualFilePointer)getUserObject();
  }

  @Override
  public @NotNull SimpleTextAttributes getAttributes() {
    if (!getFilePointer().isValid()) {
      return myInvalidAttributes;
    }
    VirtualFile file = getFilePointer().getFile();
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    FileStatus status = fileStatusManager.getStatus(file);
    SimpleTextAttributes attributes = getAttributesFor(status);
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

  @Override
  public void dispose() {
  }

  public @NotNull Project getProject() {
    return myProject;
  }
}
