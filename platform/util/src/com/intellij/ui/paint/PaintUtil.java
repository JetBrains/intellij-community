// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.JBUI.ScaleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.FLOOR;
import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;
import static com.intellij.util.ui.JBUI.ScaleType.PIX_SCALE;
import static com.intellij.util.ui.JBUI.ScaleType.USR_SCALE;

/**
 * Utility methods for pixel-perfect painting in JRE-managed HiDPI mode.
 * <p>
 * It's assumed that the {@link ScaleType#USR_SCALE} factor is already applied to the values (given in the user space)
 * passed to the methods of this class. So the user scale factor is not taken into account.
 *
 * @see ScaleType
 * @author tav
 */
public class PaintUtil {
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
   * Returns the value in the user aligned with the integer value in the device space, applying the rounding and parity modes.
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
    int devValue = devValue(usrValue, scale, pm != null && rm == ROUND ? FLOOR : rm);
    if (pm != null && ParityMode.of(devValue) != pm) {
      devValue += (rm == FLOOR) ? -1 : 1;
    }
    return devValue / scale;
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
    return ctx.getScale(USR_SCALE) == 0 ? 0 : ctx.getScale(PIX_SCALE) / ctx.getScale(USR_SCALE);
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
  public static AffineTransform alignTxToInt(@NotNull Graphics2D g, @Nullable Point2D offset, boolean alignX, boolean alignY, RoundingMode rm) {
    try {
      AffineTransform tx = g.getTransform();
      if (isFractionalScale(tx)) {
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
      Logger.getInstance("#com.intellij.ui.paint.PaintUtil").error(e);
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
  public static Shape alignClipToInt(@NotNull Graphics2D g, boolean alignH, boolean alignV, RoundingMode xyRM, RoundingMode whRM) {
    Shape clip = g.getClip();
    if ((clip instanceof Rectangle2D) && isFractionalScale(g.getTransform())) {
      Rectangle2D rect = (Rectangle2D)clip;
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
      g.setClip(new Rectangle2D.Double(x, y, w, h));
      return clip;
    }
    return null;
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

  @NotNull
  public static Point2D insets2offset(@Nullable Insets in) {
    return in == null ? new Point2D.Double(0, 0) : new Point2D.Double(in.left, in.top);
  }
}
