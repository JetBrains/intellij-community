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
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;

public abstract class LineStatusMarkerRenderer implements ActiveGutterRenderer {
  @NotNull protected final Range myRange;

  public LineStatusMarkerRenderer(@NotNull Range range) {
    myRange = range;
  }

  @NotNull
  public static TextAttributes getTextAttributes(@NotNull final Range range) {
    return new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return LineStatusMarkerRenderer.getErrorStripeColor(range);
      }
    };
  }

  @NotNull
  public static String getTooltipText(@NotNull Range range) {
    if (range.getLine1() == range.getLine2()) {
      if (range.getVcsLine1() + 1 == range.getVcsLine2()) {
        return VcsBundle.message("tooltip.text.line.before.deleted", range.getLine1() + 1);
      }
      else {
        return VcsBundle.message("tooltip.text.lines.before.deleted", range.getLine1() + 1, range.getVcsLine2() - range.getVcsLine1());
      }
    }
    else if (range.getLine1() + 1 == range.getLine2()) {
      return VcsBundle.message("tooltip.text.line.changed", range.getLine1() + 1);
    }
    else {
      return VcsBundle.message("tooltip.text.lines.changed", range.getLine1() + 1, range.getLine2());
    }
  }

  //
  // Gutter painting
  //

  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    final EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Color gutterColor = getGutterColor(myRange);
    Color borderColor = getGutterBorderColor();

    final int x = r.x + r.width - 3;
    final int endX = gutter.getWhitespaceSeparatorOffset();

    final int y = lineToY(editor, myRange.getLine1());
    final int endY = lineToY(editor, myRange.getLine2());

    if (myRange.getInnerRanges() == null) { // Mode.DEFAULT
      if (y != endY) {
        paintRect(g, gutterColor, borderColor, x, y, endX, endY);
      }
      else {
        paintTriangle(g, gutterColor, borderColor, x, endX, y);
      }
    }
    else { // Mode.SMART
      if (y == endY) {
        paintTriangle(g, gutterColor, borderColor, x, endX, y);
      }
      else {
        List<Range.InnerRange> innerRanges = myRange.getInnerRanges();
        for (Range.InnerRange innerRange : innerRanges) {
          if (innerRange.getType() == Range.DELETED) continue;

          int start = lineToY(editor, innerRange.getLine1());
          int end = lineToY(editor, innerRange.getLine2());

          paintRect(g, getGutterColor(innerRange), null, x, start, endX, end);
        }

        for (int i = 0; i < innerRanges.size(); i++) {
          Range.InnerRange innerRange = innerRanges.get(i);
          if (innerRange.getType() != Range.DELETED) continue;

          int start;
          int end;

          if (i == 0) {
            start = lineToY(editor, innerRange.getLine1());
            end = lineToY(editor, innerRange.getLine2()) + 5;
          }
          else if (i == innerRanges.size() - 1) {
            start = lineToY(editor, innerRange.getLine1()) - 5;
            end = lineToY(editor, innerRange.getLine2());
          }
          else {
            start = lineToY(editor, innerRange.getLine1()) - 3;
            end = lineToY(editor, innerRange.getLine2()) + 3;
          }

          paintRect(g, getGutterColor(innerRange), null, x, start, endX, end);
        }

        paintRect(g, null, borderColor, x, y, endX, endY);
      }
    }
  }

  private static void paintRect(@NotNull Graphics g, @Nullable Color color, @Nullable Color borderColor, int x1, int y1, int x2, int y2) {
    if (color != null) {
      g.setColor(color);
      g.fillRect(x1, y1, x2 - x1, y2 - y1);
    }
    if (borderColor != null) {
      g.setColor(borderColor);
      UIUtil.drawLine(g, x1, y1, x2 - 1, y1);
      UIUtil.drawLine(g, x1, y1, x1, y2 - 1);
      UIUtil.drawLine(g, x1, y2 - 1, x2 - 1, y2 - 1);
    }
  }

  private static void paintTriangle(@NotNull Graphics g, @Nullable Color color, @Nullable Color borderColor, int x1, int x2, int y) {
    int size = 4;

    final int[] xPoints = new int[]{x1, x1, x2};
    final int[] yPoints = new int[]{y - size, y + size, y};

    if (color != null) {
      g.setColor(color);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }
    if (borderColor != null) {
      g.setColor(borderColor);
      g.drawPolygon(xPoints, yPoints, xPoints.length);
    }
  }

  @Nullable
  private static Color getGutterColor(@NotNull Range.InnerRange range) {
    // TODO: we should move color settings from Colors-General to Colors-Diff
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return globalScheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return globalScheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      case Range.EQUAL:
        return globalScheme.getColor(EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getErrorStripeColor(@NotNull Range range) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getAttributes(DiffColors.DIFF_INSERTED).getErrorStripeColor();
      case Range.DELETED:
        return globalScheme.getAttributes(DiffColors.DIFF_DELETED).getErrorStripeColor();
      case Range.MODIFIED:
        return globalScheme.getAttributes(DiffColors.DIFF_MODIFIED).getErrorStripeColor();
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getGutterColor(@NotNull Range range) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return globalScheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return globalScheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getGutterBorderColor() {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    return globalScheme.getColor(EditorColors.BORDER_LINES_COLOR);
  }

  //
  // Popup
  //

  @Override
  public boolean canDoAction(MouseEvent e) {
    final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
    return e.getX() > gutter.getLineMarkerFreePaintersAreaOffset();
  }
}
