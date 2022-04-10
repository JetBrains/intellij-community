// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Can be used to override file color in 'Project View' and Editor titles.
 *
 * @see com.intellij.openapi.vcs.FileStatusManager#getStatus(VirtualFile)
 */
public interface FileStatusProvider {
  @ApiStatus.Internal
  ProjectExtensionPointName<FileStatusProvider> EP_NAME = new ProjectExtensionPointName<>("com.intellij.vcs.fileStatusProvider");

  @Nullable
  FileStatus getFileStatus(@NotNull VirtualFile virtualFile);
}