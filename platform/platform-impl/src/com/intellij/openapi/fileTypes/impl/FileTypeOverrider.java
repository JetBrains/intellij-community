// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface FileTypeOverrider {
  static ExtensionPointName<FileTypeOverrider> EP_NAME = ExtensionPointName.create("com.intellij.fileTypeOverrider");

  @Nullable
  FileType getOverriddenFileType(@NotNull VirtualFile file);
}
