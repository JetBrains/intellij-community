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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ChangeType {

  private static final int LAYER = HighlighterLayer.SELECTION - 1;
  private static final ChangeType INSERT = new ChangeType(TextDiffType.INSERT, false);
  private static final ChangeType DELETED = new ChangeType(TextDiffType.DELETED, false);
  private static final ChangeType CHANGE = new ChangeType(TextDiffType.CHANGED, false);
  static final ChangeType CONFLICT = new ChangeType(TextDiffType.CONFLICT, false);

  private final TextDiffType myDiffType;
  private final boolean myApplied;

  private ChangeType(TextDiffType diffType, boolean applied) {
    myApplied = applied;
    if (applied) {
      myDiffType = TextDiffType.deriveApplied(diffType);
    }
    else {
      myDiffType = diffType;
    }
  }

  public boolean isApplied() {
    return myApplied;
  }

  @NotNull
  public static ChangeType deriveApplied(ChangeType type) {
    return new ChangeType(type.myDiffType, true);
  }

  @Nullable
  public RangeHighlighter addMarker(ChangeSide changeSide, ChangeHighlighterHolder markup) {
    CharSequence text = changeSide.getText();
    if (text != null && text.length() > 0) {
      return addBlock(text, changeSide, markup, myDiffType);
    }
    else {
      return addLine(markup, changeSide.getStartLine(), myDiffType, SeparatorPlacement.TOP);
    }
  }

  @NotNull
  public TextDiffType getTypeKey() {
    return myDiffType;
  }

  @NotNull
  public TextDiffType getTextDiffType() {
    return getTypeKey();
  }

  @Nullable
  private RangeHighlighter addBlock(CharSequence text, ChangeSide changeSide, final ChangeHighlighterHolder markup, TextDiffType diffType) {
    EditorColorsScheme colorScheme = markup.getEditor().getColorsScheme();
    Color separatorColor = getSeparatorColor(diffType.getLegendColor(colorScheme));

    int length = text.length();
    int start = changeSide.getStart();
    int end = start + length;
    RangeHighlighter highlighter = markup.addRangeHighlighter(start, end, LAYER, diffType, HighlighterTargetArea.EXACT_RANGE, myApplied);

    LineSeparatorRenderer lineSeparatorRenderer = new LineSeparatorRenderer() {
      @Override
      public void drawLine(Graphics g, int x1, int x2, int y) {
        Graphics2D g2 = (Graphics2D)g;
        Color color = myDiffType.getPolygonColor(markup.getEditor());
        if (color != null) {
          if (myApplied) {
            Rectangle bounds = g.getClipBounds();
            x1 = Math.max(x1, bounds.x); // do not paint line behind clip bounds - it's very slow for dotted line
            if (x1 >= x2) return;

            UIUtil.drawBoldDottedLine(g2, x1, x2, y, null, color, false);
          }
          else {
            UIUtil.drawLine(g2, x1, y, x2, y, null, DiffUtil.getFramingColor(color));
          }
        }
      }
    };

    if (highlighter != null) {
      highlighter.setLineSeparatorPlacement(SeparatorPlacement.TOP);
      highlighter.setLineSeparatorColor(separatorColor);
      highlighter.setLineSeparatorRenderer(lineSeparatorRenderer);
    }

    if (text.charAt(length - 1) == '\n') {
      end--;
    }

    highlighter = markup.addRangeHighlighter(start, end, LAYER, TextDiffType.NONE, HighlighterTargetArea.EXACT_RANGE, myApplied);
    if (highlighter != null) {
      highlighter.setLineSeparatorPlacement(SeparatorPlacement.BOTTOM);
      highlighter.setLineSeparatorColor(separatorColor);
      highlighter.setLineSeparatorRenderer(lineSeparatorRenderer);
    }
    return highlighter;
  }

  @Nullable
  private RangeHighlighter addLine(final ChangeHighlighterHolder markup, int line, final TextDiffType type, SeparatorPlacement placement) {
    RangeHighlighter highlighter = markup.addLineHighlighter(line, LAYER, type, myApplied);
    if (highlighter == null) {
      return null;
    }
    highlighter.setLineSeparatorPlacement(placement);
    highlighter.setLineSeparatorRenderer(new LineSeparatorRenderer() {
      @Override
      public void drawLine(Graphics g, int x1, int x2, int y) {
        Graphics2D g2 = (Graphics2D)g;
        Color color = myDiffType.getPolygonColor(markup.getEditor());
        if (color != null) {
          if (type.isApplied()) {
            Rectangle bounds = g.getClipBounds();
            x1 = Math.max(x1, bounds.x); // do not paint line behind clip bounds - it's very slow for dotted line
            if (x1 >= x2) return;

            UIUtil.drawBoldDottedLine(g2, x1, x2, y, null, color, false);
          }
          else {
            DiffUtil.drawDoubleShadowedLine(g2, x1, x2, y, color);
          }
        }
      }
    });
    return highlighter;
  }

  @NotNull
  static ChangeType fromDiffFragment(DiffFragment fragment) {
    if (fragment.getText1() == null) return INSERT;
    if (fragment.getText2() == null) return DELETED;
    return CHANGE;
  }

  @NotNull
  static ChangeType fromRanges(@NotNull TextRange left, @NotNull TextRange right) {
    if (left.getLength() == 0) return INSERT;
    if (right.getLength() == 0) return DELETED;
    return CHANGE;
  }

  public String toString() {
    return myDiffType.getDisplayName();
  }

  @NotNull
  public Color getSeparatorColor(@Nullable Color highlightColor) {
    if (myApplied) {
      return highlightColor == null ? Color.DARK_GRAY : highlightColor.darker();
    }
    return Color.GRAY;
  }
}
