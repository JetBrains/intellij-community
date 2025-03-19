// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public interface BatchingFileSystem {
  /**
   * Returns a list of files in a directory along with their attributes.
   * When the {@code childrenNames} set is {@code null}, all files should be returned.
   */
  @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir, @Nullable Set<String> childrenNames);
}
