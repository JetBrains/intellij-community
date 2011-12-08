/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 * @since 31.10.2011
 */
@SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
public class SymlinkDirectory extends VirtualDirectoryImpl {
  private final AtomicNotNullLazyValue<VirtualDirectoryImpl> myTarget = new AtomicNotNullLazyValue<VirtualDirectoryImpl>() {
    @NotNull
    @Override
    protected VirtualDirectoryImpl compute() {
      final String path = getFileSystem().resolveSymLink(SymlinkDirectory.this);
      VirtualFile file = null;
      if (path != null)  {
        file = findFile(path, false);
      }
      if (file == SymlinkDirectory.this && file != null) {
        final VirtualFile parent = file.getParent();
        if (parent instanceof VirtualDirectoryImpl) {
          ((VirtualDirectoryImpl)parent).removeChild(file);
          file = findFile(path, true);
        }
      }
      return file instanceof VirtualDirectoryImpl ? (VirtualDirectoryImpl)file : new VirtualDirectoryImpl("foo", SymlinkDirectory.this, getFileSystem(), 0);
    }

    @Nullable
    private VirtualFile findFile(final String path, final boolean refresh) {
      return RecursionManager.doPreventingRecursion(SymlinkDirectory.this, false, new NullableComputable<VirtualFile>() {
        @Override
        public VirtualFile compute() {
          return refresh ? VfsImplUtil.refreshAndFindFileByPath(getFileSystem(), path) : VfsImplUtil.findFileByPath(getFileSystem(), path);
        }
      });
    }
  };

  public SymlinkDirectory(@NotNull String name, final VirtualDirectoryImpl parent, @NotNull final NewVirtualFileSystem fs, final int id) {
    super(name, parent, fs, id);
  }

  @Override
  public VirtualFileSystemEntry findChild(@NotNull String name) {
    return myTarget.getValue().findChild(name);
  }

  @Override
  public NewVirtualFile findChildIfCached(@NotNull String name) {
    return myTarget.getValue().findChildIfCached(name);
  }

  @Override
  public NewVirtualFile findChildById(int id) {
    return myTarget.getValue().findChildById(id);
  }

  @Override
  public NewVirtualFile findChildByIdIfCached(int id) {
    return myTarget.getValue().findChildByIdIfCached(id);
  }

  @Override
  public NewVirtualFile refreshAndFindChild(@NotNull String name) {
    return myTarget.getValue().refreshAndFindChild(name);
  }

  @NotNull
  @Override
  public VirtualFile[] getChildren() {
    return myTarget.getValue().getChildren();
  }
}
