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

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
public class PersistentRangeHighlighterImpl extends PersistentLineMarker implements RangeHighlighterEx {
  private final RangeHighlighterData data;
  PersistentRangeHighlighterImpl(@NotNull MarkupModel model,
                                 int start,
                                 int layer,
                                 @NotNull HighlighterTargetArea target,
                                 TextAttributes textAttributes
  ) {
    super((DocumentEx)model.getDocument(), start);
    data = new RangeHighlighterData(model, layer, target, textAttributes, this);
  }

  @Override
  protected void registerInDocument() {
    // we store highlighters in MarkupModel
    data.registerMe();
  }

  @Override
  protected boolean unregisterInDocument() {
    // we store highlighters in MarkupModel
    data.unregisterMe();
    myNode = null;
    return true;
  }

  // delegates

  public TextAttributes getTextAttributes() {
    return data.getTextAttributes();
  }

  public void setTextAttributes(TextAttributes textAttributes) {
    data.setTextAttributes(textAttributes);
  }

  public void changeAttributesInBatch(@NotNull Runnable change) {
    data.changeAttributesInBatch(change);
  }

  public int getLayer() {
    return data.getLayer();
  }

  public HighlighterTargetArea getTargetArea() {
    return data.getTargetArea();
  }

  public LineMarkerRenderer getLineMarkerRenderer() {
    return data.getLineMarkerRenderer();
  }

  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    data.setLineMarkerRenderer(renderer);
  }

  public CustomHighlighterRenderer getCustomRenderer() {
    return data.getCustomRenderer();
  }

  public void setCustomRenderer(CustomHighlighterRenderer renderer) {
    data.setCustomRenderer(renderer);
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return data.getGutterIconRenderer();
  }

  public void setGutterIconRenderer(GutterIconRenderer renderer) {
    data.setGutterIconRenderer(renderer);
  }

  public Color getErrorStripeMarkColor() {
    return data.getErrorStripeMarkColor();
  }

  public void setErrorStripeMarkColor(Color color) {
    data.setErrorStripeMarkColor(color);
  }

  public Object getErrorStripeTooltip() {
    return data.getErrorStripeTooltip();
  }

  public void setErrorStripeTooltip(Object tooltipObject) {
    data.setErrorStripeTooltip(tooltipObject);
  }

  public boolean isThinErrorStripeMark() {
    return data.isThinErrorStripeMark();
  }

  public void setThinErrorStripeMark(boolean value) {
    data.setThinErrorStripeMark(value);
  }

  public Color getLineSeparatorColor() {
    return data.getLineSeparatorColor();
  }

  public void setLineSeparatorColor(Color color) {
    data.setLineSeparatorColor(color);
  }

  public SeparatorPlacement getLineSeparatorPlacement() {
    return data.getLineSeparatorPlacement();
  }

  public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
    data.setLineSeparatorPlacement(placement);
  }

  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
    data.setEditorFilter(filter);
  }

  @NotNull
  public MarkupEditorFilter getEditorFilter() {
    return data.getEditorFilter();
  }

  public boolean isAfterEndOfLine() {
    return data.isAfterEndOfLine();
  }

  public void setAfterEndOfLine(boolean afterEndOfLine) {
    data.setAfterEndOfLine(afterEndOfLine);
  }

  public int getAffectedAreaStartOffset() {
    return data.getAffectedAreaStartOffset();
  }

  public int getAffectedAreaEndOffset() {
    return data.getAffectedAreaEndOffset();
  }
}
