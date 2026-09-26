// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
interface ChildScanner {
  record ScannedChildren(ChildInfo @NotNull [] children, boolean childrenComplete) {
  }

  /// Traverses children of `childName` recursively, so they are loaded into VFS during event processing
  ///
  /// @return `null` if error occurred or children must not be loaded proactively. Children to load into VFS otherwise
  @Nullable ScannedChildren scanChildrenRecursively(@NotNull NewVirtualFile parent, @NotNull String childName);
}
