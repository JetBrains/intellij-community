package com.intellij.vcs.log.ui.render;

import com.intellij.ui.JBColor;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class RefPainter {
  private static final int RECTANGLE_X_PADDING = 4;
  private static final int RECTANGLE_Y_PADDING = 3;
  private static final int REF_PADDING = 13;
  private static final int LABEL_PADDING = 3;

  private static final int FLAG_WIDTH = 8;
  private static final int FLAG_PADDING = 8;
  private static final int FLAG_TOP_INDENT = 2;

  private static final int ROUND_RADIUS = 5;

  public static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 12); // wtf, shouldn't we use ui font?
  private static final Color DEFAULT_FONT_COLOR = JBColor.BLACK;
  public static final int REF_HEIGHT = 22;

  @NotNull private final VcsLogColorManager myColorManager;
  private final boolean myDrawMultiRepoIndicator;

  public RefPainter(@NotNull VcsLogColorManager manager, boolean drawMultiRepoIndicator) {
    myColorManager = manager;
    myDrawMultiRepoIndicator = drawMultiRepoIndicator;
  }

  protected int getRowHeight() {
    return REF_HEIGHT;
  }

  public int getLabelWidth(@NotNull String str, FontMetrics metrics) {
    return getHorizontalDimensions(str, 0, metrics).width + LABEL_PADDING;
  }

  public int getLabelsWidth(@NotNull Collection<String> refs, FontMetrics fontMetrics) {
    if (refs.isEmpty()) return 0;

    int p = 2 * LABEL_PADDING; // additional padding after all references looks better
    for (String ref : refs) {
      HorizontalDimensions horizontalDimensions = getHorizontalDimensions(ref, p, fontMetrics);
      p += horizontalDimensions.width + LABEL_PADDING;
    }

    return p;
  }

  public Map<Integer, VcsRef> draw(@NotNull Graphics2D g2, @NotNull Collection<VcsRef> refs, int startPadding, int maxWidth) {
    float currentPadding = startPadding;
    setupGraphics(g2);
    FontRenderContext renderContext = g2.getFontRenderContext();
    Map<Integer, VcsRef> positions = new HashMap<Integer, VcsRef>();
    for (VcsRef ref : refs) {
      int x =
        drawLabel(g2, ref.getName(), (int)currentPadding, ref.getType().getBackgroundColor(), myColorManager.getRootColor(ref.getRoot())).x;
      positions.put(x, ref);
      currentPadding += paddingStr(ref.getName(), renderContext);
      if (maxWidth > 0 && x >= maxWidth) {
        break;
      }
    }
    return positions;
  }

  public void drawLabels(@NotNull Graphics2D g2, @NotNull Map<String, Color> labels, int startPadding) {
    int padding = startPadding;
    for (Map.Entry<String, Color> entry : labels.entrySet()) {
      Rectangle rectangle = drawLabel(g2, entry.getKey(), padding, entry.getValue(), null);
      padding += rectangle.width + LABEL_PADDING;
    }
  }

  public Rectangle drawLabel(@NotNull Graphics2D g2,
                             @NotNull String label,
                             int paddingX,
                             @NotNull Color bgColor,
                             @Nullable Color rootIndicatorColor) {
    setupGraphics(g2);
    FontMetrics metrics = g2.getFontMetrics();
    HorizontalDimensions horizontalDimensions = getHorizontalDimensions(label, paddingX, metrics);
    int x = horizontalDimensions.x;
    int width = horizontalDimensions.width;
    int y = RECTANGLE_Y_PADDING + (getRowHeight() - REF_HEIGHT) / 2;
    int height = REF_HEIGHT - 2 * RECTANGLE_Y_PADDING;
    RoundRectangle2D rectangle2D = new RoundRectangle2D.Double(x, y, width, height, ROUND_RADIUS, ROUND_RADIUS);

    g2.setColor(bgColor);
    g2.fill(rectangle2D);

    g2.setColor(myColorManager.getReferenceBorderColor());
    g2.draw(rectangle2D);

    g2.setColor(JBColor.BLACK);
    drawText(g2, label, paddingX + flagWidth(), (getRowHeight() - REF_HEIGHT) / 2);

    if (rootIndicatorColor != null && myColorManager.isMultipleRoots() && myDrawMultiRepoIndicator) {
      drawRootIndicator(g2, paddingX, y, height, rootIndicatorColor);
    }

    return new Rectangle(x, y, width, height);
  }

  private void drawRootIndicator(@NotNull Graphics2D g2, int x, int y, int height, @NotNull Color rootIndicatorColor) {
    g2.setColor(rootIndicatorColor);
    int x0 = x + FLAG_PADDING;
    int xMid = x0 + FLAG_WIDTH / 2;
    int xRight = x0 + FLAG_WIDTH;

    int y0 = y - FLAG_TOP_INDENT;
    int yMid = y0 + 2 * height / 3 - 2;
    int yBottom = y0 + height - 4;

    // something like a knight flag
    Polygon polygon = new Polygon(new int[]{x0, xRight, xRight, xMid, x0}, new int[]{y0, y0, yMid, yBottom, yMid}, 5);
    g2.fillPolygon(polygon);
    g2.setColor(myColorManager.getReferenceBorderColor());
    g2.drawPolygon(polygon);
  }

  private HorizontalDimensions getHorizontalDimensions(String label, int paddingX, FontMetrics metrics) {
    int x = paddingX + REF_PADDING / 2 - RECTANGLE_X_PADDING;
    int width = metrics.stringWidth(label) + 2 * RECTANGLE_X_PADDING + flagWidth();
    return new HorizontalDimensions(x, width);
  }

  private double paddingStr(@NotNull String str, @NotNull FontRenderContext renderContext) {
    return getStringWidth(str, renderContext) + REF_PADDING + flagWidth();
  }

  private int flagWidth() {
    return myColorManager.isMultipleRoots() && myDrawMultiRepoIndicator ? FLAG_WIDTH + FLAG_PADDING : 0;
  }

  private static double getStringWidth(@NotNull String str, @NotNull FontRenderContext renderContext) {
    return DEFAULT_FONT.getStringBounds(str, renderContext).getWidth();
  }

  private static void drawText(@NotNull Graphics2D g2, @NotNull String str, int paddingX, int paddingY) {
    FontMetrics metrics = g2.getFontMetrics();
    g2.setColor(DEFAULT_FONT_COLOR);
    int x = paddingX + REF_PADDING / 2;
    int y = paddingY + REF_HEIGHT / 2 + (metrics.getAscent() - metrics.getDescent()) / 2;
    g2.drawString(str, x, y);
  }

  private static void setupGraphics(Graphics2D g2) {
    g2.setFont(DEFAULT_FONT);
    g2.setStroke(new BasicStroke(1.5f));
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  }

  private static class HorizontalDimensions {
    private final int x;
    private final int width;

    private HorizontalDimensions(int x, int width) {
      this.x = x;
      this.width = width;
    }
  }

}
