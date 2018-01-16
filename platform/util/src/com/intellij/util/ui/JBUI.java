/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.components.BorderLayoutPanel;
import gnu.trove.TDoubleObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static com.intellij.util.ui.JBUI.ScaleType.*;

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
public class JBUI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.JBUI");

  public static final String USER_SCALE_FACTOR_PROPERTY = "JBUI.userScaleFactor";

  private static final PropertyChangeSupport PCS = new PropertyChangeSupport(new JBUI());

  private static final float DISCRETE_SCALE_RESOLUTION = 0.25f;

  /**
   * The IDE supports two different HiDPI modes:
   *
   * 1) IDE-managed HiDPI mode.
   *
   * Supported for backward compatibility until complete transition to the JRE-managed HiDPI mode happens.
   * In this mode there's a single coordinate space and the whole UI is scaled by the IDE guided by the
   * user scale factor ({@link #USR_SCALE}).
   *
   * 2) JRE-managed HiDPI mode.
   *
   * In this mode the JRE scales graphics prior to drawing it on the device. So, there're two coordinate
   * spaces: the user space and the device space. The system scale factor ({@link #SYS_SCALE}) defines the
   * transform b/w the spaces. The UI size metrics (windows, controls, fonts height) are in the user
   * coordinate space. Though, the raster images should be aware of the device scale in order to meet
   * HiDPI. (For instance, JRE on a Mac Retina monitor device works in the JRE-managed HiDPI mode,
   * transforming graphics to the double-scaled device coordinate space)
   *
   * The IDE operates the scale factors of the following types:
   *
   * 1) The user scale factor: {@link #USR_SCALE}
   * 2) The system (monitor device) scale factor: {@link #SYS_SCALE}
   * 3) The object (UI instance specific) scale factor: {@link #OBJ_SCALE}
   * 4) The pixel scale factor: {@link #PIX_SCALE}
   *
   * @see UIUtil#isJreHiDPIEnabled()
   * @see UIUtil#isJreHiDPI()
   * @see UIUtil#isJreHiDPI(GraphicsConfiguration)
   * @see UIUtil#isJreHiDPI(Graphics2D)
   * @see JBUI#isUsrHiDPI()
   * @see JBUI#isPixHiDPI(GraphicsConfiguration)
   * @see JBUI#isPixHiDPI(Graphics2D)
   * @see UIUtil#drawImage(Graphics, Image, int, int, int, int, ImageObserver)
   * @see UIUtil#createImage(Graphics, int, int, int)
   * @see UIUtil#createImage(GraphicsConfiguration, int, int, int)
   * @see UIUtil#createImage(int, int, int)
   * @see ScaleContext
   */
  public enum ScaleType {
    /**
     * The user scale factor is set and managed by the IDE. Currently it's derived from the UI font size,
     * specified in the IDE Settings.
     *
     * The user scale value depends on which HiDPI mode is enabled. In the IDE-managed HiDPI mode the
     * user scale "includes" the default system scale and simply equals it with the default UI font size.
     * In the JRE-managed HiDPI mode the user scale is independent of the system scale and equals 1.0
     * with the default UI font size. In case the default UI font size changes, the user scale changes
     * proportionally in both the HiDPI modes.
     *
     * In the IDE-managed HiDPI mode the user scale completely defines the UI scale. In the JRE-managed
     * HiDPI mode the user scale can be considered a supplementary scale taking effect in cases like
     * the IDE Presentation Mode and when the default UI scale is changed by the user.
     *
     * @see #setUserScaleFactor(float)
     * @see #scale(float)
     * @see #scale(int)
     */
    USR_SCALE,
    /**
     * The system scale factor is defined by the device DPI and/or the system settings. For instance,
     * Mac Retina monitor device has the system scale 2.0 by default. As there can be multiple devices
     * (multi-monitor configuration) there can be multiple system scale factors, appropriately. However,
     * there's always a single default system scale factor corresponding to the default device. And it's
     * the only system scale available in the IDE-managed HiDPI mode.
     *
     * In the JRE-managed HiDPI mode, the system scale defines the scale of the transform b/w the user
     * and the device coordinate spaces performed by the JRE.
     *
     * @see #sysScale()
     * @see #sysScale(GraphicsConfiguration)
     * @see #sysScale(Graphics2D)
     * @see #sysScale(Component)
     */
    SYS_SCALE,
    /**
     * An extra scale factor of a particular UI object, which doesn't affect any other UI object, as opposed
     * to the user scale and the system scale factors. Doesn't depend on the HiDPI mode and is 1.0 by default.
     */
    OBJ_SCALE,
    /**
     * The pixel scale factor "combines" all the other scale factors (user, system and object) and defines the
     * effective scale of a particular UI object.
     *
     * For instance, on Mac Retina monitor (JRE-managed HiDPI) in the Presentation mode (which, say,
     * doubles the UI scale) the pixel scale would equal 4.0 (provided the object scale is 1.0). The value
     * is the product of the user scale 2.0 and the system scale 2.0. In the IDE-managed HiDPI mode,
     * the pixel scale is the product of the user scale and the object scale.
     *
     * @see #pixScale()
     * @see #pixScale(GraphicsConfiguration)
     * @see #pixScale(Graphics2D)
     * @see #pixScale(Component)
     * @see #pixScale(GraphicsConfiguration, float)
     * @see #pixScale(float)
     */
    PIX_SCALE;

    public Scale of(double value) {
      return Scale.create(value, this);
    }
  }

  /**
   * A scale factor of a particular type.
   */
  public static class Scale {
    private final double value;
    private final ScaleType type;

    // The cache radically reduces potentially thousands of equal Scale instances.
    private static final ThreadLocal<EnumMap<ScaleType, TDoubleObjectHashMap<Scale>>> cache =
      new ThreadLocal<EnumMap<ScaleType, TDoubleObjectHashMap<Scale>>>() {
        @Override
        protected EnumMap<ScaleType, TDoubleObjectHashMap<Scale>> initialValue() {
          return new EnumMap<ScaleType, TDoubleObjectHashMap<Scale>>(ScaleType.class);
        }
      };

    public static Scale create(double value, ScaleType type) {
      EnumMap<ScaleType, TDoubleObjectHashMap<Scale>> emap = cache.get();
      TDoubleObjectHashMap<Scale> map = emap.get(type);
      if (map == null) {
        emap.put(type, map = new TDoubleObjectHashMap<Scale>());
      }
      Scale scale = map.get(value);
      if (scale != null) return scale;
      map.put(value, scale = new Scale(value, type));
      return scale;
    }

    private Scale(double value, ScaleType type) {
      this.value = value;
      this.type = type;
    }

    public double value() {
      return value;
    }

    public ScaleType type() {
      return type;
    }

    public Scale newOrThis(double value) {
      if (this.value == value) return this;
      return type.of(value);
    }

    @Override
    public String toString() {
      return "[" + type.name() + " " + value + "]";
    }
  }

  /**
   * The system scale factor, corresponding to the default monitor device.
   */
  private static final Float SYSTEM_SCALE_FACTOR = sysScale();

  /**
   * The user space scale factor.
   */
  private static float userScaleFactor;

  static {
    setUserScaleFactor(UIUtil.isJreHiDPIEnabled() ? 1f : SYSTEM_SCALE_FACTOR);
    LOG.info("System scale factor: " + SYSTEM_SCALE_FACTOR + " (" +
             (UIUtil.isJreHiDPIEnabled() ? "JRE-managed" : "IDE-managed") + " HiDPI)");
  }

  /**
   * Adds property change listener. Supported properties:
   * {@link #USER_SCALE_FACTOR_PROPERTY}
   */
  public static void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    PCS.addPropertyChangeListener(propertyName, listener);
  }

  /**
   * Removes property change listener
   */
  public static void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    PCS.removePropertyChangeListener(propertyName, listener);
  }

  /**
   * Returns the system scale factor, corresponding to the default monitor device.
   */
  public static float sysScale() {
    if (SYSTEM_SCALE_FACTOR != null) {
      return SYSTEM_SCALE_FACTOR;
    }

    if (UIUtil.isJreHiDPIEnabled()) {
      GraphicsDevice gd = null;
      try {
        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
      } catch (HeadlessException ignore) {}
      if (gd != null && gd.getDefaultConfiguration() != null) {
        return sysScale(gd.getDefaultConfiguration());
      }
      return 1.0f;
    }

    if (SystemProperties.has("hidpi") && !SystemProperties.is("hidpi")) {
      return 1.0f;
    }

    UIUtil.initSystemFontData();
    Pair<String, Integer> fdata = UIUtil.getSystemFontData();

    int size = fdata == null ? Fonts.label().getSize() : fdata.getSecond();
    return getFontScale(size);
  }

  /**
   * Returns the system scale factor, corresponding to the graphics configuration.
   * In the IDE-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(@Nullable GraphicsConfiguration gc) {
    if (UIUtil.isJreHiDPIEnabled() && gc != null) {
      if (gc.getDevice().getType() == GraphicsDevice.TYPE_RASTER_SCREEN) {
        if (SystemInfo.isMac && UIUtil.isJreHiDPI_earlierVersion()) {
          return UIUtil.DetectRetinaKit.isOracleMacRetinaDevice(gc.getDevice()) ? 2f : 1f;
        }
        return (float)gc.getDefaultTransform().getScaleX();
      }
    }
    return sysScale();
  }

  /**
   * Returns the system scale factor, corresponding to the graphics.
   * For BufferedImage's graphics, the scale is taken from the graphics itself.
   * In the IDE-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(@Nullable Graphics2D g) {
    if (UIUtil.isJreHiDPIEnabled() && g != null) {
      GraphicsConfiguration gc = g.getDeviceConfiguration();
      if (gc == null ||
          gc.getDevice().getType() == GraphicsDevice.TYPE_IMAGE_BUFFER ||
          gc.getDevice().getType() == GraphicsDevice.TYPE_PRINTER)
      {
        // in this case gc doesn't provide a valid scale
        return (float)g.getTransform().getScaleX();
      }
      return sysScale(gc);
    }
    return sysScale();
  }

  /**
   * Returns the system scale factor, corresponding to the device the component is tied to.
   * In the IDE-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(@Nullable Component comp) {
    if (comp != null) {
      return sysScale(comp.getGraphicsConfiguration());
    }
    return sysScale();
  }

  public static double sysScale(@Nullable ScaleContext ctx) {
    if (ctx != null) {
      return ctx.getScale(SYS_SCALE);
    }
    return sysScale();
  }

  /**
   * Returns the pixel scale factor, corresponding to the default monitor device.
   */
  public static float pixScale() {
    return UIUtil.isJreHiDPIEnabled() ? sysScale() * scale(1f) : scale(1f);
  }

  /**
   * Returns "f" scaled by pixScale().
   */
  public static float pixScale(float f) {
    return pixScale() * f;
  }

  /**
   * Returns "f" scaled by pixScale(gc).
   */
  public static float pixScale(@Nullable GraphicsConfiguration gc, float f) {
    return pixScale(gc) * f;
  }

  /**
   * Returns the pixel scale factor, corresponding to the provided configuration.
   * In the IDE-managed HiDPI mode defaults to {@link #pixScale()}
   */
  public static float pixScale(@Nullable GraphicsConfiguration gc) {
    return UIUtil.isJreHiDPIEnabled() ? sysScale(gc) * scale(1f) : scale(1f);
  }

  /**
   * Returns the pixel scale factor, corresponding to the provided graphics.
   * In the IDE-managed HiDPI mode defaults to {@link #pixScale()}
   */
  public static float pixScale(@Nullable Graphics2D g) {
    return UIUtil.isJreHiDPIEnabled() ? sysScale(g) * scale(1f) : scale(1f);
  }

  /**
   * Returns the pixel scale factor, corresponding to the device the provided component is tied to.
   * In the IDE-managed HiDPI mode defaults to {@link #pixScale()}
   */
  public static float pixScale(@Nullable Component comp) {
    return pixScale(comp != null ? comp.getGraphicsConfiguration() : null);
  }

  public static <T extends BaseScaleContext> double pixScale(@Nullable T ctx) {
    if (ctx != null) {
      double usrScale = ctx.getScale(USR_SCALE);
      return UIUtil.isJreHiDPIEnabled() ? ctx.getScale(SYS_SCALE) * usrScale : usrScale;
    }
    return pixScale();
  }

  private static void setUserScaleFactorProperty(float scale) {
    PCS.firePropertyChange(USER_SCALE_FACTOR_PROPERTY, userScaleFactor, userScaleFactor = scale);
    LOG.info("User scale factor: " + userScaleFactor);
  }

  /**
   * Sets the user scale factor.
   * The method is used by the IDE, it's not recommended to call the method directly from the client code.
   * For debugging purposes, the following registry keys can be used:
   * ide.ui.scale.override=[boolean]
   * ide.ui.scale=[float]
   */
  public static void setUserScaleFactor(float scale) {
    if (SystemProperties.has("hidpi") && !SystemProperties.is("hidpi")) {
      setUserScaleFactorProperty(1.0f);
      return;
    }

    scale = discreteScale(scale);

    // Downgrading user scale below 1.0 may be uncomfortable (tiny icons),
    // whereas some users prefer font size slightly below normal which is ok.
    if (scale < 1 && sysScale() >= 1) scale = 1;

    // Ignore the correction when UIUtil.DEF_SYSTEM_FONT_SIZE is overridden, see UIUtil.initSystemFontData.
    if (SystemInfo.isLinux && scale == 1.25f && UIUtil.DEF_SYSTEM_FONT_SIZE == 12) {
      //Default UI font size for Unity and Gnome is 15. Scaling factor 1.25f works badly on Linux
      scale = 1f;
    }
    if (userScaleFactor == scale) {
      return;
    }
    setUserScaleFactorProperty(scale);
  }

  static float discreteScale(float scale) {
    return Math.round(scale / DISCRETE_SCALE_RESOLUTION) * DISCRETE_SCALE_RESOLUTION;
  }

  /**
   * @return 'f' scaled by the user scale factor
   */
  public static float scale(float f) {
    return f * userScaleFactor;
  }

  /**
   * @return 'i' scaled by the user scale factor
   */
  public static int scale(int i) {
    return Math.round(userScaleFactor * i);
  }

  public static int scaleFontSize(float fontSize) {
    if (userScaleFactor == 1.25f) return (int)(fontSize * 1.34f);
    if (userScaleFactor == 1.75f) return (int)(fontSize * 1.67f);
    return (int)scale(fontSize);
  }

  /**
   * @return the scale factor of {@code fontSize} relative to the standard font size (currently 12pt)
   */
  public static float getFontScale(float fontSize) {
    return fontSize / UIUtil.DEF_SYSTEM_FONT_SIZE;
  }

  public static JBDimension size(int width, int height) {
    return new JBDimension(width, height);
  }

  public static JBDimension size(int widthAndHeight) {
    return new JBDimension(widthAndHeight, widthAndHeight);
  }

  public static JBDimension size(Dimension size) {
    if (size instanceof JBDimension) {
      JBDimension newSize = ((JBDimension)size).newSize();
      return size instanceof UIResource ? newSize.asUIResource() : newSize;
    }
    return new JBDimension(size.width, size.height);
  }

  public static JBInsets insets(int top, int left, int bottom, int right) {
    return new JBInsets(top, left, bottom, right);
  }

  public static JBInsets insets(int all) {
    return insets(all, all, all, all);
  }

  public static JBInsets insets(int topBottom, int leftRight) {
    return insets(topBottom, leftRight, topBottom, leftRight);
  }

  public static JBInsets emptyInsets() {
    return new JBInsets(0, 0, 0, 0);
  }

  public static JBInsets insetsTop(int t) {
    return insets(t, 0, 0, 0);
  }

  public static JBInsets insetsLeft(int l) {
    return insets(0, l, 0, 0);
  }

  public static JBInsets insetsBottom(int b) {
    return insets(0, 0, b, 0);
  }

  public static JBInsets insetsRight(int r) {
    return insets(0, 0, 0, r);
  }

  /**
   * @deprecated use JBUI.scale(EmptyIcon.create(size)) instead
   */
  public static EmptyIcon emptyIcon(int size) {
    return scale(EmptyIcon.create(size));
  }

  @NotNull
  public static <T extends JBIcon> T scale(@NotNull T icon) {
    return (T)icon.withIconPreScaled(false);
  }

  @NotNull
  public static JBDimension emptySize() {
    return new JBDimension(0, 0);
  }

  @NotNull
  public static JBInsets insets(@NotNull Insets insets) {
    return JBInsets.create(insets);
  }

  /**
   * @deprecated use {@link #isUsrHiDPI()} instead
   */
  @Deprecated
  public static boolean isHiDPI() {
    return isUsrHiDPI();
  }

  /**
   * Returns whether the {@link ScaleType#USR_SCALE} scale factor assumes HiDPI-awareness.
   * An equivalent of {@code isHiDPI(scale(1f))}
   */
  public static boolean isUsrHiDPI() {
      return isHiDPI(scale(1f));
  }

  /**
   * Returns whether the {@link ScaleType#PIX_SCALE} scale factor assumes HiDPI-awareness in the provided graphics config.
   * An equivalent of {@code isHiDPI(pixScale(gc))}
   */
  public static boolean isPixHiDPI(@Nullable GraphicsConfiguration gc) {
    return isHiDPI(pixScale(gc));
  }

  /**
   * Returns whether the {@link ScaleType#PIX_SCALE} scale factor assumes HiDPI-awareness in the provided graphics.
   * An equivalent of {@code isHiDPI(pixScale(g))}
   */
  public static boolean isPixHiDPI(@Nullable Graphics2D g) {
    return isHiDPI(pixScale(g));
  }

  /**
   * Returns whether the {@link ScaleType#PIX_SCALE} scale factor assumes HiDPI-awareness in the provided component's device.
   * An equivalent of {@code isHiDPI(pixScale(comp))}
   */
  public static boolean isPixHiDPI(@Nullable Component comp) {
    return isHiDPI(pixScale(comp));
  }

  /**
   * Returns whether the provided scale assumes HiDPI-awareness.
   */
  public static boolean isHiDPI(double scale) {
    return scale > 1f;
  }

  /**
   * Aligns the x or/and y translate of the graphics to the integer coordinate grid if the graphics has fractional scale transform,
   * otherwise does nothing. This is used to avoid the rounding problem, see JRE-502.
   *
   * @param g the graphics to align
   * @param alignX should the x-translate be aligned
   * @param alignY should the y-translate be aligned
   * @return the original graphics transform when aligned, otherwise null
   */
  public static AffineTransform alignToIntGrid(@NotNull Graphics2D g, boolean alignX, boolean alignY) {
    try {
      AffineTransform tx = g.getTransform();
      if (isFractionalScale(tx)) {
        double scaleX = tx.getScaleX();
        double scaleY = tx.getScaleY();
        AffineTransform alignedTx = new AffineTransform();
        double trX = alignX ? (int)Math.ceil(tx.getTranslateX() - 0.5) : tx.getTranslateX();
        double trY = alignY ? (int)Math.ceil(tx.getTranslateY() - 0.5) : tx.getTranslateY();
        alignedTx.translate(trX, trY);
        alignedTx.scale(scaleX, scaleY);
        assert tx.getShearX() == 0 && tx.getShearY() == 0; // the shear is ignored
        g.setTransform(alignedTx);
        return tx;
      }
    }
    catch (Exception e) {
      LOG.trace(e);
    }
    return null;
  }

  /**
   * Returns true if the transform matrix contains fractional scale element.
   */
  public static boolean isFractionalScale(AffineTransform tx) {
    double scaleX = tx.getScaleX();
    double scaleY = tx.getScaleY();
    return scaleX != (int)scaleX || scaleY != (int)scaleY;
  }

  public static class Fonts {
    @NotNull
    public static JBFont label() {
      return JBFont.create(UIManager.getFont("Label.font"), false);
    }

    @NotNull
    public static JBFont label(float size) {
      return label().deriveFont(scale(size));
    }

    @NotNull
    public static JBFont smallFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }

    @NotNull
    public static JBFont miniFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.MINI));
    }

    @NotNull
    public static JBFont create(String fontFamily, int size) {
      return JBFont.create(new Font(fontFamily, Font.PLAIN, size));
    }
  }

  @SuppressWarnings("UseDPIAwareBorders")
  public static class Borders {
    public static JBEmptyBorder empty(int top, int left, int bottom, int right) {
      return new JBEmptyBorder(top, left, bottom, right);
    }

    public static JBEmptyBorder empty(int topAndBottom, int leftAndRight) {
      return empty(topAndBottom, leftAndRight, topAndBottom, leftAndRight);
    }

    public static JBEmptyBorder emptyTop(int offset) {
      return empty(offset, 0, 0, 0);
    }

    public static JBEmptyBorder emptyLeft(int offset) {
      return empty(0, offset,  0, 0);
    }

    public static JBEmptyBorder emptyBottom(int offset) {
      return empty(0, 0, offset, 0);
    }

    public static JBEmptyBorder emptyRight(int offset) {
      return empty(0, 0, 0, offset);
    }

    public static JBEmptyBorder empty() {
      return empty(0, 0, 0, 0);
    }

    public static Border empty(int offsets) {
      return empty(offsets, offsets, offsets, offsets);
    }

    public static Border customLine(Color color, int top, int left, int bottom, int right) {
      return new CustomLineBorder(color, insets(top, left, bottom, right));
    }

    public static Border customLine(Color color, int thickness) {
      return customLine(color, thickness, thickness, thickness, thickness);
    }

    public static Border customLine(Color color) {
      return customLine(color, 1);
    }

    public static Border merge(@Nullable Border source, @NotNull Border extra, boolean extraIsOutside) {
      if (source == null) return extra;
      return new CompoundBorder(extraIsOutside ? extra : source, extraIsOutside? source : extra);
    }
  }

  public static class Panels {
    public static BorderLayoutPanel simplePanel() {
      return new BorderLayoutPanel();
    }

    public static BorderLayoutPanel simplePanel(Component comp) {
      return simplePanel().addToCenter(comp);
    }

    public static BorderLayoutPanel simplePanel(int hgap, int vgap) {
      return new BorderLayoutPanel(hgap, vgap);
    }
  }

  /**
   * A wrapper over a user scale supplier, representing a state of a UI element
   * in which its initial size is either pre-scaled (according to {@link #currentScale()})
   * or not (given in a standard resolution, e.g. 16x16 for an icon).
   */
  public abstract static class Scaler {
    protected double initialScale = currentScale();

    private double alignedScale() {
      return currentScale() / initialScale;
    }

    protected boolean isPreScaled() {
      return initialScale != 1d;
    }

    protected void setPreScaled(boolean preScaled) {
      initialScale = preScaled ? currentScale() : 1d;
    }

    /**
     * @param value the value (e.g. a size of the associated UI object) to scale
     * @return the scaled result, taking into account the pre-scaled state and {@link #currentScale()}
     */
    public double scaleVal(double value) {
      return value * alignedScale();
    }

    /**
     * Supplies the Scaler with the current user scale. This can be the current global user scale or
     * the context scale ({@link BaseScaleContext#usrScale}) or something else.
     */
    protected abstract double currentScale();

    /**
     * Synchronizes the state with the provided scaler.
     *
     * @return whether the state has been updated
     */
    public boolean update(@NotNull Scaler scaler) {
      boolean updated = initialScale != scaler.initialScale;
      initialScale = scaler.initialScale;
      return updated;
    }
  }

  /**
   * Represents a snapshot of the scale factors (see {@link ScaleType}), except the system scale.
   * The context can be associated with a UI object (see {@link ScaleContextAware}) to define its HiDPI behaviour.
   * Unlike {@link ScaleContext}, BaseScaleContext is system scale independent and is thus used for vector-based painting.
   *
   * @see ScaleContextAware
   * @see ScaleContext
   * @author tav
   */
  public static class BaseScaleContext {
    protected Scale usrScale = USR_SCALE.of(scale(1f));
    protected Scale objScale = OBJ_SCALE.of(1d);
    protected Scale pixScale = PIX_SCALE.of(usrScale.value);

    private List<UpdateListener> listeners;

    private BaseScaleContext() {
    }

    /**
     * Creates a context with the provided scale factors (system scale is ignored)
     */
    public static BaseScaleContext create(@NotNull Scale... scales) {
      BaseScaleContext ctx = create();
      for (Scale s : scales) ctx.update(s);
      return ctx;
    }

    /**
     * Creates a default context with the current user scale
     */
    public static BaseScaleContext create() {
      return new BaseScaleContext();
    }

    protected double derivePixScale() {
      return usrScale.value * objScale.value;
    }

    /**
     * @return the context scale factor of the provided type (1d for system scale)
     */
    public double getScale(ScaleType type) {
      switch (type) {
        case USR_SCALE: return usrScale.value;
        case SYS_SCALE: return 1d;
        case OBJ_SCALE: return objScale.value;
        case PIX_SCALE: return pixScale.value;
      }
      return 1f; // unreachable
    }

    protected boolean onUpdated(boolean updated) {
      if (updated) {
        update(pixScale, derivePixScale());
        notifyUpdateListeners();
      }
      return updated;
    }

    /**
     * Updates the user scale with the current global user scale if necessary.
     *
     * @return whether any of the scale factors has been updated
     */
    public boolean update() {
      return onUpdated(update(usrScale, scale(1f)));
    }

    /**
     * Updates the provided scale if necessary (system scale is ignored)
     *
     * @param scale the new scale
     * @return whether the scale factor has been updated
     */
    public boolean update(@NotNull Scale scale) {
      boolean updated = false;
      switch (scale.type) {
        case USR_SCALE: updated = update(usrScale, scale.value); break;
        case SYS_SCALE: break;
        case OBJ_SCALE: updated = update(objScale, scale.value); break;
        case PIX_SCALE: break;
      }
      return onUpdated(updated);
    }

    /**
     * Updates the context with the state of the provided one.
     *
     * @param ctx the new context
     * @return whether any of the scale factors has been updated
     */
    public boolean update(@Nullable BaseScaleContext ctx) {
      if (ctx == null) return update();
      return onUpdated(updateAll(ctx));
    }

    protected <T extends BaseScaleContext> boolean updateAll(@NotNull T ctx) {
      boolean updated = update(usrScale, ctx.usrScale.value);
      return update(objScale, ctx.objScale.value) || updated;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof BaseScaleContext)) return false;

      BaseScaleContext that = (BaseScaleContext)obj;
      return that.usrScale.value == usrScale.value &&
             that.objScale.value == objScale.value;
    }

    /**
     * Clears the links.
     */
    public void dispose() {
      listeners = null;
    }

    /**
     * A context update listener. Used to listen to possible external context updates.
     */
    public interface UpdateListener {
      void contextUpdated();
    }

    public void addUpdateListener(UpdateListener l) {
      if (listeners == null) listeners = new ArrayList<UpdateListener>(1);
      listeners.add(l);
    }

    public void removeUpdateListener(UpdateListener l) {
      if (listeners != null) listeners.remove(l);
    }

    protected void notifyUpdateListeners() {
      if (listeners == null) return;
      for (UpdateListener l : listeners) {
        l.contextUpdated();
      }
    }

    protected boolean update(@NotNull Scale scale, double value) {
      Scale newScale = scale.newOrThis(value);
      if (newScale == scale) return false;
      switch (scale.type) {
        case USR_SCALE: usrScale = newScale; break;
        case OBJ_SCALE: objScale = newScale; break;
        case PIX_SCALE: pixScale = newScale; break;
      }
      return true;
    }
  }

  /**
   * Extends {@link BaseScaleContext} with the system scale, and is thus used for raster-based painting.
   * The context is created via a context provider. If the provider is {@link Component}, the context's
   * system scale can be updated via a call to {@link #update()}, reflecting the current component's
   * system scale (which may change as the component moves b/w devices).
   *
   * @see ScaleContextAware
   * @author tav
   */
  public static class ScaleContext extends BaseScaleContext {
    protected Scale sysScale = SYS_SCALE.of(sysScale());

    @Nullable
    private WeakReference<Component> compRef;

    private ScaleContext() {
      update(pixScale, derivePixScale());
    }

    private ScaleContext(Scale scale) {
      switch (scale.type) {
        case USR_SCALE: update(usrScale, scale.value); break;
        case SYS_SCALE: update(sysScale, scale.value); break;
        case OBJ_SCALE: update(objScale, scale.value); break;
        case PIX_SCALE: break;
      }
      update(pixScale, derivePixScale());
    }

    /**
     * Creates a context based on the comp's system scale and sticks to it via the {@link #update()} method.
     */
    public static ScaleContext create(@NotNull Component comp) {
      final ScaleContext ctx = new ScaleContext(SYS_SCALE.of(sysScale(comp)));
      ctx.compRef = new WeakReference<Component>(comp);
      return ctx;
    }

    /**
     * Creates a context based on the gc's system scale
     */
    public static ScaleContext create(GraphicsConfiguration gc) {
      return new ScaleContext(SYS_SCALE.of(sysScale(gc)));
    }

    /**
     * Creates a context based on the g's system scale
     */
    public static ScaleContext create(Graphics2D g) {
      return new ScaleContext(SYS_SCALE.of(sysScale(g)));
    }

    /**
     * Creates a context with the provided scale
     */
    public static ScaleContext create(@NotNull Scale scale) {
      return new ScaleContext(scale);
    }

    /**
     * Creates a context with the provided scale factors
     */
    public static ScaleContext create(@NotNull Scale... scales) {
      ScaleContext ctx = create();
      for (Scale s : scales) ctx.update(s);
      return ctx;
    }

    /**
     * Creates a default context with the default screen scale and the current user scale
     */
    public static ScaleContext create() {
      return new ScaleContext();
    }

    @Override
    protected double derivePixScale() {
      return UIUtil.isJreHiDPIEnabled() ? sysScale.value * super.derivePixScale() : super.derivePixScale();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getScale(ScaleType type) {
      if (type == SYS_SCALE) return sysScale.value;
      return super.getScale(type);
    }

    /**
     * {@inheritDoc}
     * Also updates the system scale (if the context was created from Component) if necessary.
     */
    @Override
    public boolean update() {
      boolean updated = update(usrScale, scale(1f));
      if (compRef != null) {
        Component comp = compRef.get();
        if (comp != null) updated = update(sysScale, sysScale(comp)) || updated;
      }
      return onUpdated(updated);
    }

    /**
     * {@inheritDoc}
     * Also includes the system scale.
     */
    @Override
    public boolean update(@NotNull Scale scale) {
      if (scale.type == SYS_SCALE) return onUpdated(update(sysScale, scale.value));
      return super.update(scale);
    }

    @Override
    protected <T extends BaseScaleContext> boolean updateAll(@NotNull T ctx) {
      boolean updated = super.updateAll(ctx);
      if (!(ctx instanceof ScaleContext)) return updated;
      ScaleContext context = (ScaleContext)ctx;

      if (compRef != null) compRef.clear();
      compRef = context.compRef;

      return update(sysScale, context.sysScale.value) || updated;
    }

    @Override
    protected boolean update(@NotNull Scale scale, double value) {
      if (scale.type == SYS_SCALE) {
        Scale newScale = scale.newOrThis(value);
        if (newScale == scale) return false;
        sysScale = newScale;
        return true;
      }
      return super.update(scale, value);
    }

    @Override
    public boolean equals(Object obj) {
      if (super.equals(obj) && obj instanceof ScaleContext) {
        ScaleContext that = (ScaleContext)obj;
        return that.sysScale.value == sysScale.value;
      }
      return false;
    }

    @Override
    public void dispose() {
      super.dispose();
      if (compRef != null) {
        compRef.clear();
      }
    }
  }

  /**
   * Provides ScaleContext awareness of a UI object.
   *
   * @see ScaleContextSupport
   * @author tav
   */
  public interface ScaleContextAware<T extends BaseScaleContext> {
    /**
     * @return the scale context
     */
    @NotNull T getScaleContext();

    /**
     * Updates the current context with the state of the provided context.
     * If {@code ctx} is null, then updates the current context via {@link ScaleContext#update()}
     * and returns the result.
     *
     * @param ctx the new scale context
     * @return whether any of the scale factors has been updated
     */
    boolean updateScaleContext(@Nullable T ctx);

    /**
     * @return the scale of the provided type from the context
     */
    double getScale(ScaleType type);

    /**
     * Updates the provided scale in the context
     *
     * @return whether the provided scale has been changed
     */
    boolean updateScale(Scale scale);
  }

  public static class ScaleContextSupport<T extends BaseScaleContext> implements ScaleContextAware<T> {
    @NotNull
    private final T myScaleContext;

    public ScaleContextSupport(@NotNull T ctx) {
      myScaleContext = ctx;
    }

    @NotNull
    @Override
    public T getScaleContext() {
      return myScaleContext;
    }

    @Override
    public boolean updateScaleContext(@Nullable T ctx) {
      return myScaleContext.update(ctx);
    }

    @Override
    public double getScale(ScaleType type) {
      return getScaleContext().getScale(type);
    }

    @Override
    public boolean updateScale(Scale scale) {
      return getScaleContext().update(scale);
    }
  }

  /**
   * A {@link BaseScaleContext} aware Icon, assuming vector-based painting, system scale independent.
   *
   * @author tav
   */
  public abstract static class JBIcon extends ScaleContextSupport<BaseScaleContext> implements Icon {
    private final Scaler myScaler = new Scaler() {
      @Override
      protected double currentScale() {
        if (autoUpdateScaleContext) getScaleContext().update();
        return getScale(USR_SCALE);
      }
    };
    private boolean autoUpdateScaleContext = true;

    protected JBIcon() {
      super(BaseScaleContext.create());
    }

    protected JBIcon(JBIcon icon) {
      this();
      updateScaleContext(icon.getScaleContext());
      myScaler.update(icon.myScaler);
      autoUpdateScaleContext = icon.autoUpdateScaleContext;
    }

    protected boolean isIconPreScaled() {
      return myScaler.isPreScaled();
    }

    protected void setIconPreScaled(boolean preScaled) {
      myScaler.setPreScaled(preScaled);
    }

    @NotNull
    public JBIcon withIconPreScaled(boolean preScaled) {
      setIconPreScaled(preScaled);
      return this;
    }

    /**
     * See {@link Scaler#scaleVal(double)}
     */
    protected double scaleVal(double value) {
      return myScaler.scaleVal(value);
    }

    /**
     * Sets whether the scale context should be auto-updated by the {@link Scaler}.
     * This ensures that {@link #scaleVal(double)} always uses up-to-date scale.
     * This is useful when the icon doesn't need to recalculate its internal sizes
     * on the scale context update and so it doesn't need the result of the update
     * and/or it doesn't listen for updates. Otherwise, the value should be set to
     * false and the scale context should be updated manually.
     * <p>
     * By default the value is true.
     */
    protected void setAutoUpdateScaleContext(boolean autoUpdate) {
      autoUpdateScaleContext = autoUpdate;
    }

    @Override
    public String toString() {
      return getClass().getName() + " " + getIconWidth() + "x" + getIconHeight();
    }
  }

  /**
   * A {@link JBIcon} implementing {@link ScalableIcon}
   *
   * @author tav
   */
  public abstract static class ScalableJBIcon extends JBIcon implements ScalableIcon {
    protected ScalableJBIcon() {}

    protected ScalableJBIcon(ScalableJBIcon icon) {
      super(icon);
    }

    @Override
    public float getScale() {
      return (float)getScale(OBJ_SCALE); // todo: float -> double
    }

    @Override
    public Icon scale(float scale) {
      updateScale(OBJ_SCALE.of(scale));
      return this;
    }

    /**
     * An equivalent of scaleVal(value, PIX_SCALE)
     */
    @Override
    protected double scaleVal(double value) {
      return scaleVal(value, PIX_SCALE);
    }

    /**
     * Updates the context and scales the provided value according to the provided type
     */
    protected double scaleVal(double value, ScaleType type) {
      switch (type) {
        case USR_SCALE: return super.scaleVal(value);
        case SYS_SCALE: return value * getScale(SYS_SCALE);
        case OBJ_SCALE: return value * getScale(OBJ_SCALE);
        case PIX_SCALE: return super.scaleVal(value * getScale(OBJ_SCALE));
      }
      return value; // unreachable
    }
  }

  /**
   * A {@link ScalableJBIcon} providing an immutable caching implementation of the {@link ScalableIcon#scale(float)} method.
   *
   * @author tav
   * @author Aleksey Pivovarov
   */
  public abstract static class CachingScalableJBIcon<T extends CachingScalableJBIcon> extends ScalableJBIcon {
    private CachingScalableJBIcon myScaledIconCache;

    protected CachingScalableJBIcon() {}

    protected CachingScalableJBIcon(CachingScalableJBIcon icon) {
      super(icon);
    }

    /**
     * @return a new scaled copy of this icon, or the cached instance of the provided scale
     */
    @Override
    public Icon scale(float scale) {
      if (scale == getScale()) return this;

      if (myScaledIconCache == null || myScaledIconCache.getScale() != scale) {
        myScaledIconCache = copy();
        myScaledIconCache.updateScale(OBJ_SCALE.of(scale));
      }
      return myScaledIconCache;
    }

    /**
     * @return a copy of this icon instance
     */
    @NotNull
    protected abstract T copy();
  }

  /**
   * A {@link ScaleContext} aware Icon, assuming raster-based painting, system scale dependant.
   *
   * @author tav
   */
  public abstract static class RasterJBIcon extends ScaleContextSupport<ScaleContext> implements Icon {
    public RasterJBIcon() {
      super(ScaleContext.create());
    }
  }
}
