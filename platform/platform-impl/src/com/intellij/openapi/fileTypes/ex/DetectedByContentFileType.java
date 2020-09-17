// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.ex;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class DetectedByContentFileType implements FileType {
  public static final DetectedByContentFileType INSTANCE = new DetectedByContentFileType();

  private DetectedByContentFileType() { }

  @Override
  public @NonNls @NotNull String getName() {
    return "AUTO_DETECTED";
  }

  @Override
  public @NlsContexts.Label @NotNull String getDescription() {
    return FileTypesBundle.message("filetype.autodetect");
  }

  @Override
  public @NlsSafe @NotNull String getDefaultExtension() {
    return "";
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public @NonNls @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    return null;
  }
}
