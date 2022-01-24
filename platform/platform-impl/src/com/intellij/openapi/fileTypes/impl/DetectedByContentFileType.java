// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
    return FileTypesBundle.message("filetype.autodetected.description");
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return FileTypesBundle.message("filetype.autodetected.display.name");
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
}
