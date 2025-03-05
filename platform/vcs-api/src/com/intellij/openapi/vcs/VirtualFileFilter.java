// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class VirtualFileFilter {
  public abstract boolean shouldGoIntoDirectory(final @NotNull VirtualFile file);
  public void afterChildrenVisited(@NotNull VirtualFile file) {}
}
