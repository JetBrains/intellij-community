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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
public class RangeHighlighterImpl implements RangeHighlighterEx {
  private final MarkupModel myModel;
  private final int myLayer;
  private final HighlighterTargetArea myTargetArea;
  private TextAttributes myTextAttributes;
  private LineMarkerRenderer myLineMarkerRenderer;
  private Color myErrorStripeColor;
  private Color myLineSeparatorColor;
  private SeparatorPlacement mySeparatorPlacement;
  private boolean isAfterEndOfLine;
  private final RangeMarkerEx myRangeMarker;
  private GutterIconRenderer myGutterIconRenderer;
  private boolean myErrorStripeMarkIsThin;
  private Object myErrorStripeTooltip;
  private MarkupEditorFilter myFilter = MarkupEditorFilter.EMPTY;

  RangeHighlighterImpl(@NotNull MarkupModel model,
                       int start,
                       int end,
                       int layer,
                       @NotNull HighlighterTargetArea target,
                       TextAttributes textAttributes,
                       boolean persistent) {
    myRangeMarker = persistent
                    ? new PersistentLineMarker((DocumentEx)model.getDocument(), start)
                    : (RangeMarkerEx)model.getDocument().createRangeMarker(start, end);
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

  public int getAffectedAreaStartOffset() {
    int startOffset = getStartOffset();
    if (myTargetArea == HighlighterTargetArea.EXACT_RANGE) return startOffset;
    if (startOffset == getDocument().getTextLength()) return startOffset;
    return getDocument().getLineStartOffset(getDocument().getLineNumber(startOffset));
  }

  public int getAffectedAreaEndOffset() {
    int endOffset = getEndOffset();
    if (myTargetArea == HighlighterTargetArea.EXACT_RANGE) return endOffset;
    int textLength = getDocument().getTextLength();
    if (endOffset == textLength) return endOffset;
    return Math.min(textLength, getDocument().getLineEndOffset(getDocument().getLineNumber(endOffset)) + 1);
  }

  public LineMarkerRenderer getLineMarkerRenderer() {
    return myLineMarkerRenderer;
  }

  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    myLineMarkerRenderer = renderer;
    fireChanged();
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

  private void fireChanged() {
    if (myModel instanceof MarkupModelImpl) {
      ((MarkupModelImpl)myModel).fireSegmentHighlighterChanged(this);
    }
  }

  public int getStartOffset() {
    return myRangeMarker.getStartOffset();
  }

  public long getId() {
    return myRangeMarker.getId();
  }

  public int getEndOffset() {
    return myRangeMarker.getEndOffset();
  }

  public boolean isValid() {
    return myRangeMarker.isValid();
  }

  @NotNull
  public Document getDocument() {
    return myRangeMarker.getDocument();
  }

  public void setGreedyToLeft(boolean greedy) {
    myRangeMarker.setGreedyToLeft(greedy);
  }

  public void setGreedyToRight(boolean greedy) {
    myRangeMarker.setGreedyToRight(greedy);
  }

  public boolean isGreedyToRight() {
    return myRangeMarker.isGreedyToRight();
  }

  public boolean isGreedyToLeft() {
    return myRangeMarker.isGreedyToLeft();
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return myRangeMarker.getUserData(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myRangeMarker.putUserData(key, value);
  }

  public String toString() {
    return myRangeMarker.toString();
  }
}
