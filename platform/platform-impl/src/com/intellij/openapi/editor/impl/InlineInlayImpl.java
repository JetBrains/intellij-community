// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Point;
import java.util.List;

import static com.intellij.openapi.editor.impl.InlayKeys.ID_BEFORE_DISPOSAL;
import static com.intellij.openapi.editor.impl.InlayKeys.ORDER_BEFORE_DISPOSAL;

/**
 * @see InlayModel#addInlineElement
 */
final class InlineInlayImpl<R extends EditorCustomElementRenderer> extends InlayImpl<R, InlineInlayImpl<?>> {
  final int myPriority;

  InlineInlayImpl(@NotNull EditorImpl editor,
                  int offset,
                  boolean relatesToPrecedingText,
                  int priority,
                  @NotNull R renderer) {
    super(editor, offset, relatesToPrecedingText, renderer);
    myPriority = priority;
  }

  @Override
  public RangeMarkerTree<InlineInlayImpl<?>> getTree() {
    return myEditor.getInlayModel().myInlineElementsTree;
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    myEditor.getInlayModel().myPutMergedIntervalsAtBeginning = intervalStart() == e.getOffset();
    super.changedUpdateImpl(e);
    if (isValid() && DocumentUtil.isInsideSurrogatePair(getDocument(), intervalStart())) {
      invalidate();
    }
  }

  @Override
  protected void onReTarget(@NotNull DocumentEvent e) {
    InlayModelImpl inlayModel = myEditor.getInlayModel();
    inlayModel.myPutMergedIntervalsAtBeginning = intervalStart() == e.getMoveOffset() + e.getNewLength();
    if (DocumentUtil.isInsideSurrogatePair(getDocument(), getOffset())) {
      inlayModel.myMoveInProgress = true;
      try {
        invalidate();
      }
      finally {
        inlayModel.myMoveInProgress = false;
      }
    }
  }

  @Override
  public void dispose() {
    if (isValid()) {
      int offset = getOffset();
      List<Inlay<?>> inlays = myEditor.getInlayModel().getInlineElementsInRange(offset, offset);
      putUserData(ORDER_BEFORE_DISPOSAL, inlays.indexOf(this));
      putUserData(ID_BEFORE_DISPOSAL, getId());
    }
    super.dispose();
  }

  @Override
  void doUpdate() {
    myWidthInPixels = myRenderer.calcWidthInPixels(this);
    if (myWidthInPixels <= 0) {
      throw PluginException.createByClass(
        "Positive width should be defined for an inline element by " + myRenderer +
        " (class=" + myRenderer.getClass().getName() + ", valid=" + isValid() + ", myWidthInPixels=" + myWidthInPixels + ")",
        null, myRenderer.getClass()
      );
    }
  }

  @Override
  public @NotNull Placement getPlacement() {
    return Placement.INLINE;
  }

  @Override
  public @NotNull VisualPosition getVisualPosition() {
    int offset = getOffset();
    List<Inlay<?>> inlays = myEditor.getInlayModel().getInlineElementsInRange(offset, offset);
    List<CustomWrap> customWraps = myEditor.getCustomWrapModel().getWrapsAtOffset(offset);
    if (customWraps.isEmpty()) {
      VisualPosition pos = myEditor.offsetToVisualPosition(offset, false, false);
      int order = inlays.indexOf(this);
      return new VisualPosition(pos.line, pos.column + order, true);
    }
    else {
      int firstRelatedToPrecedingIndex = ContainerUtil.indexOf(inlays, inlay -> inlay.isRelatedToPrecedingText());
      firstRelatedToPrecedingIndex = firstRelatedToPrecedingIndex >= 0 ? firstRelatedToPrecedingIndex : inlays.size();
      VisualPosition pos = myEditor.offsetToVisualPosition(offset, false, isRelatedToPrecedingText());
      int order = inlays.indexOf(this);
      return new VisualPosition(pos.line, pos.column + order - (isRelatedToPrecedingText() ? firstRelatedToPrecedingIndex : 0), true);
    }
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

  int getOrder() {
    Integer value = getUserData(ORDER_BEFORE_DISPOSAL);
    return value == null ? -1 : value;
  }

  @Override
  public @NotNull InlayProperties getProperties() {
    return new InlayProperties()
      .relatesToPrecedingText(isRelatedToPrecedingText())
      .priority(myPriority);
  }

  @Override
  public String toString() {
    return "[Inline inlay, offset=" + getOffset() + ", width=" + myWidthInPixels + ", renderer=" + myRenderer + "]" + (isValid() ? "" : "(invalid)");
  }
}
