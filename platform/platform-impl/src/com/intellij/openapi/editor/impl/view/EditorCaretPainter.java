// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretVisualAttributes;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.caret.model.CaretCursor;
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle;
import com.intellij.openapi.editor.impl.caret.model.CaretRepaintMetrics;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.IslandsState;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.List;

@ApiStatus.Internal
public final class EditorCaretPainter {
  private static final int CARET_REPAINT_RECTANGLE_MARGIN = 1;
  private static final int CARET_CACHE_RECTANGLE_MARGIN = CARET_REPAINT_RECTANGLE_MARGIN + 1;
  private static final int CARET_DIRECTION_MARK_SIZE = 3;
  private static final Color CARET_LIGHT = Gray._255;
  private static final Color CARET_DARK = Gray._0;

  private final EditorView myView;
  private final EditorImpl myEditor;
  private final Document myDocument;

  public EditorCaretPainter(@NotNull EditorView myView) {
    this.myView = myView;
    this.myEditor = myView.getEditor();
    this.myDocument = myView.getDocument();
  }

  void repaintCarets(@NotNull CaretCursor caretCursor) {
    var locations = caretCursor.locations();
    var metrics = caretCursor.repaintMetrics();
    var editor = myView.getEditor();
    for (var rectangle : caretRectanglesForLocations(locations, metrics, CARET_REPAINT_RECTANGLE_MARGIN)) {
      editor.getContentComponent().repaintCaret(
        rectangle.x, rectangle.y, rectangle.width, rectangle.height
      );
    }
  }

  void paintCaret(Graphics2D graphics, int yShift) {
    if (myEditor.isPurePaintingMode() ||
        myEditor.isStickyLinePainting() /* suppress caret painting on sticky lines panel */) {
      return;
    }
    CaretCursor caretCursor = myEditor.getCaretCursor(true);
    if (caretCursor != null) {
      new Session(graphics, yShift).paintCaret(caretCursor);
    }
  }

  @NotNull List<Rectangle> caretRectanglesForLocations(
    @NotNull List<CaretRectangle> locations,
    @NotNull CaretRepaintMetrics repaintMetrics
  ) {
    List<Rectangle> rectangles = caretRectanglesForLocations(
      locations,
      repaintMetrics,
      CARET_CACHE_RECTANGLE_MARGIN
    );
    return ContainerUtil.map(rectangles, EditorCaretPainter::coerceAtLeastEmpty);
  }

  private final class Session {
    private final Graphics2D myGraphics;
    private final Insets myInsets;
    private final int myYShift;
    private final int myAscent;

    Session(Graphics2D graphics, int yShift) {
      myInsets = myView.getInsets();
      myGraphics = graphics;
      myYShift = yShift;
      myAscent = myView.getAscent();
    }

