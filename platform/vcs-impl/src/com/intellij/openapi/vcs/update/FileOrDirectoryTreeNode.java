/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Map;

/**
 * author: lesya
 */
public abstract class FileOrDirectoryTreeNode extends AbstractTreeNode implements VirtualFilePointerListener, Disposable {
  private static final Map<FileStatus, SimpleTextAttributes> myFileStatusToAttributeMap = new HashMap<>();
  private final SimpleTextAttributes myInvalidAttributes;
  @NotNull
  private final Project myProject;
  protected final File myFile;
  private final String myName;

  protected FileOrDirectoryTreeNode(@NotNull String path,
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

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  protected String getFilePath() {
    return getFilePointer().getPresentableUrl();
  }

  @Override
  public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
  }

  @Override
  public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
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
      if (oldObject instanceof VirtualFilePointer) {
        VirtualFilePointer pointer = (VirtualFilePointer)oldObject;
        Disposer.dispose((Disposable)pointer);
      }
    }
  }

  public VirtualFilePointer getFilePointer() {
    return (VirtualFilePointer)getUserObject();
  }

  @NotNull
  @Override
  public SimpleTextAttributes getAttributes() {
    if (!getFilePointer().isValid()) {
      return myInvalidAttributes;
    }
    VirtualFile file = getFilePointer().getFile();
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    FileStatus status = fileStatusManager.getStatus(file);
    SimpleTextAttributes attributes = getAttributesFor(status);
    return myFilterAttributes == null ? attributes : SimpleTextAttributes.merge(myFilterAttributes, attributes);
  }

  @NotNull
  private static SimpleTextAttributes getAttributesFor(@NotNull FileStatus status) {
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

  @NotNull
  public Project getProject() {
    return myProject;
  }
}
