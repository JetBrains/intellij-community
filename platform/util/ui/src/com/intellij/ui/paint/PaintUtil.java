// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.paint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleType;
import com.intellij.util.ui.AATextInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedList;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.*;
import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;
import static com.intellij.ui.scale.ScaleType.USR_SCALE;

/**
 * Utility methods for pixel-perfect painting in JRE-managed HiDPI mode.
 * <p>
 * It's assumed that the {@link ScaleType#USR_SCALE} factor is already applied to the values (given in the user space)
 * passed to the methods of this class. So the user scale factor is not taken into account.
 *
 * @see ScaleType
 * @author tav
 */
public final class PaintUtil {
  /**
   * Defines which of the {@link Math} rounding method should be applied when converting to the device space.
   */
  public enum RoundingMode {
    FLOOR {
      @Override
      public int round(double value) {
        return (int)Math.floor(value);
      }
    },
    CEIL {
      @Override
      public int round(double value) {
        return (int)Math.ceil(value);
      }
    },
    ROUND {
      @Override
      public int round(double value) {
        return (int)Math.round(value);
      }
    },
    /** Rounds with flooring .5 value */
    ROUND_FLOOR_BIAS {
      @Override
      public int round(double value) {
        return (int)Math.ceil(value - 0.5);
      }
    };

    /**
     * Rounds the value according to the rounding mode.
     *
     * @param value the value to round
     * @return the rounded value
     */
    public abstract int round(double value);
  }

  /**
   * Defines whether a value should be aligned with the nearest integer odd/even value in the device space.
   */
  public enum ParityMode {
    EVEN,
    ODD;
    /**
     * Returns true for {@code EVEN}.
     */
    public boolean even() {
      return this == EVEN;
    }
    /**
     * Returns parity of the value.
     */
    public static ParityMode of(int value) {
      return value % 2 == 0 ? EVEN : ODD;
    }
    /**
     * Returns inverted parity mode.
     */
    public static ParityMode invert(ParityMode pm) {
      return pm == EVEN ? ODD : EVEN;
    }
  }

  /**
   * @see #getParityMode(double, ScaleContext, RoundingMode)
   */
  public static ParityMode getParityMode(double usrValue, @NotNull Graphics2D g) {
    return getParityMode(usrValue, ScaleContext.create(g), null);
  }

  /**
   * Returns parity of the value converted to the device space with the given rounding mode ({@code ROUND} if null).
   *
   * @param usrValue the value, given in the user space
   * @param ctx the scale context (the user scale is ignored)
   * @param rm the rounding mode to apply
   * @return the parity of the value in the device space
   */
  public static ParityMode getParityMode(double usrValue, @NotNull ScaleContext ctx, @Nullable RoundingMode rm) {
    int devValue = devValue(usrValue, getScale(ctx), rm == null ? ROUND : rm);
    return ParityMode.of(devValue);
  }

  /**
   * @see #alignToInt(double, ScaleContext, RoundingMode, ParityMode)
   */
  public static double alignToInt(double usrValue, @NotNull Graphics2D g) {
    return alignToInt(usrValue, ScaleContext.create(g), null, null);
  }

  /**
   * @see #alignToInt(double, ScaleContext, RoundingMode, ParityMode)
   */
  public static double alignToInt(double usrValue, @NotNull Graphics2D g, @Nullable RoundingMode rm) {
    return alignToInt(usrValue, ScaleContext.create(g), rm, null);
  }

  /**
   * @see #alignToInt(double, ScaleContext, RoundingMode, ParityMode)
   */
  public static double alignToInt(double usrValue, @NotNull Graphics2D g, @Nullable ParityMode pm) {
    return alignToInt(usrValue, ScaleContext.create(g), null, pm);
  }

  /**
   * Returns the value in the user space aligned with the integer value in the device space, applying the rounding and parity modes.
   * If the rounding mode is null - {@code ROUND} is applied. If the parity mode is null - it's ignored, otherwise the value
   * converted to the device space is aligned with the nearest integer odd/even value by the provided rounding mode.
   * For instance, 2.1 would be floor'd to odd 1, ceil'd and round'ed to odd 3. Also, 1.9 would be floor'd to odd 1, ceil'd to
   * odd 3 and round'ed to odd 1.
   *
   * @param usrValue the value to align, given in the user space
   * @param ctx the scale context, the user scale is ignored
   * @param rm the rounding mode to apply ({@code ROUND} if null)
   * @param pm the parity mode to apply (ignored if null)
   * @return the aligned value, in the user space
   */
  public static double alignToInt(double usrValue, @NotNull ScaleContext ctx, @Nullable RoundingMode rm, @Nullable ParityMode pm) {
    if (rm == null) rm = ROUND;
    double scale = getScale(ctx);
    if (scale == 0) return 0;

    int devValue = devValue(usrValue, scale, pm != null && rm == ROUND ? FLOOR : rm);
    if (pm != null && ParityMode.of(devValue) != pm) {
      devValue += rm == FLOOR ? -1 : 1;
    }
    return devValue / scale;
  }

