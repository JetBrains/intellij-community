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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.editor.CustomFileDropHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

final class PatchFileDropHandler extends CustomFileDropHandler {
  @Override
  public boolean canHandle(@NotNull Transferable t, @Nullable Editor editor) {
    List<File> list = FileCopyPasteUtil.getFileList(t);
    if (list == null || list.size() != 1) return false;
    return looksLikePatchFile(list.get(0));
  }

  @Override
  public boolean handleDrop(@NotNull Transferable t, @Nullable Editor editor, @NotNull Project project) {
    List<File> list = FileCopyPasteUtil.getFileList(t);
    if (list == null || list.size() != 1) return false;
    return ApplyPatchAction.showAndGetApplyPatch(project, list.get(0));
  }

  private static boolean looksLikePatchFile(@NotNull File file) {
    // do not use VFS here to detect the file type, may lead to freezes
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(file.getName());
    return PatchFileType.INSTANCE == fileType;
  }
}
