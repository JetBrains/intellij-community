/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    public EditorEx getEditor() {
      return null;
    }

    @Override
    public FileEditor getFileEditor() {
      return null;
    }

    public void addDisposable(@NotNull Disposable disposable) {
      Logger.getInstance("#com.intellij.openapi.diff.impl.EditorSource").assertTrue(false);
    }

    @Nullable
    public FragmentSide getSide() {
      return null;
    }

    @Nullable
    public DiffContent getContent() {
      return null;
    }
  };
}
