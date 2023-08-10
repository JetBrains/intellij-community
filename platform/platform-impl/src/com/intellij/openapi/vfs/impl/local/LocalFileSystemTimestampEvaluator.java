// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@ApiStatus.Internal
public interface LocalFileSystemTimestampEvaluator {
  ExtensionPointName<LocalFileSystemTimestampEvaluator> EP_NAME = ExtensionPointName.create("com.intellij.localFileSystemTimestampEvaluator");

  @Nullable Long getTimestamp(@NotNull VirtualFile file);

  /**
   * Can be used when VirtualFile for the path has not been loaded yet.
   */
  @Nullable Long getTimestamp(@NotNull Path file);
}
