/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public abstract class AbstractMockGlyphVector extends GlyphVector {
  @Override
  public Font getFont() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FontRenderContext getFontRenderContext() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void performDefaultLayout() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNumGlyphs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getGlyphCode(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, int[] codeReturn) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Rectangle2D getLogicalBounds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Rectangle2D getVisualBounds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Shape getOutline() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Shape getOutline(float x, float y) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Shape getGlyphOutline(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Point2D getGlyphPosition(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setGlyphPosition(int glyphIndex, Point2D newPos) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AffineTransform getGlyphTransform(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {
    throw new UnsupportedOperationException();
  }

  @Override
  public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, float[] positionReturn) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Shape getGlyphLogicalBounds(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Shape getGlyphVisualBounds(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlyphMetrics getGlyphMetrics(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("CovariantEquals")
  @Override
  public boolean equals(GlyphVector set) {
    throw new UnsupportedOperationException();
  }
}
