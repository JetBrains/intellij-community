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

/**
 * @author Konstantin Bulenkov
 */
public class JBUI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.JBUI");

  public static final String SCALE_FACTOR_PROPERTY = "JBUI.scale";

  private static final PropertyChangeSupport PCS = new PropertyChangeSupport(new JBUI());

  /**
   * A default system scale factor.
   */
  public static final float SYSTEM_DEF_SCALE = getSystemDefScale();

  private static float scaleFactor;

  static {
    setScaleFactor(SYSTEM_DEF_SCALE);
  }

  /**
   * Adds property change listener. Supported properties:
   * {@link #SCALE_FACTOR_PROPERTY}
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

  private static float getSystemDefScale() {
    if (SystemInfo.isMac) {
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

  private static void setScaleFactorProperty(float scale) {
    PCS.firePropertyChange(SCALE_FACTOR_PROPERTY, scaleFactor, scaleFactor = scale);
    LOG.info("UI scale factor: " + scaleFactor);
  }

  public static void setScaleFactor(float scale) {
    if (SystemProperties.has("hidpi") && !SystemProperties.is("hidpi")) {
      setScaleFactorProperty(1.0f);
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
    if (scaleFactor == scale) {
      return;
    }
    setScaleFactorProperty(scale);
  }

  public static int scale(int i) {
    return Math.round(scaleFactor * i);
  }

  public static int scaleFontSize(int fontSize) {
    if (scaleFactor == 1.25f) return (int)(fontSize * 1.34f);
    if (scaleFactor == 1.75f) return (int)(fontSize * 1.67f);
    return scale(fontSize);
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

  public static float scale(float f) {
    return f * scaleFactor;
  }

  public static JBInsets insets(Insets insets) {
    return JBInsets.create(insets);
  }

  public static boolean isHiDPI() {
    return scaleFactor > 1.0f;
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
      // We don't JBUI-scale images on Retina, see comments in ImageLoader.loadFromUrl(..)
      // So, make icons JBUI-scale conformant.
      return UIUtil.isRetina() ? 1f : scale(1f);
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
   * An Icon supporting both JBUI.scale & arbitrary scale factors.
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
      JBUI,       // JBIcon's scale
      ARBITRARY,  // ScalableIcon's scale
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
        case ARBITRARY:
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

  public interface AuxJBUIScale {
    /**
     * Checks if cached JBUI.scale should be updated and updates it.
     *
     * @return true if cached JBUI.scale was updated
     */
    boolean updateJBUIScale();

    /**
     * @return true if cached JBUI.scale should be updated
     */
    boolean needUpdateJBUIScale();
  }

  /**
   * A JBIcon caching JBUI.scale and allowing to lazily track its change.
   *
   * @author tav
   */
  public static abstract class AuxJBIcon extends JBIcon implements AuxJBUIScale {
    private float myCachedJBUIScale = currentJBUIScale();

    @Override
    public boolean updateJBUIScale() {
      if (needUpdateJBUIScale()) {
        myCachedJBUIScale = currentJBUIScale();
        return true;
      }
      return false;
    }

    @Override
    public boolean needUpdateJBUIScale() {
      return myCachedJBUIScale != currentJBUIScale();
    }
  }

  /**
   * A ScalableJBIcon caching JBUI.scale and allowing to lazily track its change.
   *
   * @author tav
   */
  public static abstract class AuxScalableJBIcon extends CachingScalableJBIcon implements AuxJBUIScale {
    private float myCachedJBUIScale = currentJBUIScale();

    protected AuxScalableJBIcon() {}

    protected AuxScalableJBIcon(AuxScalableJBIcon icon) {
      super(icon);
    }

    @Override
    public boolean updateJBUIScale() {
      if (needUpdateJBUIScale()) {
        myCachedJBUIScale = currentJBUIScale();
        return true;
      }
      return false;
    }

    @Override
    public boolean needUpdateJBUIScale() {
      return myCachedJBUIScale != currentJBUIScale();
    }
  }
}
