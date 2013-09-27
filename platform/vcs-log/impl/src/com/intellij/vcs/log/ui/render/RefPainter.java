package com.intellij.vcs.log.ui.render;

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;

/**
 * @author erokhins
 */
public class RefPainter {
  private static final int RECTANGLE_X_PADDING = 4;
  private static final int RECTANGLE_Y_PADDING = 3;
  private static final int REF_PADDING = 13;

  private static final int FLAG_WIDTH = 8;
  private static final int FLAG_PADDING = 8;
  private static final int FLAG_TOP_INDENT = 2;

  private static final int ROUND_RADIUS = 5;

  private static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 12);
  private static final Color DEFAULT_FONT_COLOR = Color.black;

  @NotNull private final VcsLogColorManager myColorManager;
  private final boolean myDrawMultiRepoIndicator;

  public RefPainter(@NotNull VcsLogColorManager manager, boolean drawMultiRepoIndicator) {
    myColorManager = manager;
    myDrawMultiRepoIndicator = drawMultiRepoIndicator;
  }

  private double paddingStr(@NotNull String str, @NotNull FontRenderContext renderContext) {
    return DEFAULT_FONT.getStringBounds(str, renderContext).getWidth() + REF_PADDING + flagWidth();
  }

  private int flagWidth() {
    return myColorManager.isMultipleRoots() && myDrawMultiRepoIndicator ? FLAG_WIDTH + FLAG_PADDING : 0;
  }

  private static void drawText(@NotNull Graphics2D g2, @NotNull String str, int padding) {
    FontMetrics metrics = g2.getFontMetrics();
    g2.setColor(DEFAULT_FONT_COLOR);
    int x = padding + REF_PADDING / 2;
    int y = HEIGHT_CELL / 2 + (metrics.getAscent() - metrics.getDescent()) / 2;
    g2.drawString(str, x, y);
  }

  private int draw(@NotNull Graphics2D g2, @NotNull VcsRef ref, int padding) {
    FontMetrics metrics = g2.getFontMetrics();
    int x = padding + REF_PADDING / 2 - RECTANGLE_X_PADDING;
    int y = RECTANGLE_Y_PADDING;
    int width = metrics.stringWidth(ref.getName()) + 2 * RECTANGLE_X_PADDING + flagWidth();
    int height = HEIGHT_CELL - 2 * RECTANGLE_Y_PADDING;
    RoundRectangle2D rectangle2D = new RoundRectangle2D.Double(x, y, width, height, ROUND_RADIUS, ROUND_RADIUS);

    g2.setColor(ref.getType().getBackgroundColor());
    g2.fill(rectangle2D);

    g2.setColor(myColorManager.getReferenceBorderColor());
    g2.draw(rectangle2D);

    if (myColorManager.isMultipleRoots() && myDrawMultiRepoIndicator) {
      drawRootIndicator(g2, ref, padding, y, height);
    }

    g2.setColor(Color.black);
    drawText(g2, ref.getName(), padding + flagWidth());
    return x;
  }

  private void drawRootIndicator(@NotNull Graphics2D g2, @NotNull VcsRef ref, int padding, int y, int height) {
    g2.setColor(myColorManager.getRootColor(ref.getRoot()));
    int x0 = padding + FLAG_PADDING;
    int xMid = x0 + FLAG_WIDTH / 2;
    int xRight = x0 + FLAG_WIDTH;

    int y0 = y - FLAG_TOP_INDENT;
    int yMid = y0 + 2 * height / 3 - 2;
    int yBottom = y0 + height - 4;

    // something like a knight flag
    Polygon polygon = new Polygon(new int[]{ x0, xRight, xRight,    xMid,   x0 },
                                  new int[]{ y0,     y0,   yMid, yBottom, yMid }, 5);
    g2.fillPolygon(polygon);
    g2.setColor(myColorManager.getReferenceBorderColor());
    g2.drawPolygon(polygon);
  }

  public int padding(@NotNull List<VcsRef> refs, @NotNull FontRenderContext renderContext) {
    float p = 0;
    for (VcsRef ref : refs) {
      p += paddingStr(ref.getName(), renderContext);
    }
    return Math.round(p);
  }

  public Map<Integer, VcsRef> draw(@NotNull Graphics2D g2, @NotNull List<VcsRef> refs, int startPadding) {
    float currentPadding = startPadding;
    g2.setFont(DEFAULT_FONT);
    g2.setStroke(new BasicStroke(1.5f));
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    FontRenderContext renderContext = g2.getFontRenderContext();
    Map<Integer, VcsRef> positions = new HashMap<Integer, VcsRef>();
    for (VcsRef ref : refs) {
      int x = draw(g2, ref, (int)currentPadding);
      positions.put(x, ref);
      currentPadding += paddingStr(ref.getName(), renderContext);
    }
    return positions;
  }

}
