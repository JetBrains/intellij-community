// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VcsSymlinkResolver {
  ExtensionPointName<VcsSymlinkResolver> EP_NAME = new ExtensionPointName<>("com.intellij.vcs.vcsSymlinkResolver");

  boolean isEnabled();

  @Nullable
  VirtualFile resolveSymlink(@NotNull VirtualFile file);
}
