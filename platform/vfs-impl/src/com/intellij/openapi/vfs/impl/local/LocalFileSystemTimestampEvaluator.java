// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface LocalFileSystemTimestampEvaluator {
  ExtensionPointName<LocalFileSystemTimestampEvaluator> EP_NAME = ExtensionPointName.create("com.intellij.localFileSystemTimestampEvaluator");

  @Nullable Long getTimestamp(@NotNull VirtualFile file);
}
