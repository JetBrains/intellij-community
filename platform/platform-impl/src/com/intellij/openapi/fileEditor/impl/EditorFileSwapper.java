// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class EditorFileSwapper {
  public static final ExtensionPointName<EditorFileSwapper> EP_NAME = new ExtensionPointName<>("com.intellij.editorFileSwapper");

  /**
   * @deprecated Use {@link #getFileToSwapTo(Project, EditorComposite)}
   */
  @Deprecated
  public @Nullable Pair<VirtualFile, Integer> getFileToSwapTo(Project project, EditorWithProviderComposite editorWithProviderComposite) {
    PluginException.reportDeprecatedUsage("EditorFileSwapper#getFileToSwapTo(Project, EditorWithProviderComposite)",
                                          "Use overload that accepts EditorComposite");
    return null;
  }

  public @Nullable Pair<VirtualFile, Integer> getFileToSwapTo(Project project, EditorComposite composite) {
    return getFileToSwapTo(project, (EditorWithProviderComposite) composite);
  }

  public static @Nullable TextEditorImpl findSinglePsiAwareEditor(@NotNull List<? extends FileEditor> fileEditors) {
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
