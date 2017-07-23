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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.SystemProperties;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.image.ImageObserver;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
public class JBUI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.JBUI");

  public static final String USER_SCALE_FACTOR_PROPERTY = "JBUI.userScaleFactor";

  private static final PropertyChangeSupport PCS = new PropertyChangeSupport(new JBUI());

  /**
   * The IDE supports two different HiDPI modes:
   *
   * 1) IDE-managed HiDPI mode.
   *
   * Supported for backward compatibility until complete transition to the JRE-managed HiDPI mode happens.
   * In this mode there's a single coordinate space and the whole UI is scaled by the IDE guided by the
   * user scale factor ({@link #USR}).
   *
   * 2) JRE-managed HiDPI mode.
   *
   * In this mode the JRE scales graphics prior to drawing it on the device. So, there're two coordinate
   * spaces: the user space and the device space. The system scale factor ({@link #SYS}) defines the
   * transform b/w the spaces. The UI size metrics (windows, controls, fonts height) are in the user
   * coordinate space. Though, the raster images should be aware of the device scale in order to meet
   * HiDPI. (For instance, JRE on a Mac Retina monitor device works in the JRE-managed HiDPI mode,
   * transforming graphics to the double-scaled device coordinate space)
   *
   * The IDE operates the scale factors of the following types:
   *
   * 1) The user scale factor: {@link #USR}
   * 2) The system (monitor device) scale factor: {@link #SYS}
   * 3) The pixel scale factor: {@link #PIX}
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
    USR,
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
    SYS,
    /**
     * The pixel scale factor "combines" both the user and the system scale factors and defines the
     * effective scale of the whole UI.
     *
     * For instance, on Mac Retina monitor (JRE-managed HiDPI) in the Presentation mode (which, say,
     * doubles the UI scale) the pixel scale would equal 4.0. The value is the product of the user
     * scale 2.0 and the system scale 2.0. In the IDE-managed HiDPI mode, the pixel scale always equals
     * the user scale.
     *
     * @see #pixScale()
     * @see #pixScale(GraphicsConfiguration)
     * @see #pixScale(Graphics2D)
     * @see #pixScale(Component)
     * @see #pixScale(GraphicsConfiguration, float)
     * @see #pixScale(float)
     */
    PIX;

    private final Key<Float> key = Key.create(name());
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
   * @see #addPropertyChangeListener(String, PropertyChangeListener)
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
   * Returns the system scale factor based on the JBUIScaleUpdatable.
   * In the IDE-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(JBUIScaleUpdatable updatable) {
    if (UIUtil.isJreHiDPIEnabled() && updatable != null) {
      return updatable.getJBUIScale(ScaleType.SYS);
    }
    return sysScale();
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
   * Returns the pixel scale factor based on the JBUIScaleUpdatable.
   */
  public static float pixScale(@NotNull JBUIScaleUpdatable updatableable) {
    return UIUtil.isJreHiDPIEnabled() ? sysScale(updatableable) * updatableable.getJBUIScale(ScaleType.USR) : updatableable.getJBUIScale(ScaleType.USR);
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

    if (scale < 1.25f) scale = 1.0f;
    else if (scale < 1.5f) scale = 1.25f;
    else if (scale < 1.75f) scale = 1.5f;
    else if (scale < 2f) scale = 1.75f;
    else scale = 2.0f;

    if (SystemInfo.isLinux && scale == 1.25f) {
      //Default UI font size for Unity and Gnome is 15. Scaling factor 1.25f works badly on Linux
      scale = 1f;
    }
    if (userScaleFactor == scale) {
      return;
    }
    setUserScaleFactorProperty(scale);
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
   * @param fontSize
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
      final JBDimension jbSize = (JBDimension)size;
      if (jbSize.myJBUIScale == scale(1f)) {
        return jbSize;
      }
      final JBDimension newSize = new JBDimension((int)(jbSize.width / jbSize.myJBUIScale), (int)(jbSize.height / jbSize.myJBUIScale));
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

  public static <T extends JBIcon> T scale(T icon) {
    return (T)icon.withJBUIPreScaled(false);
  }

  public static JBDimension emptySize() {
    return new JBDimension(0, 0);
  }

  public static JBInsets insets(Insets insets) {
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
   * Returns whether the {@link ScaleType#USR} scale factor assumes HiDPI-awareness.
   * An equivalent of {@code isHiDPI(scale(1f))}
   */
  public static boolean isUsrHiDPI() {
      return isHiDPI(scale(1f));
  }

  /**
   * Returns whether the {@link ScaleType#PIX} scale factor assumes HiDPI-awareness in the provided graphics config.
   * An equivalent of {@code isHiDPI(pixScale(gc))}
   */
  public static boolean isPixHiDPI(@Nullable GraphicsConfiguration gc) {
    return isHiDPI(pixScale(gc));
  }

  /**
   * Returns whether the {@link ScaleType#PIX} scale factor assumes HiDPI-awareness in the provided graphics.
   * An equivalent of {@code isHiDPI(pixScale(g))}
   */
  public static boolean isPixHiDPI(@Nullable Graphics2D g) {
    return isHiDPI(pixScale(g));
  }

  /**
   * Returns whether the {@link ScaleType#PIX} scale factor assumes HiDPI-awareness in the provided component's device.
   * An equivalent of {@code isHiDPI(pixScale(comp))}
   */
  public static boolean isPixHiDPI(@Nullable Component comp) {
    return isHiDPI(pixScale(comp));
  }

  /**
   * Returns whether the provided scale assumes HiDPI-awareness.
   */
  public static boolean isHiDPI(float scale) {
    return scale > 1f;
  }

  public static class Fonts {
    public static JBFont label() {
      return JBFont.create(UIManager.getFont("Label.font"), false);
    }

    public static JBFont label(float size) {
      return label().deriveFont(scale(size));
    }

    public static JBFont smallFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }

    public static JBFont miniFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.MINI));
    }

    public static JBFont create(String fontFamily, int size) {
      return JBFont.create(new Font(fontFamily, Font.PLAIN, size));
    }
  }

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
   * An Icon dynamically sticking to JBUI.scale to meet HiDPI.
   *
   * @author tav
   */
  public abstract static class JBIcon implements Icon {
    private float myInitialJBUIScale = currentJBUIScale();

    protected JBIcon() {}

    protected JBIcon(JBIcon icon) {
      myInitialJBUIScale = icon.myInitialJBUIScale;
    }

    static float currentJBUIScale() {
      // We don't JBUI-scale images in JRE-managed HiDPI mode, see comments in ImageLoader.loadFromUrl(..)
      // So, make icons JBUI-scale conformant.
      return UIUtil.isJreHiDPIEnabled() ? 1f : scale(1f);
    }

    /**
     * @return the scale factor aligning the icon size metrics to conform to up-to-date JBUI.scale
     */
    private float getAligningScale() {
      return currentJBUIScale() / myInitialJBUIScale;
    }

    /**
     * @return whether the icon size metrics are pre-scaled or not
     */
    protected boolean isJBUIPreScaled() {
      return myInitialJBUIScale != 1f;
    }

    /**
     * Sets the icon size metrics to {@code preScaled}
     */
    protected void setJBUIPreScaled(boolean preScaled) {
      myInitialJBUIScale = preScaled ? currentJBUIScale() : 1f;
    }

    /**
     * Sets the icon size metrics to {@code preScaled}
     *
     * @return the icon (this or new instance) with size metrics set to {@code preScaled}
     */
    public JBIcon withJBUIPreScaled(boolean preScaled) {
      setJBUIPreScaled(preScaled);
      return this;
    }

    /**
     * Scales the value to conform to JBUI.scale
     */
    public int scaleVal(int value) {
      return (int)scaleVal((float)value);
    }

    /**
     * Scales the value to conform to JBUI.scale
     */
    public float scaleVal(float value) {
      return value * getAligningScale();
    }

    @Override
    public String toString() {
      return getClass().getName() + " " + getIconWidth() + "x" + getIconHeight();
    }
  }

  /**
   * An Icon supporting both JBUI.scale & instance scale factors.
   *
   * @author tav
   */
  public abstract static class ScalableJBIcon extends JBIcon implements ScalableIcon {
    private float myScale = 1f;

    protected ScalableJBIcon() {}

    protected ScalableJBIcon(ScalableJBIcon icon) {
      super(icon);
      myScale = icon.myScale;
    }

    public enum Scale {
      JBUI,       // JBIcon's JBUI scale
      INSTANCE,   // ScalableIcon's instance scale
      EFFECTIVE   // effective scale
    }

    @Override
    public float getScale() {
      return myScale;
    }

    protected void setScale(float scale) {
      myScale = scale;
    }

    @Override
    public int scaleVal(int value) {
      return scaleVal(value, Scale.EFFECTIVE);
    }

    @Override
    public float scaleVal(float value) {
      return scaleVal(value, Scale.EFFECTIVE);
    }

    public int scaleVal(int value, Scale type) {
      return (int)scaleVal((float)value, type);
    }

    public float scaleVal(float value, Scale type) {
      switch (type) {
        case JBUI:
          return super.scaleVal(value);
        case INSTANCE:
          return value * myScale;
        case EFFECTIVE:
        default:
          return super.scaleVal(value * myScale);
      }
    }

    /**
     * Scales the value in the icon's scale.
     */
    public static int scaleVal(Icon icon, int value, Scale type) {
      return (int)scaleVal(icon, (float)value, type);
    }

    /**
     * Scales the value in the icon's scale.
     */
    public static float scaleVal(Icon icon, float value, Scale type) {
      if (icon instanceof ScalableJBIcon) {
        return ((ScalableJBIcon)icon).scaleVal(value, type);
      }
      return value;
    }
  }

  /**
   * A ScalableJBIcon providing an immutable caching implementation of the {@link #scale(float)} method.
   *
   * @author tav
   * @author Aleksey Pivovarov
   */
  public abstract static class CachingScalableJBIcon<T extends CachingScalableJBIcon> extends ScalableJBIcon {
    private CachingScalableJBIcon myScaledIconCache;

    protected CachingScalableJBIcon() {}

    protected CachingScalableJBIcon(CachingScalableJBIcon icon) {
      super(icon);
      myScaledIconCache = null;
    }

    /**
     * @return a new scaled copy of this icon, or the cached instance of the provided scale
     */
    @Override
    public Icon scale(float scale) {
      if (scale == getScale()) return this;

      if (myScaledIconCache == null || myScaledIconCache.getScale() != scale) {
        myScaledIconCache = copy();
        myScaledIconCache.setScale(scale);
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
   * An interface to update JBUI scale factors.
   */
  public interface JBUIScaleUpdatable {
    /**
     * Checks if tracked user scale should be updated and updates it.
     *
     * @return true if tracked user scale was updated
     */
    boolean updateJBUIScale();

    /**
     * Updates all the scale factors based on the provided graphics.
     *
     * @param g the graphics, if null defaults to {@link #updateJBUIScale()}
     * @return true if any of the tracked scale factors was updated
     */
    boolean updateJBUIScale(@Nullable Graphics2D g);

    /**
     * Updates all the scale factors based on the provided graphics config.
     *
     * @param gc the graphics config, if null defaults to {@link #updateJBUIScale()}
     * @return true if any of the tracked scale factors was updated
     */
    boolean updateJBUIScale(@Nullable GraphicsConfiguration gc);

    /**
     * @return true if tracked user scale should be updated
     */
    boolean needUpdateJBUIScale();

    /**
     * @param g the graphics, if null defaults to {@link #needUpdateJBUIScale()}
     * @return true if any of the tracked scale factors should be updated
     */
    boolean needUpdateJBUIScale(@Nullable Graphics2D g);

    /**
     * @param gc the graphics config, if null defaults to {@link #needUpdateJBUIScale()}
     * @return true if any of the tracked scale factors should be updated
     */
    boolean needUpdateJBUIScale(@Nullable GraphicsConfiguration gc);

    /**
     * @param type the type of the scale
     * @return the tracked scale factor value of the type
     */
    float getJBUIScale(ScaleType type);
  }

  /**
   * A helper class to update JBUI scale factors.
   */
  private static class JBUIScaleUpdater implements JBUIScaleUpdatable {
    // ScaleType.USR - tracked
    // ScaleType.SYS - tracked
    // ScaleType.PIX - derived
    KeyFMap myTrackedJBUIScale = KeyFMap.EMPTY_MAP;

    {
      put(ScaleType.USR.key, JBIcon.currentJBUIScale());
      put(ScaleType.SYS.key, sysScale());
    }

    private void put(Key<Float> key, Float value) {
      myTrackedJBUIScale = myTrackedJBUIScale.plus(key, value);
    }

    @Override
    public boolean updateJBUIScale() {
      return updateJBUIScale(JBIcon.currentJBUIScale(), ScaleType.USR);
    }

    private boolean updateJBUIScale(float scale, ScaleType type) {
      if (needUpdateJBUIScale(scale, type)) {
        put(type.key, scale);
        return true;
      }
      return false;
    }

    @Override
    public boolean updateJBUIScale(@Nullable Graphics2D g) {
      boolean res = updateJBUIScale();
      if (g != null) res = res || updateJBUIScale(sysScale(g), ScaleType.SYS);
      return res;
    }

    @Override
    public boolean updateJBUIScale(@Nullable GraphicsConfiguration gc) {
      boolean res = updateJBUIScale();
      if (gc != null) res = res || updateJBUIScale(sysScale(gc), ScaleType.SYS);
      return res;
    }

    @Override
    public boolean needUpdateJBUIScale() {
      return needUpdateJBUIScale(JBIcon.currentJBUIScale(), ScaleType.USR);
    }

    private boolean needUpdateJBUIScale(float scale, ScaleType type) {
      return getJBUIScale(type) != scale;
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable Graphics2D g) {
      return needUpdateJBUIScale() || g != null && needUpdateJBUIScale(sysScale(g), ScaleType.SYS);
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable GraphicsConfiguration gc) {
      return needUpdateJBUIScale() || gc != null && needUpdateJBUIScale(sysScale(gc), ScaleType.SYS);
    }

    @Override
    public float getJBUIScale(ScaleType type) {
      return type == ScaleType.PIX ?
             pixScale(this) : // derive
             myTrackedJBUIScale.get(type.key);
    }
  }

  /**
   * A JBIcon lazily updating JBUI scale factors change.
   *
   * @author tav
   */
  public abstract static class UpdatingJBIcon extends JBIcon implements JBUIScaleUpdatable {
    private final JBUIScaleUpdater myJBUIScaleDelegate = new JBUIScaleUpdater();

    @Override
    public boolean updateJBUIScale() {
      return myJBUIScaleDelegate.updateJBUIScale();
    }

    @Override
    public boolean updateJBUIScale(@Nullable Graphics2D g) {
      return myJBUIScaleDelegate.updateJBUIScale(g);
    }

    @Override
    public boolean updateJBUIScale(@Nullable GraphicsConfiguration gc) {
      return myJBUIScaleDelegate.updateJBUIScale(gc);
    }

    @Override
    public boolean needUpdateJBUIScale() {
      return myJBUIScaleDelegate.needUpdateJBUIScale();
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable Graphics2D g) {
      return myJBUIScaleDelegate.needUpdateJBUIScale(g);
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable GraphicsConfiguration gc) {
      return myJBUIScaleDelegate.needUpdateJBUIScale(gc);
    }

    @Override
    public float getJBUIScale(ScaleType type) {
      return myJBUIScaleDelegate.getJBUIScale(type);
    }
  }

  /**
   * A ScalableJBIcon lazily updating JBUI scale factors change.
   *
   * @author tav
   */
  public abstract static class UpdatingScalableJBIcon<T extends UpdatingScalableJBIcon>
    extends CachingScalableJBIcon<T> implements JBUIScaleUpdatable {

    private final JBUIScaleUpdater myJBUIScaleDelegate = new JBUIScaleUpdater();

    protected UpdatingScalableJBIcon() {}

    protected UpdatingScalableJBIcon(UpdatingScalableJBIcon icon) {
      super(icon);
    }

    @Override
    public boolean updateJBUIScale() {
      return myJBUIScaleDelegate.updateJBUIScale();
    }

    @Override
    public boolean updateJBUIScale(@Nullable Graphics2D g) {
      return myJBUIScaleDelegate.updateJBUIScale(g);
    }

    @Override
    public boolean updateJBUIScale(@Nullable GraphicsConfiguration gc) {
      return myJBUIScaleDelegate.updateJBUIScale(gc);
    }

    @Override
    public boolean needUpdateJBUIScale() {
      return myJBUIScaleDelegate.needUpdateJBUIScale();
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable Graphics2D g) {
      return myJBUIScaleDelegate.needUpdateJBUIScale(g);
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable GraphicsConfiguration gc) {
      return myJBUIScaleDelegate.needUpdateJBUIScale(gc);
    }

    @Override
    public float getJBUIScale(ScaleType type) {
      return myJBUIScaleDelegate.getJBUIScale(type);
    }
  }
}
