// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class PatchFileType implements FileType {
  public static final PatchFileType INSTANCE = new PatchFileType();

  public static final String NAME = "PATCH"; //NON-NLS

  private PatchFileType() {
  }

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return NAME;
  }

  @Override
  @NotNull
  public String getDescription() {
    return VcsBundle.message("filetype.patch.description");
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return VcsBundle.message("filetype.patch.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return "patch";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Vcs.Patch_file;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  public static boolean isPatchFile(@Nullable VirtualFile vFile) {
    return vFile != null && FileTypeRegistry.getInstance().isFileOfType(vFile, INSTANCE);
  }

  public static boolean isPatchFile(@NotNull File file) {
    return isPatchFile(VfsUtil.findFileByIoFile(file, true));
  }
}
