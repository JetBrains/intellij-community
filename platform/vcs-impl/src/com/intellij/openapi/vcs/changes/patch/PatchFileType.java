/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileTypes.FileType;
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

  @NotNull
  @NonNls
  public String getName() {
    return NAME;
  }

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

  @Nullable
  public Icon getIcon() {
    return AllIcons.Vcs.Patch;
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
    return vFile != null && vFile.getFileType() == StdFileTypes.PATCH;
  }

  public static boolean isPatchFile(@NotNull File file) {
    return isPatchFile(VfsUtil.findFileByIoFile(file, true));
  }
}
