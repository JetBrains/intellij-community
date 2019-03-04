// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.cachedValueProfiler;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CVPFileType implements FileType {
  public static final CVPFileType INSTANCE = new CVPFileType();

  private CVPFileType() {
  }

  @NotNull
  @Override
  public String getName() {
    return "CVP";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Cached value profiling snapshot";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "cvp";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.General.ContextHelp;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }
}
