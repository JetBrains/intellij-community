/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * User: cdr
 */
class RangeHighlighterData {
  private final MarkupModel myModel;
  private final int myLayer;
  private final HighlighterTargetArea myTargetArea;
  private TextAttributes myTextAttributes;
  private LineMarkerRenderer myLineMarkerRenderer;
  private Color myErrorStripeColor;
  private Color myLineSeparatorColor;
  private SeparatorPlacement mySeparatorPlacement;
  private boolean isAfterEndOfLine;
  private GutterIconRenderer myGutterIconRenderer;
  private boolean myErrorStripeMarkIsThin;
  private Object myErrorStripeTooltip;
  private MarkupEditorFilter myFilter = MarkupEditorFilter.EMPTY;
  private CustomHighlighterRenderer myCustomRenderer;
  private final RangeHighlighterEx rangeHighlighter;

  RangeHighlighterData(@NotNull MarkupModel model,
                       int layer,
                       @NotNull HighlighterTargetArea target,
                       TextAttributes textAttributes,
                       @NotNull RangeHighlighterEx rangeHighlighter) {
    this.rangeHighlighter = rangeHighlighter;
    myTextAttributes = textAttributes;
    myTargetArea = target;
    myLayer = layer;
    myModel = model;
    if (textAttributes != null) {
      myErrorStripeColor = textAttributes.getErrorStripeColor();
    }
  }

  public TextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  public void setTextAttributes(TextAttributes textAttributes) {
    TextAttributes old = myTextAttributes;
    myTextAttributes = textAttributes;
    if (!Comparing.equal(old, textAttributes)) {
      fireChanged();
    }
  }

  public int getLayer() {
    return myLayer;
  }

  public HighlighterTargetArea getTargetArea() {
    return myTargetArea;
  }

  public LineMarkerRenderer getLineMarkerRenderer() {
    return myLineMarkerRenderer;
  }

  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    myLineMarkerRenderer = renderer;
    fireChanged();
  }

  public CustomHighlighterRenderer getCustomRenderer() {
    return myCustomRenderer;
  }

  public void setCustomRenderer(CustomHighlighterRenderer renderer) {
    myCustomRenderer = renderer;
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  public void setGutterIconRenderer(GutterIconRenderer renderer) {
    GutterIconRenderer old = myGutterIconRenderer;
    myGutterIconRenderer = renderer;
    if (!Comparing.equal(old, renderer)) {
      fireChanged();
    }
  }

  public Color getErrorStripeMarkColor() {
    return myErrorStripeColor;
  }

  public void setErrorStripeMarkColor(Color color) {
    Color old = myErrorStripeColor;
    myErrorStripeColor = color;
    if (!Comparing.equal(old, color)) {
      fireChanged();
    }
  }

  public Object getErrorStripeTooltip() {
    return myErrorStripeTooltip;
  }

  public void setErrorStripeTooltip(Object tooltipObject) {
    Object old = myErrorStripeTooltip;
    myErrorStripeTooltip = tooltipObject;
    if (!Comparing.equal(old, tooltipObject)) {
      fireChanged();
    }
  }

  public boolean isThinErrorStripeMark() {
    return myErrorStripeMarkIsThin;
  }

  public void setThinErrorStripeMark(boolean value) {
    boolean old = myErrorStripeMarkIsThin;
    myErrorStripeMarkIsThin = value;
    if (old != value) {
      fireChanged();
    }
  }

  public Color getLineSeparatorColor() {
    return myLineSeparatorColor;
  }

  public void setLineSeparatorColor(Color color) {
    Color old = myLineSeparatorColor;
    myLineSeparatorColor = color;
    if (!Comparing.equal(old, color)) {
      fireChanged();
    }
  }

  public SeparatorPlacement getLineSeparatorPlacement() {
    return mySeparatorPlacement;
  }

  public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
    SeparatorPlacement old = mySeparatorPlacement;
    mySeparatorPlacement = placement;
    if (!Comparing.equal(old, placement)) {
      fireChanged();
    }
  }

  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
    myFilter = filter;
    fireChanged();
  }

  @NotNull
  public MarkupEditorFilter getEditorFilter() {
    return myFilter;
  }

  public boolean isAfterEndOfLine() {
    return isAfterEndOfLine;
  }

  public void setAfterEndOfLine(boolean afterEndOfLine) {
    boolean old = isAfterEndOfLine;
    isAfterEndOfLine = afterEndOfLine;
    if (old != afterEndOfLine) {
      fireChanged();
    }
  }

  private boolean inBatchChange = false;
  private boolean changed = false;
  private void fireChanged() {
    if (myModel instanceof MarkupModelImpl) {
      if (inBatchChange) {
        changed = true;
      }
      else {
        ((MarkupModelImpl)myModel).fireAttributesChanged(rangeHighlighter);
      }
    }
  }

  public int getAffectedAreaStartOffset() {
    int startOffset = rangeHighlighter.getStartOffset();
    if (getTargetArea() == HighlighterTargetArea.EXACT_RANGE) return startOffset;
    if (startOffset == myModel.getDocument().getTextLength()) return startOffset;
    return myModel.getDocument().getLineStartOffset(myModel.getDocument().getLineNumber(startOffset));
  }

  public int getAffectedAreaEndOffset() {
    int endOffset = rangeHighlighter.getEndOffset();
    if (getTargetArea() == HighlighterTargetArea.EXACT_RANGE) return endOffset;
    int textLength = myModel.getDocument().getTextLength();
    if (endOffset == textLength) return endOffset;
    return Math.min(textLength, myModel.getDocument().getLineEndOffset(myModel.getDocument().getLineNumber(endOffset)) + 1);
  }

  public void registerMe() {
    ((MarkupModelImpl)myModel).addRangeHighlighter(rangeHighlighter);
  }

  public void unregisterMe() {
    myModel.removeHighlighter(rangeHighlighter);
  }

  public void changeAttributesInBatch(@NotNull Runnable change) {
    inBatchChange = true;
    try {
      change.run();
    }
    finally {
      inBatchChange = false;
      if (changed) {
        changed= false;
        ((MarkupModelImpl)myModel).fireAttributesChanged(rangeHighlighter);
      }
    }
  }
}
