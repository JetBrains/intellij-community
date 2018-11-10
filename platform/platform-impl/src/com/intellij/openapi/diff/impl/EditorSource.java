// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 * @author max
 */
public interface EditorSource {
  @Nullable FragmentSide getSide();

  @Nullable DiffContent getContent();

  @Nullable EditorEx getEditor();

  @Nullable FileEditor getFileEditor();

  void addDisposable(@NotNull Disposable disposable);

  EditorSource NULL = new EditorSource() {
    @Override
    public EditorEx getEditor() {
      return null;
    }

    @Override
    public FileEditor getFileEditor() {
      return null;
    }

    @Override
    public void addDisposable(@NotNull Disposable disposable) {
      Logger.getInstance("#com.intellij.openapi.diff.impl.EditorSource").assertTrue(false);
    }

    @Override
    @Nullable
    public FragmentSide getSide() {
      return null;
    }

    @Override
    @Nullable
    public DiffContent getContent() {
      return null;
    }
  };
}
