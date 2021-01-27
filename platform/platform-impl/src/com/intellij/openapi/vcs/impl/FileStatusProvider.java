// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface FileStatusProvider {
  @ApiStatus.Internal
  ProjectExtensionPointName<FileStatusProvider> EP_NAME = new ProjectExtensionPointName<>("com.intellij.vcs.fileStatusProvider");

  FileStatus getFileStatus(@NotNull VirtualFile virtualFile);
}