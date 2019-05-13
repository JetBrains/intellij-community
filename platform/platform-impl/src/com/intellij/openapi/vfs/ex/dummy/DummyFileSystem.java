/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class DummyFileSystem extends DeprecatedVirtualFileSystem implements NonPhysicalFileSystem {
  @NonNls public static final String PROTOCOL = "dummy";
  private VirtualFileDirectoryImpl myRoot;

  public static DummyFileSystem getInstance() {
    return (DummyFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public DummyFileSystem() {
    startEventPropagation();
  }

  public VirtualFile createRoot(String name) {
    myRoot = new VirtualFileDirectoryImpl(this, null, name);
    fireFileCreated(null, myRoot);
    return myRoot;
  }

  @Nullable
  public VirtualFile findById(int id) {
    return findById(id, myRoot);
  }

  @Nullable
  private static VirtualFile findById(final int id, final VirtualFileImpl r) {
    if (r == null) return null;
    if (r.getId() == id) return r;
    @SuppressWarnings("UnsafeVfsRecursion") final VirtualFile[] children = r.getChildren();
    if (children != null) {
      for (VirtualFile f : children) {
        final VirtualFile child = findById(id, (VirtualFileImpl)f);
        if (child != null) return child;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
//    LOG.error("method not implemented");
    return null;
  }

  @NotNull
  @Override
  public String extractPresentableUrl(@NotNull String path) {
    return path;
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
    fireBeforeFileDeletion(requestor, vFile);
    final VirtualFileDirectoryImpl parent = (VirtualFileDirectoryImpl)vFile.getParent();
    if (parent == null) {
      throw new IOException(VfsBundle.message("file.delete.root.error", vFile.getPresentableUrl()));
    }

    parent.removeChild((VirtualFileImpl)vFile);
    fireFileDeleted(requestor, vFile, vFile.getName(), parent);
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    final String oldName = vFile.getName();
    fireBeforePropertyChange(requestor, vFile, VirtualFile.PROP_NAME, oldName, newName);
    ((VirtualFileImpl)vFile).setName(newName);
    firePropertyChanged(requestor, vFile, VirtualFile.PROP_NAME, oldName, newName);
  }

  @NotNull
  @Override
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    final VirtualFileDirectoryImpl dir = (VirtualFileDirectoryImpl)vDir;
    VirtualFileImpl child = new VirtualFileDataImpl(this, dir, fileName);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }

  @Override
  public void fireBeforeContentsChange(final Object requestor, @NotNull final VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }

  @Override
  public void fireContentsChanged(final Object requestor, @NotNull final VirtualFile file, final long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    final VirtualFileDirectoryImpl dir = (VirtualFileDirectoryImpl)vDir;
    VirtualFileImpl child = new VirtualFileDirectoryImpl(this, dir, dirName);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }
}
