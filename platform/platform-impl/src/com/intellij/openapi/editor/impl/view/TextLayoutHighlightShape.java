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
package com.intellij.openapi.editor.impl.view;

import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

public class TextLayoutHighlightShape {
  private static final BasicStroke DEFAULT_STROKE = new BasicStroke();
  
  private final Shape myShape;
  private final float myHeight;

  public TextLayoutHighlightShape(@Nullable TextLayout textLayout, int start, int end, int ascent, int height) {
    myHeight = height;
    if (textLayout == null) {
      Path2D.Float shape = new Path2D.Float();
      shape.moveTo(0, 0);
      shape.lineTo(0, 0);
      shape.lineTo(0, height);
      shape.lineTo(0, height);
      shape.closePath();
      myShape = shape;
    }
    else {
      Rectangle2D clip = new Rectangle2D.Float(0, -ascent, textLayout.getAdvance(), height);
      Shape shape = textLayout.getLogicalHighlightShape(start, end, clip);
      myShape = AffineTransform.getTranslateInstance(0, ascent).createTransformedShape(shape);
    }
  }

  // drawing 1-pixel line at the inner edge of highlighting region
  @SuppressWarnings("SuspiciousNameCombination")
  public void draw(Graphics2D g, boolean rounded) {
    TFloatArrayList[] coords = getAdjustedChunksCoordinates();
    if (coords == null) return;

    g.setStroke(DEFAULT_STROKE);
    float maxY = myHeight - 1;
    for (int i = 0; i < coords[0].size(); i += 2) {
      float topStart = coords[0].get(i);
      float topEnd = coords[0].get(i + 1);
      float bottomStart = coords[1].get(i);
      float bottomEnd = coords[1].get(i + 1);

      Path2D.Float path = new Path2D.Float();
      addLine(path, topStart, 0, topEnd, 0, rounded);
      addLine(path, topEnd, 0, bottomEnd, maxY, rounded);
      addLine(path, bottomEnd, maxY, bottomStart, maxY, rounded);
      addLine(path, bottomStart, maxY, topStart, 0, rounded);

      g.draw(path);
    }
  }
  
  @Nullable
  private TFloatArrayList[] getAdjustedChunksCoordinates() {
    // extracting top and bottom points
    TFloatArrayList[] coords = new TFloatArrayList[]{new TFloatArrayList(), new TFloatArrayList()};
    PathIterator it = myShape.getPathIterator(null);
    float[] data = new float[6];
    while (!it.isDone()) {
      switch (it.currentSegment(data)) {
        case PathIterator.SEG_MOVETO:
        case PathIterator.SEG_LINETO:
          addBoundaryPoint(data[0], data[1], coords);
          break;
        case PathIterator.SEG_QUADTO:
          addBoundaryPoint(data[2], data[3], coords);
          break;
        case PathIterator.SEG_CUBICTO:
          addBoundaryPoint(data[4], data[5], coords);
      }
      it.next();
    }
    // expecting 2 points per chunk
    if (coords[0].isEmpty() || coords[0].size() != coords[1].size() || coords[0].size() % 2 != 0 || coords[1].size() % 2 != 0 ) return null;
    // sorting and merging adjacent chunks
    coords[0].sort();
    coords[1].sort();
    int pos = 2;
    while (pos < coords[0].size()) {
      if (coords[0].get(pos) == coords[0].get(pos - 1) && coords[1].get(pos) == coords[1].get(pos - 1)) {
        coords[0].remove(pos - 1, 2);
        coords[1].remove(pos - 1, 2);
      }
      else {
        pos += 2;
      }
    }
    // shrinking chunks for 1 pixel at right and bottom sides to move outline inside highlighted shape
    for (int i = 0; i < coords[0].size(); i += 2) {
      float topStart = coords[0].get(i);
      float topEnd = coords[0].get(i + 1);
      float bottomStart = coords[1].get(i);
      float bottomEnd = coords[1].get(i + 1);

      if (topStart == topEnd) {
        coords[0].set(i + 1, topEnd + 1); // painting 2-pixel line for empty chunks
      }
      else {
        coords[0].set(i + 1, topEnd - 1); // painting inside character region for non-empty chunks
      }
      if (bottomStart == bottomEnd) {
        coords[1].set(i + 1, bottomEnd + 1);
      }
      else {
        float slopeLeft = (bottomStart - topStart) / myHeight;
        float slopeRight = (bottomEnd - topEnd) / myHeight;
        coords[1].set(i, bottomStart - slopeLeft);
        coords[1].set(i + 1, bottomEnd - 1 - slopeRight);
      }
    }
    return coords;
  }

  private void addBoundaryPoint(float x, float y, TFloatArrayList[] coords) {
    if (y == 0) {
      coords[0].add(x);
    }
    else if (y == myHeight) {
      coords[1].add(x);
    }
  }

  public void fill(Graphics2D g) {
    g.fill(myShape);
  }

