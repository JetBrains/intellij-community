/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ui.util.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class FileAppearanceServiceImpl extends FileAppearanceService {
  private static CellAppearanceEx EMPTY = new CellAppearanceEx() {
    @Override
    public void customize(@NotNull SimpleColoredComponent component) { }

    @Override
    public void customize(@NotNull HtmlListCellRenderer renderer) { }

    @NotNull
    @Override
    public String getText() { return ""; }
  };

  @NotNull
  @Override
  public CellAppearanceEx empty() {
    return EMPTY;
  }

  @NotNull
  @Override
  public CellAppearanceEx forVirtualFile(@NotNull final VirtualFile file) {
    if (!file.isValid()) {
      return forInvalidUrl(file.getPresentableUrl());
    }

    final VirtualFileSystem fileSystem = file.getFileSystem();
    if (fileSystem.getProtocol().equals(JarFileSystem.PROTOCOL)) {
      return new JarSubfileCellAppearance(file);
    }
    if (fileSystem instanceof HttpFileSystem) {
      return new HttpUrlCellAppearance(file);
    }
    if (file.isDirectory()) {
      return SimpleTextCellAppearance.regular(file.getPresentableUrl(), PlatformIcons.FOLDER_ICON);
    }
    return new ValidFileCellAppearance(file);
  }

  @NotNull
  @Override
  public CellAppearanceEx forIoFile(@NotNull final File file) {
    final String absolutePath = file.getAbsolutePath();
    if (!file.exists()) {
      return forInvalidUrl(absolutePath);
    }

    if (file.isDirectory()) {
      return SimpleTextCellAppearance.regular(absolutePath, PlatformIcons.FOLDER_ICON);
    }

    final String name = file.getName();
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    final File parent = file.getParentFile();
    final CompositeAppearance appearance = CompositeAppearance.textComment(name, parent.getAbsolutePath());
    appearance.setIcon(fileType.getIcon());
    return appearance;
  }

  @Override
  @NotNull
  public CellAppearanceEx forInvalidUrl(@NotNull final String text) {
    return SimpleTextCellAppearance.invalid(text, PlatformIcons.INVALID_ENTRY_ICON);
  }
}
