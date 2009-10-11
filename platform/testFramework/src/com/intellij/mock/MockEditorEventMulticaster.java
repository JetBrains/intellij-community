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

public class MockEditorEventMulticaster implements EditorEventMulticaster {
  public MockEditorEventMulticaster() {
  }

  public void addDocumentListener(DocumentListener listener) {
  }

  public void addDocumentListener(DocumentListener listener, Disposable parentDisposable) {
      }

  public void removeDocumentListener(DocumentListener listener) {
  }

  public void addEditorMouseListener(EditorMouseListener listener) {
  }

  public void addEditorMouseListener(final EditorMouseListener listener, final Disposable parentDisposable) {
  }

  public void removeEditorMouseListener(EditorMouseListener listener) {
  }

  public void addEditorMouseMotionListener(EditorMouseMotionListener listener) {
  }

  public void addEditorMouseMotionListener(EditorMouseMotionListener listener, Disposable parentDisposable) {
  }

  public void removeEditorMouseMotionListener(EditorMouseMotionListener listener) {
  }

  public void addCaretListener(CaretListener listener) {
  }

  public void addCaretListener(CaretListener listener, Disposable parentDisposable) {
  }

  public void removeCaretListener(CaretListener listener) {
  }

  public void addSelectionListener(SelectionListener listener) {
  }

  public void removeSelectionListener(SelectionListener listener) {
  }

  public void addVisibleAreaListener(VisibleAreaListener listener) {
  }

  public void removeVisibleAreaListener(VisibleAreaListener listener) {
  }

}