    void paintCaret(CaretCursor caretCursor) {
      Graphics2D g = IdeBackgroundUtil.getOriginalGraphics(myGraphics);
      EditorSettings settings = myEditor.getSettings();
      Color caretColor = myEditor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
      if (caretColor == null) {
        caretColor = new JBColor(CARET_DARK, CARET_LIGHT);
      }
      int minX = myInsets.left;
      int caretHeight = caretCursor.repaintMetrics().caretHeight;
      int topOverhang = caretCursor.repaintMetrics().caretTopOverhang;
      float opacity = caretCursor.blinkOpacity();
      for (CaretRectangle location : caretCursor.locations()) {
        float x = (float)location.getX();
        int y = (int)location.getY() - topOverhang + myYShift;
        Caret caret = location.getCaret();
        CaretVisualAttributes attr = caret == null ? CaretVisualAttributes.getDefault() : caret.getVisualAttributes();
        Color caretWithOpacity = withOpacity(attr.getColor() != null ? attr.getColor() : caretColor, opacity);
        g.setColor(caretWithOpacity);
        boolean isRtl = location.isRtl();
        float width = location.getWidth();
        float startX = Math.max(minX, isRtl ? x - width : x);
        CaretVisualAttributes.Shape shape = attr.getShape();
        switch (shape) {
          case DEFAULT -> {
            if (myEditor.isInsertMode() != settings.isBlockCursor()) {
              float lineWidth = JBUIScale.scale(attr.getWidth(settings.getLineCursorWidth())) * myEditor.getScale();
              // fully cover extra character's pixel which can appear due to antialiasing
              // see IDEA-148843 for more details
              if (x > minX && lineWidth > 1) {
                x -= 1 / JBUIScale.sysScale(g);
              }
              paintCaretBar(g, caret, x, y, lineWidth, caretHeight, isRtl);
            } else {
              paintCaretBlock(g, startX, y, width, caretHeight);
              paintCaretText(g, caret, caretColor, opacity, startX, y, topOverhang, isRtl);
            }
          }
          case BLOCK -> {
            paintCaretBlock(g, startX, y, width, caretHeight);
            paintCaretText(g, caret, caretColor, opacity, startX, y, topOverhang, isRtl);
          }
          case BAR -> {
            // Don't draw if thickness is zero. This allows a plugin to "hide" carets, e.g. to visually emulate a block selection as a
            // selection rather than as multiple carets with discrete selections
            if (attr.getThickness() > 0) {
              int barWidth = Math.max((int)(width * attr.getThickness()), JBUIScale.scale(settings.getLineCursorWidth()));
              if (!isRtl && x > minX && barWidth > 1 && barWidth < (width / 2)) x -= 1 / JBUIScale.sysScale(g);
              paintCaretBar(g, caret, isRtl ? x - barWidth : x, y, barWidth, caretHeight, isRtl);
              Shape savedClip = g.getClip();
              //noinspection GraphicsSetClipInspection
              g.setClip(new Rectangle2D.Float(isRtl ? x - barWidth : x, y, barWidth, caretHeight));
              paintCaretText(g, caret, caretColor, opacity, startX, y, topOverhang, isRtl);
              //noinspection GraphicsSetClipInspection
              g.setClip(savedClip);
            }
          }
          case UNDERSCORE -> {
            if (attr.getThickness() > 0) {
              int underscoreHeight = Math.max((int)(caretHeight * attr.getThickness()), 1);
              paintCaretUnderscore(g, startX, y + caretHeight - underscoreHeight, width, underscoreHeight);
              Shape oldClip = g.getClip();
              //noinspection GraphicsSetClipInspection
              g.setClip(new Rectangle2D.Float(startX, y + caretHeight - underscoreHeight, width, underscoreHeight));
              paintCaretText(g, caret, caretColor, opacity, startX, y, topOverhang, isRtl);
              //noinspection GraphicsSetClipInspection
              g.setClip(oldClip);
            }
          }
          case BOX -> {
            paintCaretBox(g, startX, y, width, caretHeight);
          }
        }
      }
    }

    private void paintCaretText(
      @NotNull Graphics2D g,
      @Nullable Caret caret,
      @NotNull Color caretColor,
      float opacity,
      float x,
      float y,
      int topOverhang,
      boolean isRtl
    ) {
      if (caret != null) {
        var config = GraphicsUtil.setupAAPainting(g);
        try {
          Color withedOpacity = withOpacity(ColorUtil.isDark(caretColor) ? CARET_LIGHT : CARET_DARK, opacity);
          int targetVisualColumn = caret.getVisualPosition().column - (isRtl ? 1 : 0);
          for (var fragment : VisualLineFragmentsIterator.create(myView, caret.getVisualLineStart(), false)) {
            if (fragment.getCurrentInlay() != null) {
              continue;
            }
            int startVisualColumn = fragment.getStartVisualColumn();
            int endVisualColumn = fragment.getEndVisualColumn();
            if (startVisualColumn <= targetVisualColumn && targetVisualColumn < endVisualColumn) {
              g.setColor(withedOpacity);
              fragment.draw(
                x,
                y + topOverhang + myAscent,
                fragment.visualColumnToOffset(targetVisualColumn - startVisualColumn),
                fragment.visualColumnToOffset(targetVisualColumn + 1 - startVisualColumn)
              ).accept(g);
              break;
            }
          }
          ComplexTextFragment.flushDrawingCache(g);
        } finally {
          config.restore();
        }
      }
    }
  }

