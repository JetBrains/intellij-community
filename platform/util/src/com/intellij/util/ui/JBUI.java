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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
public class JBUI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.JBUI");

  public static final String USER_SCALE_FACTOR_PROPERTY = "JBUI.userScaleFactor";

  private static final PropertyChangeSupport PCS = new PropertyChangeSupport(new JBUI());

  /**
   * The HiDPI scale factor can be one of the following types:
   *
   * 1) The user space scale factor.
   * 2) The device space scale factor (or the pixel scale).
   * 3) The system (monitor device) scale factor.
   *
   * The IDE supports two different HiDPI modes:
   *
   * 1) App-managed HiDPI mode. In this mode the user space matches the device space.
   * All the UI is scaled by the IDE. The user space scale equals the pixel scale.
   * Also, by default the user space scale equals the system scale (so all the three
   * scale factors are equal), but it may exceed the system scale if a user changes
   * the user space scale factor via IDE Settings.
   *
   * 2) JDK-managed HiDPI mode. In this mode the device space may exceed the user space.
   * All the vector graphics (including fonts) are transformed from the user space
   * to the device space by the JDK, transparently to the IDE. All the raster images,
   * though, should be handled specially by the IDE and presented to the JDK as
   * HiDPI-aware images. The user space scale & the pixel scale are treated differently
   * in this mode. Namely, the pixel scale is the product of the user space scale and
   * the system scale. By default, the user space scale is set to 1.0 and the pixel scale
   * equals the system scale. The user space scale is still managed by the IDE and can
   * be changed by a user via IDE Settings.
   *
   * @see UIUtil#isJDKManagedHiDPI()
   * @see UIUtil#isJDKManagedHiDPIScreen()
   * @see UIUtil#isJDKManagedHiDPIScreen(Graphics2D)
   */
  public enum ScaleType {
    USR,
    PIX,
    SYS
  }

  /**
   * The system scale factor, corresponding to the default monitor device.
   */
  public static final Float SYSTEM_SCALE_FACTOR = sysScale();

  /**
   * The user space scale factor.
   */
  private static float userScaleFactor;

  static {
    setUserScaleFactor(UIUtil.isJDKManagedHiDPI() ? 1f : SYSTEM_SCALE_FACTOR);
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

    if (UIUtil.isJDKManagedHiDPI()) {
      GraphicsDevice gd = null;
      try {
        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
      } catch (HeadlessException ignore) {}
      if (gd != null) {
        return sysScale(gd);
      }
      return 1.0f;
    }

    if (SystemProperties.has("hidpi") && !SystemProperties.is("hidpi")) {
      return 1.0f;
    }

    UIUtil.initSystemFontData();
    Pair<String, Integer> fdata = UIUtil.getSystemFontData();

    int size;
    if (fdata != null) {
      size = fdata.getSecond();
    } else {
      size = Fonts.label().getSize();
    }
    return size / UIUtil.DEF_SYSTEM_FONT_SIZE;
  }

  /**
   * Returns the system scale factor based on the JBUIScaleTrackable.
   * In JDK-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(JBUIScaleTrackable trackable) {
    if (UIUtil.isJDKManagedHiDPI() && trackable != null) {
      return trackable.getJBUIScale(ScaleType.SYS);
    }
    return sysScale();
  }

  /**
   * Returns the system scale factor, corresponding to the provided graphics device.
   * In JDK-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(Graphics2D g) {
    if (UIUtil.isJDKManagedHiDPI() && g != null) {
      return sysScale(g.getDeviceConfiguration().getDevice());
    }
    return sysScale();
  }

  /**
   * Returns the system scale factor, corresponding to the provided device.
   * In JDK-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(GraphicsDevice gd) {
    if (UIUtil.isJDKManagedHiDPI() && gd != null) {
      return (float)gd.getDefaultConfiguration().getDefaultTransform().getScaleX();
    }
    return sysScale();
  }

  /**
   * Returns the pixel scale factor, corresponding to the default monitor device.
   */
  public static float pixScale() {
    return UIUtil.isJDKManagedHiDPI() ? sysScale() * scale(1f) : scale(1f);
  }

  /**
   * Returns "f" scaled by pixScale().
   */
  public static float pixScale(float f) {
    return pixScale() * f;
  }

  /**
   * Returns the pixel scale factor based on the JBUIScaleTrackable.
   */
  public static float pixScale(@NotNull JBUIScaleTrackable trackable) {
    return UIUtil.isJDKManagedHiDPI() ? sysScale(trackable) * trackable.getJBUIScale(ScaleType.USR) : trackable.getJBUIScale(ScaleType.USR);
  }

  /**
   * Returns the pixel scale factor, corresponding to the provided graphics device.
   */
  public static float pixScale(Graphics2D g) {
    return UIUtil.isJDKManagedHiDPI() ? sysScale(g) * scale(1f) : scale(1f);
  }

  private static void setUserScaleFactorProperty(float scale) {
    PCS.firePropertyChange(USER_SCALE_FACTOR_PROPERTY, userScaleFactor, userScaleFactor = scale);
    LOG.info("UI scale factor: " + userScaleFactor);
  }

  /**
   * Sets the user scale factor.
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

  public static boolean isHiDPI() {
    return pixScale(1f) > 1.0f;
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

  public static class ComboBox {
    /**
     *        JComboBox<String> comboBox = new ComboBox<>(new String[] {"First", "Second", "Third"});
     *        comboBox.setEditable(true);
     *        comboBox.setEditor(JBUI.ComboBox.compositeComboboxEditor(new JTextField(), new JLabel(AllIcons.Icon_CE)));
     *
     *        @param components an array of JComponent objects. The first one is the editable text component.
     */
