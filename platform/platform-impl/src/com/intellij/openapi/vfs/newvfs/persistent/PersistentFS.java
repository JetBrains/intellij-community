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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class PersistentFS extends ManagingFS {
  static final int CHILDREN_CACHED_FLAG = 0x01;
  static final int IS_DIRECTORY_FLAG = 0x02;
  static final int IS_READ_ONLY = 0x04;
  static final int MUST_RELOAD_CONTENT = 0x08;
  static final int IS_SYMLINK = 0x10;
  static final int IS_SPECIAL = 0x20;

  static final int ALL_VALID_FLAGS =
    CHILDREN_CACHED_FLAG | IS_DIRECTORY_FLAG | IS_READ_ONLY | MUST_RELOAD_CONTENT | IS_SYMLINK | IS_SPECIAL;

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static PersistentFS getInstance() {
    return (PersistentFS)ManagingFS.getInstance();
  }

  public abstract void clearIdCache();

  @NotNull
  public abstract String[] listPersisted(@NotNull VirtualFile parent);

  @NotNull
  public abstract Pair<String[],int[]> listAll(@NotNull VirtualFile parent);

  public abstract int getId(@NotNull VirtualFile parent, @NotNull String childName, @NotNull NewVirtualFileSystem delegate);

  public abstract String getName(int id);

  public abstract boolean isDirectory(int id);

  @Nullable
  public abstract NewVirtualFile findFileByIdIfCached(int id);

  public abstract int storeUnlinkedContent(@NotNull byte[] bytes);

  @NotNull
  public abstract byte[] contentsToByteArray(int contentId) throws IOException;

  @NotNull
  public abstract byte[] contentsToByteArray(@NotNull VirtualFile file, boolean cacheContent) throws IOException;

  public abstract int acquireContent(@NotNull VirtualFile file);

  public abstract void releaseContent(int contentId);

  public abstract int getCurrentContentId(@NotNull VirtualFile file);

  @NotNull
  public static NewVirtualFileSystem replaceWithNativeFS(@NotNull final NewVirtualFileSystem fs) {
    if (SystemInfo.isWindows &&
        !(fs instanceof Win32LocalFileSystem) &&
        fs.getProtocol().equals(LocalFileSystem.PROTOCOL) &&
        Win32LocalFileSystem.isAvailable()) {
      return Win32LocalFileSystem.getWin32Instance();
    }
    return fs;
  }
}
