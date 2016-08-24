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

import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

class InlayImpl extends RangeMarkerImpl implements Inlay, Getter<InlayImpl> {
  @NotNull
  private final EditorImpl myEditor;
  final int myOriginalOffset; // used for sorting of inlays, if they ever get merged into same offset after document modification
  int myOffsetBeforeDisposal = -1;
  private int myWidthInPixels;
  @NotNull
  private final EditorCustomElementRenderer myRenderer;

  InlayImpl(@NotNull EditorImpl editor, int offset, @NotNull EditorCustomElementRenderer renderer) {
    super(editor.getDocument(), offset, offset, false);
    myEditor = editor;
    myOriginalOffset = offset;
    myRenderer = renderer;
    doUpdateSize();
    myEditor.getInlayModel().myInlayTree.addInterval(this, offset, offset, false, false, 0);
  }

  @Override
  public void updateSize() {
    doUpdateSize();
    myEditor.getInlayModel().notifyChanged(this);
  }

  private void doUpdateSize() {
    myWidthInPixels = myRenderer.calcWidthInPixels(myEditor);
    if (myWidthInPixels <= 0) {
      throw new IllegalArgumentException("Positive width should be defined for an inline element");
    }
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    super.changedUpdateImpl(e);
    if (myEditor.getInlayModel().myStickToLargerOffsetsOnUpdate && isValid() && e.getOldLength() == 0 && getOffset() == e.getOffset()) {
      int newOffset = e.getOffset() + e.getNewLength();
      setIntervalStart(newOffset);
      setIntervalEnd(newOffset);
    }
  }

  @Override
  public void dispose() {
    if (isValid()) {
      myOffsetBeforeDisposal = getOffset(); // We want listeners notified after disposal, but want inlay offset to be available at that time
      myEditor.getInlayModel().myInlayTree.removeInterval(this);
      myEditor.getInlayModel().notifyRemoved(this);
    }
  }

  @Override
  public int getOffset() {
    return myOffsetBeforeDisposal == -1 ? getStartOffset() : myOffsetBeforeDisposal;
  }

  @NotNull
  @Override
  public EditorCustomElementRenderer getRenderer() {
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
