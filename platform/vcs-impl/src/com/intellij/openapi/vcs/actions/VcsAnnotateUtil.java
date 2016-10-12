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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class VcsAnnotateUtil {
  @NotNull
  public static List<Editor> getEditors(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
    return ContainerUtil.mapNotNull(editors, new Function<FileEditor, Editor>() {
      @Override
      public Editor fun(FileEditor fileEditor) {
        return fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
      }
    });
  }

  @NotNull
  public static BackgroundableActionLock getBackgroundableLock(@NotNull Project project, @NotNull VirtualFile file) {
    return BackgroundableActionLock.getLock(project, VcsBackgroundableActions.ANNOTATE, file.getPath());
  }
}
