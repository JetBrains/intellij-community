// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EditorFileSwapper {
  @Nullable Pair<VirtualFile, @Nullable Integer> getFileToSwapTo(Project project, EditorComposite composite);

  static @Nullable TextEditorImpl findSinglePsiAwareEditor(@NotNull List<? extends FileEditor> fileEditors) {
    TextEditorImpl result = null;
    for (FileEditor fileEditor : fileEditors) {
      if (fileEditor instanceof TextEditorImpl) {
        if (result == null) {
          result = (TextEditorImpl)fileEditor;
        }
        else {
          return null;
        }
      }
    }
    return result;
  }
}
