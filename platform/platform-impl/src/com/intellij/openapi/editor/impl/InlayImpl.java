/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class InlayImpl extends RangeMarkerImpl implements Inlay, Getter<InlayImpl> {
  private static final Key<Integer> ORDER_KEY = Key.create("inlay.order.key");

  @NotNull
  private final EditorImpl myEditor;
  private final boolean myRelatedToPrecedingText;
  final int myOriginalOffset; // used for sorting of inlays, if they ever get merged into same offset after document modification
  int myOffsetBeforeDisposal = -1;
  private int myWidthInPixels;
  @NotNull
  private final EditorCustomElementRenderer myRenderer;

  InlayImpl(@NotNull EditorImpl editor, int offset, boolean relatesToPreceedingText, @NotNull EditorCustomElementRenderer renderer) {
    super(editor.getDocument(), offset, offset, false);
    myEditor = editor;
    myRelatedToPrecedingText = relatesToPreceedingText;
    myOriginalOffset = offset;
    myRenderer = renderer;
    doUpdateSize();
    myEditor.getInlayModel().myInlayTree.addInterval(this, offset, offset, false, false, false, 0);
  }

  @Override
  public void updateSize() {
    doUpdateSize();
    myEditor.getInlayModel().notifyChanged(this);
  }

  @Override
  public void repaint() {
    if (isValid() && !myEditor.isDisposed()) {
      int offset = getOffset();
      myEditor.repaint(offset, offset, false);
    }
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
    if (isValid()) {
      if (DocumentUtil.isInsideSurrogatePair(getDocument(), intervalStart())) {
        invalidate(e);
      }
      else if (myRelatedToPrecedingText && e.getOldLength() == 0) {
        int newOffset = e.getOffset() + e.getNewLength();
        setIntervalStart(newOffset);
        setIntervalEnd(newOffset);
      }
    }
  }

  @Override
  protected void onReTarget(int startOffset, int endOffset, int destOffset) {
    if (DocumentUtil.isInsideSurrogatePair(getDocument(), getOffset())) {
      myEditor.getInlayModel().myMoveInProgress = true;
      try {
        invalidate("moved inside surrogate pair on retarget");
      }
      finally {
        myEditor.getInlayModel().myMoveInProgress = false;
      }
    }
  }

  @Override
  public void dispose() {
    if (isValid()) {
      myOffsetBeforeDisposal = getOffset(); // We want listeners notified after disposal, but want inlay offset to be available at that time
      InlayModelImpl inlayModel = myEditor.getInlayModel();
      List<Inlay> inlays = inlayModel.getInlineElementsInRange(myOffsetBeforeDisposal, myOffsetBeforeDisposal);
      putUserData(ORDER_KEY, inlays.indexOf(this));
      inlayModel.myInlayTree.removeInterval(this);
      inlayModel.notifyRemoved(this);
    }
  }

  @Override
  public int getOffset() {
    return myOffsetBeforeDisposal == -1 ? getStartOffset() : myOffsetBeforeDisposal;
  }

  @Override
  public boolean isRelatedToPrecedingText() {
    return myRelatedToPrecedingText;
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    int offset = getOffset();
    VisualPosition pos = myEditor.offsetToVisualPosition(offset);
    List<Inlay> inlays = myEditor.getInlayModel().getInlineElementsInRange(offset, offset);
    int order = inlays.indexOf(this);
    return new VisualPosition(pos.line, pos.column + order, true);
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

  int getOrder() {
    Integer value = getUserData(ORDER_KEY);
    return value == null ? -1 : value;
  }
}
