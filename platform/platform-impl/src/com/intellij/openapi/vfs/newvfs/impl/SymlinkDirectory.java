/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 * @since 31.10.2011
 */
@SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
public class SymlinkDirectory extends VirtualDirectoryImpl {
  private static final VirtualFileSystemEntry BAD_LINK = new VirtualFileImpl("*BAD_LINK*", null, -1) {
    @Override
    public String toString() {
      return getName();
    }
  };

  private final String myTargetPath;
  private VirtualFileSystemEntry myTargetDir;

  public SymlinkDirectory(@NotNull final String name, final VirtualDirectoryImpl parent, @NotNull final NewVirtualFileSystem fs, final int id) {
    super(name, parent, fs, id);
    myTargetPath = getFileSystem().resolveSymLink(this);
    if (myTargetPath == null) {
      myTargetDir = BAD_LINK;
    }
  }

  @Nullable
  public String getTargetPath() {
    return myTargetPath;
  }

  @Override
  public VirtualDirectoryImpl getRealFile() {
    if (myTargetDir == BAD_LINK) return null;
    if (myTargetDir != null) return (VirtualDirectoryImpl)myTargetDir;

    final VirtualFile file = findFile(myTargetPath, false);
    if (!(file instanceof VirtualDirectoryImpl) ||
        file == this ||
        FileUtil.isAncestor(new File(file.getPath()), new File(getPath()), true)) {
      myTargetDir = BAD_LINK;
      return null;
    }
    else {
      myTargetDir = (VirtualDirectoryImpl)file;
    }

    return (VirtualDirectoryImpl)myTargetDir;
  }

  @Nullable
  private VirtualFile findFile(final String path, final boolean refresh) {
    return RecursionManager.doPreventingRecursion(this, false, new NullableComputable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return refresh ? VfsImplUtil.refreshAndFindFileByPath(getFileSystem(), path) : VfsImplUtil.findFileByPath(getFileSystem(), path);
      }
    });
  }

  @Override
  public VirtualFileSystemEntry findChild(@NotNull final String name) {
    final VirtualDirectoryImpl target = getRealFile();
    return target == null ? null : target.findChild(name);
  }

  @Override
  public NewVirtualFile findChildIfCached(@NotNull final String name) {
    final VirtualDirectoryImpl target = getRealFile();
    return target == null ? null : target.findChildIfCached(name);
  }

  @Override
  public NewVirtualFile findChildById(final int id) {
    final VirtualDirectoryImpl target = getRealFile();
    return target == null ? null : target.findChildById(id);
  }

  @Override
  public NewVirtualFile findChildByIdIfCached(final int id) {
    final VirtualDirectoryImpl target = getRealFile();
    return target == null ? null : target.findChildByIdIfCached(id);
  }

  @Override
  public NewVirtualFile refreshAndFindChild(@NotNull final String name) {
    final VirtualDirectoryImpl target = getRealFile();
    return target == null ? null : target.refreshAndFindChild(name);
  }

  @NotNull
  @Override
  public VirtualFile[] getChildren() {
    final VirtualDirectoryImpl target = getRealFile();
    return target != null ? target.getChildren() : VirtualFile.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getCachedChildren() {
    final VirtualDirectoryImpl target = getRealFile();
    return target != null ? target.getCachedChildren() : Collections.<VirtualFile>emptyList();
  }

  @Override
  public void addChild(@NotNull final VirtualFileSystemEntry file) {
    final VirtualDirectoryImpl target = getRealFile();
    if (target != null) {
      target.addChild(file);
    }
  }

  @Override
  public void removeChild(@NotNull final VirtualFile file) {
    final VirtualDirectoryImpl target = getRealFile();
    if (target != null) {
      target.removeChild(file);
    }
  }

  @NotNull
  @Override
  public Iterable<VirtualFile> iterInDbChildren() {
    final VirtualDirectoryImpl target = getRealFile();
    return target != null ? target.iterInDbChildren() : Collections.<VirtualFile>emptyList();
  }
}
