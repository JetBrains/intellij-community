// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public final class Win32LocalFileSystem extends LocalFileSystemBase {
  public static boolean isAvailable() {
    return IdeaWin32.isAvailable();
  }

  private static final ThreadLocal<Win32LocalFileSystem> THREAD_LOCAL = ThreadLocal.withInitial(Win32LocalFileSystem::new);

  @NotNull
  public static Win32LocalFileSystem getWin32Instance() {
    if (!isAvailable()) throw new RuntimeException("Native filesystem for Windows is not loaded");
    Win32LocalFileSystem fileSystem = THREAD_LOCAL.get();
    fileSystem.myFsCache.clearCache();
    return fileSystem;
  }

  private final Win32FsCache myFsCache = new Win32FsCache();

  private Win32LocalFileSystem() { }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    return myFsCache.list(file);
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    return myFsCache.getAttributes(file);
  }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    throw new UnsupportedOperationException();
  }
}