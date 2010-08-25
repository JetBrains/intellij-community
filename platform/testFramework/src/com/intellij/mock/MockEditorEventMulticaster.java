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
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.*;
import org.jetbrains.annotations.NotNull;

public class MockEditorEventMulticaster implements EditorEventMulticaster {
  public MockEditorEventMulticaster() {
  }

  public void addDocumentListener(@NotNull DocumentListener listener) {
  }

  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
      }

  public void removeDocumentListener(@NotNull DocumentListener listener) {
  }

  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
  }

  public void addEditorMouseListener(@NotNull final EditorMouseListener listener, @NotNull final Disposable parentDisposable) {
  }

  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
  }

  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
  }

  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener, @NotNull Disposable parentDisposable) {
  }

  public void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
  }

  public void addCaretListener(@NotNull CaretListener listener) {
  }

  public void addCaretListener(@NotNull CaretListener listener, @NotNull Disposable parentDisposable) {
  }

  public void removeCaretListener(@NotNull CaretListener listener) {
  }

  public void addSelectionListener(@NotNull SelectionListener listener) {
  }

  public void removeSelectionListener(@NotNull SelectionListener listener) {
  }

  public void addVisibleAreaListener(@NotNull VisibleAreaListener listener) {
  }

  public void removeVisibleAreaListener(@NotNull VisibleAreaListener listener) {
  }

}
