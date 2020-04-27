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
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
class TextComponentSelectionModel implements SelectionModel {
  private final TextComponentEditor myEditor;

  TextComponentSelectionModel(@NotNull TextComponentEditorImpl textComponentEditor) {
    myEditor = textComponentEditor;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  @Nullable
  @Override
  public VisualPosition getSelectionStartPosition() {
    return null;
  }

  @Nullable
  @Override
  public VisualPosition getSelectionEndPosition() {
    return null;
  }

  @Nullable
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return null;
  }

  @Override
  public void addSelectionListener(@NotNull final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeSelectionListener(@NotNull final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void copySelectionToClipboard() {
    if (! (myEditor.getContentComponent() instanceof JPasswordField)) {
      EditorCopyPasteHelper.getInstance().copySelectionToClipboard(myEditor);
    }
  }

  @Override
  public void setBlockSelection(@NotNull final LogicalPosition blockStart, @NotNull final LogicalPosition blockEnd) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int @NotNull [] getBlockSelectionStarts() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int @NotNull [] getBlockSelectionEnds() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public TextAttributes getTextAttributes() {
    return null;
  }
}