  /**
   * Returns the value in the user space aligned to an integer value in both user and device spaces
   * <p>
   *   Doesn't work if scaling is not a multiple of 0.25, returns the original value in such a case.
   * </p>
   *
   * @param usrValue the original value
   * @param ctx the scaling context to determine the system scale
   * @param rm the rounding mode, only FLOOR and CEIL are supported
   * @param pm the parity mode to align with in the device space
   * @return the aligned value or the original value if scaling is impossible to align (weird custom scaling)
   */
  public static int alignIntToInt(int usrValue, @NotNull ScaleContext ctx, @NotNull RoundingMode rm, @Nullable ParityMode pm) {
    if (rm != FLOOR && rm != CEIL) {
      throw new IllegalArgumentException("Invalid value of rounding mode: " + rm + ", only FLOOR and CEIL are supported");
    }
    int result = usrValue;
    int attempts = 0;
    final int maxAttempts = pm == null ? 4 : 8; // should be enough if scaling is a multiple of 0.25
    while (result >= 0 && isNotSuitablyAlignedToInt(result, ctx, pm)) {
      result += rm == FLOOR ? -1 : 1;
      if (++attempts > maxAttempts) {
        return usrValue; // unusual scaling (not a multiple of 0.25)
      }
    }
    return result;
  }

  private static boolean isNotSuitablyAlignedToInt(int value, @NotNull ScaleContext ctx, @Nullable ParityMode pm) {
    double scaled = devValue(value, ctx);
    int rounded = (int) Math.round(scaled);
    boolean aligned = Math.abs(rounded - scaled) < 0.0001;
    boolean parityMatches = pm == null || ParityMode.of(rounded) == pm;
    return !(aligned && parityMatches);
  }

  /**
   * @see #alignToInt(double, ScaleContext, RoundingMode, ParityMode)
   */
  public static double alignToInt(double usrValue, @NotNull ScaleContext ctx) {
    return alignToInt(usrValue, ctx, null, null);
  }

  /**
   * Converts the value from the user to the device space.
   *
   * @param usrValue the value in the user space
   * @param g the graphics
   * @return the converted value
   */
  public static double devValue(double usrValue, @NotNull Graphics2D g) {
    return devValue(usrValue, ScaleContext.create(g));
  }

  /**
   * @see #devValue(double, Graphics2D)
   */
  public static double devValue(double usrValue, @NotNull ScaleContext ctx) {
    return usrValue * getScale(ctx);
  }

  private static int devValue(double usrValue, double scale, @Nullable RoundingMode rm) {
    if (rm == null) rm = ROUND;
    return rm.round(usrValue * scale);
  }

  /**
   * Returns one device pixel converted to the user space.
   *
   * @param g the graphics
   * @return one device pixel in user space
   */
  public static double devPixel(Graphics2D g) {
    return 1 / devValue(1, g);
  }

  private static double getScale(ScaleContext ctx) {
    // exclude the user scale, unless it's zero
    double scale = ctx.getScale(USR_SCALE) == 0 ? 0 : ctx.getScale(PIX_SCALE) / ctx.getScale(USR_SCALE);
    if (scale <= 0) {
      //Logger.getInstance(PaintUtil.class).warn("bad scale in the context: " + ctx.toString(), new Throwable());
    }
    return scale;
  }