/*    public static ComboBoxCompositeEditor compositeComboboxEditor  (JComponent ... components) {
      return new ComboBoxCompositeEditor(components);
    }*/
  }

  /**
   * An Icon dynamically sticking to JBUI.scale to meet HiDPI.
   *
   * @author tav
   */
  public static abstract class JBIcon implements Icon {
    private float myInitialJBUIScale = currentJBUIScale();

    protected JBIcon() {}

    protected JBIcon(JBIcon icon) {
      myInitialJBUIScale = icon.myInitialJBUIScale;
    }

    static float currentJBUIScale() {
      // We don't JBUI-scale images in JDK-managed HiDPI mode, see comments in ImageLoader.loadFromUrl(..)
      // So, make icons JBUI-scale conformant.
      return UIUtil.isJDKManagedHiDPI() ? 1f : scale(1f);
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
  }

  /**
   * An Icon supporting both JBUI.scale & instance scale factors.
   *
   * @author tav
   */
  public static abstract class ScalableJBIcon extends JBIcon implements ScalableIcon {
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
  public static abstract class CachingScalableJBIcon<T extends CachingScalableJBIcon> extends ScalableJBIcon {
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
   * An interface to track JBUI scale factors.
   */
  public interface JBUIScaleTrackable {
    /**
     * Checks if tracked user scale should be updated and updates it.
     *
     * @return true if tracked user scale was updated
     */
    boolean updateJBUIScale();

    /**
     * Updates all the scale factors based on the provided graphics (device).
     *
     * @param g the graphics, if null defaults to {@link #updateJBUIScale()}
     * @return true if any of the tracked scale factors was updated
     */
    boolean updateJBUIScale(@Nullable Graphics2D g);

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
     * @param type the type of the scale
     * @return the tracked scale factor value of the type
     */
    float getJBUIScale(ScaleType type);
  }

  /**
   * A helper class to track JBUI scale factors.
   */
  private static class JBUIScaleTracker implements JBUIScaleTrackable {
    // ScaleType.USR - tracked
    // ScaleType.SYS - tracked
    // ScaleType.PIX - derived
    Map<ScaleType, Float> myTrackedJBUIScale = new HashMap<ScaleType, Float>();

    JBUIScaleTracker() {
      myTrackedJBUIScale.put(ScaleType.USR, JBIcon.currentJBUIScale());
      myTrackedJBUIScale.put(ScaleType.SYS, sysScale());
    }

    @Override
    public boolean updateJBUIScale() {
      return updateJBUIScale(JBIcon.currentJBUIScale(), ScaleType.USR);
    }

    private boolean updateJBUIScale(float scale, ScaleType type) {
      if (needUpdateJBUIScale(scale, type)) {
        myTrackedJBUIScale.put(type, scale);
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
    public boolean needUpdateJBUIScale() {
      return needUpdateJBUIScale(JBIcon.currentJBUIScale(), ScaleType.USR);
    }

    private boolean needUpdateJBUIScale(float scale, ScaleType type) {
      return getJBUIScale(type) != scale;
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable Graphics2D g) {
      return needUpdateJBUIScale() || (g != null && needUpdateJBUIScale(sysScale(g), ScaleType.SYS));
    }

    @Override
    public float getJBUIScale(ScaleType type) {
      if (type == ScaleType.PIX) {
        return pixScale(this); // derive
      }
      return myTrackedJBUIScale.get(type);
    }
  }

  /**
   * A JBIcon lazily tracking JBUI scale factors change.
   *
   * @author tav
   */
  public static abstract class AuxJBIcon extends JBIcon implements JBUIScaleTrackable {
    private JBUIScaleTracker myJBUIScaleDelegate = new JBUIScaleTracker();

    @Override
    public boolean updateJBUIScale() {
      return myJBUIScaleDelegate.updateJBUIScale();
    }

    @Override
    public boolean updateJBUIScale(@Nullable Graphics2D g) {
      return myJBUIScaleDelegate.updateJBUIScale(g);
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
    public float getJBUIScale(ScaleType type) {
      return myJBUIScaleDelegate.getJBUIScale(type);
    }
  }

  /**
   * A ScalableJBIcon lazily tracking JBUI scale factors change.
   *
   * @author tav
   */
  public static abstract class AuxScalableJBIcon extends CachingScalableJBIcon implements JBUIScaleTrackable {
    private JBUIScaleTracker myJBUIScaleDelegate = new JBUIScaleTracker();

    protected AuxScalableJBIcon() {}

    protected AuxScalableJBIcon(AuxScalableJBIcon icon) {
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
    public boolean needUpdateJBUIScale() {
      return myJBUIScaleDelegate.needUpdateJBUIScale();
    }

    @Override
    public boolean needUpdateJBUIScale(@Nullable Graphics2D g) {
      return myJBUIScaleDelegate.needUpdateJBUIScale(g);
    }

    @Override
    public float getJBUIScale(ScaleType type) {
      return myJBUIScaleDelegate.getJBUIScale(type);
    }
  }
}
