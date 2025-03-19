// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.*;
import org.jetbrains.annotations.NotNull;

public class MockEditorEventMulticaster implements EditorEventMulticaster {
  public MockEditorEventMulticaster() {
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
      }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
  }

  @Override
  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
  }

  @Override
  public void addEditorMouseListener(final @NotNull EditorMouseListener listener, final @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
  }

  @Override
  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
  }

  @Override
  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener) {
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
  }

  @Override
  public void addSelectionListener(@NotNull SelectionListener listener) {
  }

  @Override
  public void addSelectionListener(@NotNull SelectionListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeSelectionListener(@NotNull SelectionListener listener) {
  }

  @Override
  public void addVisibleAreaListener(@NotNull VisibleAreaListener listener) {
  }

  @Override
  public void addVisibleAreaListener(@NotNull VisibleAreaListener listener, @NotNull Disposable parent) {

  }

  @Override
  public void removeVisibleAreaListener(@NotNull VisibleAreaListener listener) {
  }

}
