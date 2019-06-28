// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class PatchFileType implements FileType {
  public static final String NAME = "PATCH";

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return NAME;
  }

  @Override
  @NotNull
  public String getDescription() {
    return VcsBundle.message("patch.file.type.description");
  }

  @Override
  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return "patch";
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return AllIcons.Vcs.Patch_file;
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
  @Nullable
  @NonNls
  public String getCharset(@NotNull VirtualFile file, @NotNull final byte[] content) {
    return null;
  }

  @Nullable
  public SyntaxHighlighter getHighlighter(@Nullable Project project, final VirtualFile virtualFile) {
    return null;
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull VirtualFile file, @NotNull Project project) {
    return null;
  }

  public static boolean isPatchFile(@Nullable VirtualFile vFile) {
    return vFile != null && FileTypeRegistry.getInstance().isFileOfType(vFile, StdFileTypes.PATCH);
  }

  public static boolean isPatchFile(@NotNull File file) {
    return isPatchFile(VfsUtil.findFileByIoFile(file, true));
  }
}
