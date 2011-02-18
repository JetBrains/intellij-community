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

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
class RangeHighlighterImpl extends RangeMarkerImpl implements RangeHighlighterEx {
  RangeHighlighterImpl(@NotNull MarkupModel model,
                       int start,
                       int end,
                       int layer,
                       @NotNull HighlighterTargetArea target,
                       TextAttributes textAttributes) {
    super((DocumentEx)model.getDocument(), start, end,false);

    RangeHighlighterData data = new RangeHighlighterData(model, layer, target, textAttributes) {
      @NotNull
      @Override
      public RangeHighlighterEx getRangeHighlighter() {
        return RangeHighlighterImpl.this;
      }
    };
    data.registerMe(start, end);
  }

  protected RangeHighlighterData getData() {
    return ((RangeHighlighterTree.RHNode)myNode).data;
  }

  @Override
  protected void registerInDocument(int start, int end) {
    // we store highlighters in MarkupModel
  }

  @Override
  protected boolean unregisterInDocument() {
    if (myNode == null) return false;
    // we store highlighters in MarkupModel
    getData().unregisterMe();
    myNode = null;
    return true;
  }

  // delegates
  public TextAttributes getTextAttributes() {
    return getData().getTextAttributes();
  }

  public void setTextAttributes(TextAttributes textAttributes) {
    getData().setTextAttributes(textAttributes);
  }

  boolean changeAttributesInBatch(@NotNull Consumer<RangeHighlighterEx> change) {
    return getData().changeAttributesInBatch(change);
  }

  public int getLayer() {
    return getData().getLayer();
  }

  public HighlighterTargetArea getTargetArea() {
    return getData().getTargetArea();
  }

  public LineMarkerRenderer getLineMarkerRenderer() {
    return getData().getLineMarkerRenderer();
  }

  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    getData().setLineMarkerRenderer(renderer);
  }

  public CustomHighlighterRenderer getCustomRenderer() {
    return getData().getCustomRenderer();
  }

  public void setCustomRenderer(CustomHighlighterRenderer renderer) {
    getData().setCustomRenderer(renderer);
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return getData().getGutterIconRenderer();
  }

  public void setGutterIconRenderer(GutterIconRenderer renderer) {
    getData().setGutterIconRenderer(renderer);
  }

  public Color getErrorStripeMarkColor() {
    return getData().getErrorStripeMarkColor();
  }

  public void setErrorStripeMarkColor(Color color) {
    getData().setErrorStripeMarkColor(color);
  }

  public Object getErrorStripeTooltip() {
    return getData().getErrorStripeTooltip();
  }

  public void setErrorStripeTooltip(Object tooltipObject) {
    getData().setErrorStripeTooltip(tooltipObject);
  }

  public boolean isThinErrorStripeMark() {
    return getData().isThinErrorStripeMark();
  }

  public void setThinErrorStripeMark(boolean value) {
    getData().setThinErrorStripeMark(value);
  }

  public Color getLineSeparatorColor() {
    return getData().getLineSeparatorColor();
  }

  public void setLineSeparatorColor(Color color) {
    getData().setLineSeparatorColor(color);
  }

  public SeparatorPlacement getLineSeparatorPlacement() {
    return getData().getLineSeparatorPlacement();
  }

  public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
    getData().setLineSeparatorPlacement(placement);
  }

  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
    getData().setEditorFilter(filter);
  }

  @NotNull
  public MarkupEditorFilter getEditorFilter() {
    return getData().getEditorFilter();
  }

  public boolean isAfterEndOfLine() {
    return getData().isAfterEndOfLine();
  }

  public void setAfterEndOfLine(boolean afterEndOfLine) {
    getData().setAfterEndOfLine(afterEndOfLine);
  }

  public int getAffectedAreaStartOffset() {
    return getData().getAffectedAreaStartOffset();
  }

  public int getAffectedAreaEndOffset() {
    return getData().getAffectedAreaEndOffset();
  }

  @Override
  public String toString() {
    return "RangeHighlighter: ("+getStartOffset()+","+getEndOffset()+"); layer="+getLayer();
  }
}
