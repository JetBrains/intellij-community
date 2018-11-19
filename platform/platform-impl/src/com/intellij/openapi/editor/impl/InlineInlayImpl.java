// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

class InlineInlayImpl<R extends EditorCustomElementRenderer> extends InlayImpl<R, InlineInlayImpl> {
  InlineInlayImpl(@NotNull EditorImpl editor,
                  int offset,
                  boolean relatesToPrecedingText,
                  @NotNull R renderer) {
    super(editor, offset, relatesToPrecedingText, renderer);
  }

  @Override
  RangeMarkerTree<InlineInlayImpl> getTree() {
    return myEditor.getInlayModel().myInlineElementsTree;
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    myEditor.getInlayModel().myPutMergedIntervalsAtBeginning = intervalStart() == e.getOffset();
    super.changedUpdateImpl(e);
    if (isValid() && DocumentUtil.isInsideSurrogatePair(getDocument(), intervalStart())) {
      invalidate(e);
    }
  }

  @Override
  protected void onReTarget(int startOffset, int endOffset, int destOffset) {
    InlayModelImpl inlayModel = myEditor.getInlayModel();
    inlayModel.myPutMergedIntervalsAtBeginning = intervalStart() == endOffset;
    if (DocumentUtil.isInsideSurrogatePair(getDocument(), getOffset())) {
      inlayModel.myMoveInProgress = true;
      try {
        invalidate("moved inside surrogate pair on retarget");
      }
      finally {
        inlayModel.myMoveInProgress = false;
      }
    }
  }

  @Override
  void doUpdateSize() {
    myWidthInPixels = myRenderer.calcWidthInPixels(this);
    if (myWidthInPixels <= 0) {
      throw new IllegalArgumentException("Positive width should be defined for an inline element");
    }
  }

  @NotNull
  @Override
  public VerticalAlignment getVerticalAlignment() {
    return VerticalAlignment.INLINE;
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

  @Override
  Point getPosition() {
    VisualPosition pos = getVisualPosition();
    return myEditor.visualPositionToXY(pos);
  }

  @Override
  public int getHeightInPixels() {
    return myEditor.getLineHeight();
  }
}
