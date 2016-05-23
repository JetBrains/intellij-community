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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class InlayImpl extends RangeMarkerImpl implements Inlay, Getter<InlayImpl> {
  private final EditorImpl myEditor;
  private final int myWidthInPixels;
  private final Renderer myRenderer;

  InlayImpl(@NotNull EditorImpl editor, int offset, int widthInPixels, @Nullable Renderer renderer) {
    super(editor.getDocument(), offset, offset, false);
    myEditor = editor;
    myWidthInPixels = widthInPixels;
    myRenderer = renderer;
    myEditor.getInlayModel().myInlayTree.addInterval(this, offset, offset, false, false, 0);
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    super.changedUpdateImpl(e);
    if (isValid() && getOffset() >= e.getOffset() && getOffset() <= e.getOffset() + e.getNewLength()) {
      invalidate(e);
    }
  }

  @Override
  public void dispose() {
    if (isValid()) {
      myEditor.getInlayModel().myInlayTree.removeInterval(this);
    }
  }

  @Override
  public int getOffset() {
    return getStartOffset();
  }

  @Nullable
  @Override
  public Renderer getRenderer() {
    return myRenderer;
  }

  @Override
  public int getWidthInPixels() {
    return myWidthInPixels;
  }

  @Override
  public InlayImpl get() {
    return this;
  }
}