  /**
   * Aligns the x or/and y translate of the graphics to the integer coordinate if the graphics has fractional scale transform,
   * otherwise does nothing.
   *
   * @param g the graphics to align
   * @param offset x/y offset to take into account when provided (this may be e.g. insets left/top)
   * @param alignX should the x-translate be aligned
   * @param alignY should the y-translate be aligned
   * @return the original graphics transform when aligned, otherwise null
   */
  public static @Nullable AffineTransform alignTxToInt(@NotNull Graphics2D g, @Nullable Point2D offset, boolean alignX, boolean alignY, RoundingMode rm) {
    try {
      AffineTransform tx = g.getTransform();
      if (isFractionalScale(tx) && (tx.getType() & AffineTransform.TYPE_MASK_ROTATION) == 0) {
        double scaleX = tx.getScaleX();
        double scaleY = tx.getScaleY();
        AffineTransform alignedTx = new AffineTransform();
        double trX = tx.getTranslateX();
        double trY = tx.getTranslateY();
        if (alignX) {
          double offX = trX + (offset != null ? offset.getX() * scaleX : 0);
          trX += rm.round(offX) - offX;
        }
        if (alignY) {
          double offY = trY + (offset != null ? offset.getY() * scaleY : 0);
          trY += rm.round(offY) - offY;
        }
        alignedTx.translate(trX, trY);
        alignedTx.scale(scaleX, scaleY);
        assert tx.getShearX() == 0 && tx.getShearY() == 0; // the shear is ignored
        g.setTransform(alignedTx);
        return tx;
      }
    }
    catch (Exception e) {
      Logger.getInstance(PaintUtil.class).error(e);
    }
    return null;
  }

  /**
   * Aligns the graphics rectangular clip to int coordinates if the graphics has fractional scale transform.
   *
   * @param g the graphics to align
   * @param alignH should the x/width be aligned
   * @param alignV should the y/height be aligned
   * @param xyRM the rounding mode to apply to the clip's x/y
   * @param whRM the rounding mode to apply to the clip's width/height
   * @return the original graphics clip when aligned, otherwise null
   */
  public static @Nullable Shape alignClipToInt(@NotNull Graphics2D g, boolean alignH, boolean alignV, RoundingMode xyRM, RoundingMode whRM) {
    AffineTransform transform = g.getTransform();
    double scaleX = transform.getScaleX();
    double scaleY = transform.getScaleY();
    // temporarily unscale to prevent getClip() from messing with coordinates
    g.scale(1.0 / scaleX, 1.0 / scaleY);
    try {
      Shape clip = g.getClip();
      if (clip instanceof Rectangle2D rect && isFractionalScale(transform)) {
        double x = rect.getX();
        double y = rect.getY();
        double w = rect.getWidth();
        double h = rect.getHeight();
        if (alignH) {
          x = alignToInt(rect.getX(), g, xyRM);
          w = alignToInt(rect.getX() + rect.getWidth(), g, whRM) - x;
        }
        if (alignV) {
          y = alignToInt(rect.getY(), g, xyRM);
          h = alignToInt(rect.getY() + rect.getHeight(), g, whRM) - y;
        }
        // A rare case when replacing the clipping rectangle actually makes sense:
        //noinspection GraphicsSetClipInspection
        g.setClip(new Rectangle2D.Double(x, y, w, h));
        return clip;
      }
      return null;
    } finally {
      // re-scale back
      g.scale(scaleX, scaleY);
    }
  }

  /**
   * Returns (in user space) the fractional part of the XY {@code comp}'s offset relative to its {@code JRootPane} ancestor.
   * <p>
   * Used for repainting a {@code JComponent} in a UI hierarchy via an image buffer on fractional scale graphics.
   * <pre>
   * class MyPainter {
   *   void paintToBuffer() {
   *     JComponent myComp = getMyComp();
   *     Point2D offset = getFractOffsetInRootPane(myComp);
   *     Image buffer = getBufferForMyComp(myComp);
   *     Graphics2D g2d = (Graphics2D)buffer.getGraphics();
   *     // the fractional part of myComp's offset affects J2D rounding logic and so the final rasterization of myComp
   *     // the offset is set on g2d to have the same myComp's rasterization as in original JRootPane full repaint
   *     // otherwise pixel floating effect can be observed
   *     g2d.translate(offset.getX(), offset.getY());
   *     myComp.paint(g2d);
   *   }
   *   void paint(Graphics2D g2d) { // g2d is translated to myComp's parent XY
   *     JComponent myComp = getMyComp();
   *     Point2D offset = getFractOffsetInRootPane(myComp);
   *     Image buffer = getBufferForMyComp(myComp); // already painted buffer
   *     g2d.translate(myComp.getX() - offset.getX(), myComp.getY() - offset.getY()); // negate the fractional offset set above
   *     UIUtil.paintImage(g2d, buffer, 0, 0, null);
   *   }
   * }
   * </pre>
   */
  public static @NotNull Point2D getFractOffsetInRootPane(@NotNull JComponent comp) {
    if (!comp.isShowing() || !isFractionalScale(comp.getGraphicsConfiguration().getDefaultTransform())) return new Point2D.Double();
    int x = 0;
    int y = 0;
    while (!(comp instanceof JRootPane) && comp != null) {
      x += comp.getX();
      y += comp.getY();
      comp = (JComponent)comp.getParent();
    }
    double scale = JBUIScale.sysScale(comp);
    double sx = x * scale;
    double sy = y * scale;
    return new Point2D.Double((sx - (int)sx) / scale, (sy - (int)sy) / scale);
  }