  private void paintCaretBar(@NotNull Graphics2D g, @Nullable Caret caret, float x, float y, float w, float h, boolean isRtl) {
    var old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    boolean shouldDrawRtl = myDocument.getTextLength() > 0 &&
                            caret != null &&
                            !myView.getTextLayoutCache().getLineLayout(caret.getLogicalPosition().line).isLtr();
    float radius = IslandsState.Companion.isEnabled() ? Math.min(w / 2, CARET_DIRECTION_MARK_SIZE) : 0.0f;
    GeneralPath caretShape = new GeneralPath();
    caretShape.moveTo(x, y + radius);
    if (shouldDrawRtl && isRtl) {
      caretShape.moveTo(x, y + CARET_DIRECTION_MARK_SIZE);
      caretShape.lineTo(x - CARET_DIRECTION_MARK_SIZE, y);
      caretShape.lineTo(x + radius, y);
    } else {
      caretShape.quadTo(x, y, x + radius, y);
    }
    if (shouldDrawRtl && !isRtl) {
      caretShape.lineTo(x + w + CARET_DIRECTION_MARK_SIZE, y);
      caretShape.lineTo(x + w, y + CARET_DIRECTION_MARK_SIZE);
    } else {
      caretShape.lineTo(x + w - radius, y);
      caretShape.quadTo(x + w, y, x + w, y + radius);
    }
    caretShape.lineTo(x + w, y + h - radius);
    caretShape.quadTo(x + w, y + h, x + w - radius, y + h);
    caretShape.lineTo(x + radius, y + h);
    caretShape.quadTo(x, y + h, x, y + h - radius);
    caretShape.closePath();
    g.fill(caretShape);
    if (old != null) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }
  }

  private static void paintCaretBlock(@NotNull Graphics2D g, float x, float y, float w, float h) {
    g.fill(new Rectangle2D.Float(x, y, w, h));
  }

  private static void paintCaretUnderscore(@NotNull Graphics2D g, float x, float y, float w, float h) {
    g.fill(new Rectangle2D.Float(x, y, w, h));
  }

  private static void paintCaretBox(@NotNull Graphics2D g, float x, float y, float w, float h) {
    if (w > 2) {
      float outlineWidth = (float) PaintUtil.alignToInt(1, g);
      Area area = new Area(new Rectangle2D.Float(x, y, w, h));
      var rectangleX = new Rectangle2D.Float(
        x + outlineWidth,
        y + outlineWidth,
        w - (2 * outlineWidth),
        h - (2 * outlineWidth)
      );
      area.subtract(new Area(rectangleX));
      g.fill(area);
    } else {
      paintCaretBlock(g, x, y, w, h);
    }
  }

  private static @NotNull List<Rectangle> caretRectanglesForLocations(
    @NotNull List<CaretRectangle> locations,
    @NotNull CaretRepaintMetrics metrics,
    int grow
  ) {
    return ContainerUtil.map(
      locations,
      location -> caretRectangleForLocationAndGrow(location, metrics, grow)
    );
  }

  /**
   * {@link #exactCaretRectangleForLocation} grown by {@code grow} pixels on every side.
   * <p>
   * Due to how fractional scaling works (mostly on Windows), the exact rectangle in user space can map to physical
   * pixels with a plus-minus-one-pixel error, so painting/repainting exactly the tight bounds leaves thin uncovered
   * strips ("tango" and the trail of dots). The fix is to consistently work with slightly larger rectangles: cache a
   * bit more than we repaint, and repaint a bit more than the exact bounds.
   *
   * @param grow number of pixels to expand the rectangle by on each side
   */
  private static @NotNull Rectangle caretRectangleForLocationAndGrow(
    @NotNull CaretRectangle location,
    @NotNull CaretRepaintMetrics metrics,
    int grow
  ) {
    var rectangle = exactCaretRectangleForLocation(location, metrics);
    rectangle.grow(grow, grow);
    return rectangle;
  }

  /**
   * The tightest integer-pixel rectangle (in user space) that covers everything the caret draws at a given location:
   * the caret bar itself plus its direction mark, extended upward by {@code topOverhang} and down to {@code caretHeight}.
   * <p>
   * The horizontal bounds are computed with {@code floor}/{@code ceil} around the fractional caret x, so the returned
   * rectangle always encloses the ideal geometry without clipping it. This is the exact bounds only; it does not
   * account for the plus-minus-one-pixel error introduced by fractional scaling &mdash; callers that paint or request
   * repaints under such scaling should use {@link #caretRectangleForLocationAndGrow} to overextend it.
   */
  private static @NotNull Rectangle exactCaretRectangleForLocation(
    @NotNull CaretRectangle location,
    @NotNull CaretRepaintMetrics metrics
  ) {
    float x = (float)location.getX();
    int y = (int)location.getY() - metrics.caretTopOverhang;
    float width = location.getWidth() + CARET_DIRECTION_MARK_SIZE;
    int xStart = (int)Math.floor(x - width);
    int xEnd = (int)Math.ceil(x + width);
    return new Rectangle(xStart, y, xEnd - xStart, metrics.caretHeight);
  }

  private static @NotNull Rectangle coerceAtLeastEmpty(@NotNull Rectangle r) {
    int newX = Math.max(r.x, 0);
    int newY = Math.max(r.y, 0);
    int newWidth = Math.max((r.width + (r.x - newX)), 0);
    int newHeight = Math.max((r.height + (r.y - newY)), 0);
    return new Rectangle(newX, newY, newWidth, newHeight);
  }

  private static @NotNull Color withOpacity(Color color, float opacity) {
    return ColorUtil.toAlpha(color, (int)(color.getAlpha() * opacity));
  }
}