  public void setAsClip(Graphics2D g) {
    g.setClip(myShape);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  public static void drawCombined(Graphics2D g, TextLayoutHighlightShape leadingShape, TextLayoutHighlightShape trailingShape,
                                  int verticalShift, int maxWidth, boolean extendFirstLineToTheRight, boolean rounded) {
    TFloatArrayList[] leadingCoords = leadingShape.getAdjustedChunksCoordinates();
    TFloatArrayList[] trailingCoords = trailingShape.getAdjustedChunksCoordinates();
    if (leadingCoords == null || trailingCoords == null) return;
    
    maxWidth--; // painting inside clip area
    boolean containsInnerLines = verticalShift > leadingShape.myHeight;
    Path2D.Float path = new Path2D.Float();
    float bottomY = leadingShape.myHeight - 1;
    float topStart = 0;
    float topEnd = 0;
    float bottomStart;
    float bottomEnd = 0;
    float leftGap = leadingCoords[1].get(0) - (containsInnerLines ? 0 : trailingCoords[0].get(0));
    float adjustY = leftGap == 0 ? 2 : leftGap > 0 ? 1 : 0; // avoiding 1-pixel gap between aligned lines
    for (int i = 0; i < leadingCoords[0].size(); i += 2) {
      topStart = leadingCoords[0].get(i);
      topEnd = leadingCoords[0].get(i + 1);
      bottomStart = leadingCoords[1].get(i);
      bottomEnd = leadingCoords[1].get(i + 1);

      if (i > 0) {
        addLine(path, leadingCoords[1].get(i - 1), bottomY, bottomStart, bottomY, rounded);
      }
      addLine(path, bottomStart, bottomY + adjustY, topStart, 0, rounded);
      if ((i + 2) < leadingCoords[0].size() || !extendFirstLineToTheRight) {
        addLine(path, topStart, 0, topEnd, 0, rounded);
        addLine(path, topEnd, 0, bottomEnd, bottomY, rounded);
      }
      adjustY = 0;
    }
    if (extendFirstLineToTheRight) {
      topEnd = Math.max(topEnd, maxWidth);
      addLine(path, topStart, 0, topEnd, 0, rounded);
      addLine(path, topEnd, 0, topEnd, verticalShift - 1, rounded);
    }
    else if (containsInnerLines) {
      if (bottomEnd < maxWidth) {
        addLine(path, bottomEnd, bottomY + 1, rounded);
        addLine(path, bottomEnd, bottomY + 1, maxWidth, bottomY + 1, rounded);
        addLine(path, maxWidth, bottomY + 1, maxWidth, verticalShift - 1, rounded);
      }
      else {
        addLine(path, bottomEnd, verticalShift - 1, rounded);
      }
    }
    bottomY = trailingShape.myHeight - 1 + verticalShift;
    float lastX = (float)path.getCurrentPoint().getX();
    float targetX = trailingCoords[0].get(trailingCoords[0].size() - 1);
    if (lastX < targetX) {
      addLine(path, lastX, verticalShift, rounded);
      addLine(path, lastX, verticalShift, targetX, verticalShift, rounded);
    }
    else {
      addLine(path, lastX, verticalShift - 1, targetX, verticalShift - 1, rounded);
      adjustY = lastX == targetX ? -2 : -1; // for lastX == targetX we need to avoid a gap when rounding is used
    }
    for (int i = trailingCoords[0].size() - 2; i >= 0; i -= 2) {
      topStart = trailingCoords[0].get(i);
      topEnd = trailingCoords[0].get(i + 1);
      bottomStart = trailingCoords[1].get(i);
      bottomEnd = trailingCoords[1].get(i + 1);
      
      addLine(path, topEnd, verticalShift + adjustY, bottomEnd, bottomY, rounded);
      addLine(path, bottomEnd, bottomY, bottomStart, bottomY, rounded);
      addLine(path, bottomStart, bottomY, topStart, verticalShift, rounded);
      if (i > 0) {
        addLine(path, topStart, verticalShift, trailingCoords[0].get(i - 1), verticalShift, rounded);
      }
      
      adjustY = 0;
    }
    if (containsInnerLines) {
      if (topStart > 0) {
        addLine(path, topStart, verticalShift + 1, rounded);
        addLine(path, topStart, verticalShift + 1, 0, verticalShift + 1, rounded);
        addLine(path, 0, verticalShift + 1, 0, leadingShape.myHeight, rounded);
      }
      else {
        addLine(path, 0, leadingShape.myHeight, rounded);
      }
    }
    lastX = (float)path.getCurrentPoint().getX();
    targetX = leadingCoords[1].get(0);
    bottomY = leadingShape.myHeight - 1;
    if (lastX < targetX) {
      addLine(path, lastX, bottomY + 1, targetX, bottomY + 1, rounded);
    }
    else {
      addLine(path, lastX, bottomY, rounded);
      addLine(path, lastX, bottomY, targetX, bottomY, rounded);
    }
    
    g.setStroke(DEFAULT_STROKE);
    g.draw(path);
  }

  private static void addLine(Path2D path, float toX, float toY, boolean rounded) {
    addLine(path, (float)path.getCurrentPoint().getX(), (float)path.getCurrentPoint().getY(), toX, toY, false, rounded);
  }
  
  private static void addLine(Path2D path, float fromX, float fromY, float toX, float toY, boolean rounded) {
    addLine(path, fromX, fromY, toX, toY, rounded, rounded);
  }
  
  private static void addLine(Path2D path, float fromX, float fromY, float toX, float toY, boolean roundedStart, boolean roundedEnd) {
    if (roundedStart || roundedEnd) {
      float distX = Math.abs(toX - fromX);
      float distY = Math.abs(toY - fromY);
      if (distX >= 2 || distY >= 2) {
        float dx, dy;
        if (distX > distY) {
          // 'horizontal' line
          dx = Math.signum(toX - fromX);
          dy = (toY - fromY) / distX;
        }
        else {
          // 'vertical' line
          dy = Math.signum(toY - fromY);
          dx = (toX - fromX) / distY;
        }
        if (roundedStart) {
          fromX += dx;
          fromY += dy;
        }
        if (roundedEnd) {
          toX -= dx;
          toY -= dy;
        }
      }
      else {
        return;
      }
    }
    path.moveTo(fromX, fromY);
    path.lineTo(toX, toY);
  }
}
