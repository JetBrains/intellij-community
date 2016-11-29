/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FileTypeChooser {
  /**
   * If fileName is already associated any known file type returns it.
   * Otherwise asks user to select file type and associates it with fileName extension if any selected.
   *
   * @return Known file type or null. Never returns {@link com.intellij.openapi.fileTypes.FileTypes#UNKNOWN}.
   */
  @Nullable
  public static FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @Nullable Project project) {
    if (project != null && !(file instanceof FakeVirtualFile)) {
      PsiManagerEx.getInstanceEx(project).getFileManager().findFile(file); // autodetect text file if needed
    }
    FileType type = file.getFileType();
    if (type == FileTypes.UNKNOWN) {
      type = getKnownFileTypeOrAssociate(file.getName());
    }
    return type;
  }

  /**
   * Speculates if file with newName would have known file type
   */
  @Nullable
  public static FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile parent, @NotNull String newName, @Nullable Project project) {
    return getKnownFileTypeOrAssociate(new FakeVirtualFile(parent, newName), project);
  }

  @Nullable
  public static FileType getKnownFileTypeOrAssociate(@NotNull String fileName) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(fileName);
    if (type == FileTypes.UNKNOWN) {
      type = associateFileType(fileName);
    }
    return type;
  }

  @Nullable
  public static FileType associateFileType(@NotNull final String fileName) {
    final FileTypeChooserDialog chooser =
      FileTypeChooserDialogFactory.SERVICE.getInstance().createFileTypeChooserDialog(suggestPatterns(fileName), fileName);
    if (!chooser.showAndGet()) {
      return null;
    }
    final FileType type = chooser.getSelectedType();
    if (type == FileTypes.UNKNOWN || type == null) return null;

    ApplicationManager.getApplication().runWriteAction(() -> FileTypeManagerEx.getInstanceEx().associatePattern(type, chooser.getSelectedItem()));

    return type;
  }

  @NotNull
  static List<String> suggestPatterns(@NotNull String fileName) {
    List<String> patterns = ContainerUtil.newLinkedList(fileName);

    int i = -1;
    while ((i = fileName.indexOf('.', i + 1)) > 0) {
      String extension = fileName.substring(i);
      if (!StringUtil.isEmpty(extension)) {
        patterns.add(0, "*" + extension);
      }
    }

    return patterns;
  }
}
