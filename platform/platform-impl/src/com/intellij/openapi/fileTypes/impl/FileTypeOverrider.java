// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to override the file type for a file. Overrides take precedence over all other ways of determining the type of the file
 * (name checks, content checks, {@link com.intellij.openapi.fileTypes.FileTypeRegistry.FileTypeDetector}). An overridden file type
 * completely replaces the file's normal file type for PSI, actions and all other features.
 *
 * If the override conditions for a given {@code FileTypeOverrider} change, it needs to call
 * {@link com.intellij.util.FileContentUtilCore#reparseFiles(VirtualFile...)} if it's possible to identify specific files affected
 * by the change, or {@link FileTypeManagerEx#fireFileTypesChanged()} if the change affects an unknown number of files.
 *
 * @author yole
 */
@ApiStatus.Experimental
public interface FileTypeOverrider {
  ExtensionPointName<FileTypeOverrider> EP_NAME = ExtensionPointName.create("com.intellij.fileTypeOverrider");

  @Nullable
  FileType getOverriddenFileType(@NotNull VirtualFile file);
}