  /**
   * Returns negated Point2D instance.
   */
  public static @NotNull Point2D negate(@NotNull Point2D pt) {
    return new Point2D.Double(-pt.getX(), -pt.getY());
  }

  /**
   * Returns true if the transform matrix contains fractional scale element.
   */
  public static boolean isFractionalScale(@NotNull AffineTransform tx) {
    double scaleX = tx.getScaleX();
    double scaleY = tx.getScaleY();
    return scaleX != (int)scaleX || scaleY != (int)scaleY;
  }

  /**
   * Calls the {@code paint} action with the provided antialiasing.
   *
   * @param g the graphics to paint on
   * @param valueAA a value for the {@link RenderingHints#KEY_ANTIALIASING} key
   * @param paint the paint action
   */
  public static void paintWithAA(@NotNull Graphics2D g, @NotNull Object valueAA, @NotNull Runnable paint) {
    if (valueAA == RenderingHints.VALUE_ANTIALIAS_DEFAULT) {
      paint.run();
      return;
    }
    Object key = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, valueAA);
    try {
      paint.run();
    } finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, key);
    }
  }

  public static @NotNull Point2D insets2offset(@Nullable Insets in) {
    return in == null ? new Point2D.Double(0, 0) : new Point2D.Double(in.left, in.top);
  }

  /**
   * Calculates the width of the specified text string when drawn using the provided Graphics context and FontMetrics.
   * This method provides a more accurate measurement compared to the metrics.stringWidth(...) method, as it takes into account the Graphics context.
   * <p/>
   * Note: this method ignores aliasing hints stored in the JComponent and shall NOT be used
   * in pair with {@link SwingUtilities2#drawString(JComponent, Graphics, String, int, int)}, see {@link AATextInfo}.
   *
   * @param text    The text string whose width needs to be calculated.
   * @param g       The Graphics context used for rendering the text.
   * @param metrics The FontMetrics object associated with the font used for rendering the text.
   * @return The width of the text string in pixels when drawn using the specified Graphics context and FontMetrics.

   * @deprecated Prefer using {@link UIUtil#computeStringWidth(JComponent, String)} instead
   */
  @Deprecated(forRemoval = true)
  public static int getStringWidth(String text, Graphics g, FontMetrics metrics) {
    return metrics.getStringBounds(text, g).getBounds().width;
  }

  @ApiStatus.Internal
  @Contract("!null, _, _ -> !null")
  public static @Nullable String cutContainerText(@Nullable String text, int maxWidth, @NotNull JComponent component) {
    if (text == null) return null;

    if (text.startsWith("(") && text.endsWith(")")) {
      text = text.substring(1, text.length() - 1);
    }

    if (maxWidth < 0) return text;

    FontMetrics fontMetrics = component.getFontMetrics(component.getFont());
    boolean in = text.startsWith("in ");
    if (in) text = text.substring(3);
    String left = in ? "in " : "";
    String adjustedText = left + text;

    int fullWidth = UIUtil.computeStringWidth(component, fontMetrics, adjustedText);
    if (fullWidth < maxWidth) return adjustedText;

    String separator = text.contains("/") ? "/" :
                       SystemInfo.isWindows && text.contains("\\") ? "\\" :
                       text.contains(".") ? "." :
                       text.contains("-") ? "-" : " ";
    LinkedList<String> parts = new LinkedList<>(StringUtil.split(text, separator));
    int index;
    while (parts.size() > 1) {
      index = parts.size() / 2 - 1;
      parts.remove(index);
      if (UIUtil.computeStringWidth(component, fontMetrics, left + StringUtil.join(parts, separator) + "...") < maxWidth) {
        parts.add(index, "...");
        return left + StringUtil.join(parts, separator);
      }
    }
    int adjustedWidth = Math.max(adjustedText.length() * maxWidth / fullWidth - 1, left.length() + 3);
    return StringUtil.trimMiddle(adjustedText, adjustedWidth);
  }
}
