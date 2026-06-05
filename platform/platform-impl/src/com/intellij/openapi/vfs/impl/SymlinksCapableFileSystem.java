// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Trait for a file-system impl ([com.intellij.openapi.vfs.newvfs.FileSystemInterface]) that supports symlinks
/// and needs to be notified about their changes.
///
/// Low-level interface: to be used mostly inside platform VFS implementation.
@ApiStatus.Internal
public interface SymlinksCapableFileSystem {
  /// Returns `true` if symlinks are really supported, `false` otherwise
  /// (a file system implementation could be capable of symlinks in principle, but does not support them right now)
  boolean areSymlinksSupported();

  void symlinkUpdated(
    int fileId,
    @Nullable VirtualFile parent,
    @NotNull CharSequence name,
    @NotNull String linkPath,
    @Nullable String linkTarget
  );

  void symlinkRemoved(int fileId);
}
