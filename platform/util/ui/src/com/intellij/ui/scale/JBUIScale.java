// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.util.LazyInitializer.MutableNotNullValue;
import com.intellij.util.LazyInitializer.NullableValue;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.DetectRetinaKit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author tav
 */
@SuppressWarnings("CodeBlock2Expr")
public final class JBUIScale {
  public static final boolean SCALE_VERBOSE = Boolean.getBoolean("ide.ui.scale.verbose");
  public static final String USER_SCALE_FACTOR_PROPERTY = "JBUIScale.userScaleFactor";

  private static final PropertyChangeSupport PROPERTY_CHANGE_SUPPORT = new PropertyChangeSupport(new JBUIScale());
  private static final float DISCRETE_SCALE_RESOLUTION = 0.25f;

  @SuppressWarnings("StaticNonFinalField")
  public static float DEF_SYSTEM_FONT_SIZE = 12f;

  public static void addUserScaleChangeListener(@NotNull PropertyChangeListener listener) {
    PROPERTY_CHANGE_SUPPORT.addPropertyChangeListener(USER_SCALE_FACTOR_PROPERTY, listener);
  }

  public static void removeUserScaleChangeListener(@NotNull PropertyChangeListener listener) {
    PROPERTY_CHANGE_SUPPORT.removePropertyChangeListener(listener);
  }

  private static final AtomicNotNullLazyValue<Pair<String, Integer>> systemFontData = AtomicNotNullLazyValue.createValue(() -> {
    // with JB Linux JDK the label font comes properly scaled based on Xft.dpi settings.
    Font font = UIManager.getFont("Label.font");
    if (SystemInfo.isMacOSElCapitan) {
      // text family should be used for relatively small sizes (<20pt), don't change to Display
      // see more about SF https://medium.com/@mach/the-secret-of-san-francisco-fonts-4b5295d9a745#.2ndr50z2v
      font = new Font(".SF NS Text", font.getStyle(), font.getSize());
    }

    Logger log = getLogger();
    boolean isScaleVerbose = SCALE_VERBOSE;
    if (isScaleVerbose) {
      log.info(String.format("Label font: %s, %d", font.getFontName(), font.getSize()));
    }

    if (SystemInfo.isLinux) {
      Object value = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI");
      if (isScaleVerbose) {
        log.info(String.format("gnome.Xft/DPI: %s", value));
      }
      if (value instanceof Integer) { // defined by JB JDK when the resource is available in the system
        // If the property is defined, then:
        // 1) it provides correct system scale
        // 2) the label font size is scaled
        int dpi = ((Integer)value).intValue() / 1024;
        if (dpi < 50) dpi = 50;
        float scale = JreHiDpiUtil.isJreHiDPIEnabled() ? 1f : discreteScale(dpi / 96f); // no scaling in JRE-HiDPI mode
        DEF_SYSTEM_FONT_SIZE = font.getSize() / scale; // derive actual system base font size
        if (isScaleVerbose) {
          log.info(String.format("DEF_SYSTEM_FONT_SIZE: %.2f", DEF_SYSTEM_FONT_SIZE));
        }
      }
      else if (!SystemInfo.isJetBrainsJvm) {
        // With Oracle JDK: derive scale from X server DPI, do not change DEF_SYSTEM_FONT_SIZE
        float size = DEF_SYSTEM_FONT_SIZE * getScreenScale();
        font = font.deriveFont(size);
        if (isScaleVerbose) {
          log.info(String.format("(Not-JB JRE) reset font size: %.2f", size));
        }
      }
    }
    else if (SystemInfo.isWindows) {
      //noinspection HardCodedStringLiteral
      @SuppressWarnings("SpellCheckingInspection")
      Font winFont = (Font)Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
      if (winFont != null) {
        font = winFont; // comes scaled
        if (isScaleVerbose) {
          log.info(String.format("Windows sys font: %s, %d", winFont.getFontName(), winFont.getSize()));
        }
      }
    }
    Pair<String, Integer> result = Pair.create(font.getName(), font.getSize());
    if (isScaleVerbose) {
      log.info(String.format("ourSystemFontData: %s, %d", result.first, result.second));
    }
    return result;
  });

  @ApiStatus.Internal
  public static final NullableValue<Float> DEBUG_USER_SCALE_FACTOR = new NullableValue<Float>() {
    @Nullable
    @Override
    public Float initialize() {
      String prop = System.getProperty("ide.ui.scale");
      if (prop != null) {
        try {
          return Float.parseFloat(prop);
        }
        catch (NumberFormatException e) {
          getLogger().error("ide.ui.scale system property is not a float value: " + prop);
        }
      }
      else if (Boolean.getBoolean("ide.ui.scale.override")) {
        return 1f;
      }
      return null;
    }
  };

  @NotNull
  // cannot be static because logging maybe not configured yet
  private static Logger getLogger() {
    return Logger.getInstance("#com.intellij.util.ui.JBUIScale");
  }

  /**
   * The system scale factor, corresponding to the default monitor device.
   */
  private static final MutableNotNullValue<Float> SYSTEM_SCALE_FACTOR = new MutableNotNullValue<>(() -> {
    if (!SystemProperties.getBooleanProperty("hidpi", true)) {
      return 1f;
    }

    if (JreHiDpiUtil.isJreHiDPIEnabled()) {
      GraphicsDevice gd = null;
      try {
        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
      }
      catch (HeadlessException ignore) {
      }
      if (gd != null && gd.getDefaultConfiguration() != null) {
        return sysScale(gd.getDefaultConfiguration());
      }
      return 1f;
    }

    float result = getFontScale(systemFontData.getValue().getSecond());
    getLogger().info("System scale factor: " + result + " (" + (JreHiDpiUtil.isJreHiDPIEnabled() ? "JRE" : "IDE") + "-managed HiDPI)");
    return result;
  });

  @TestOnly
  public static void setSystemScaleFactor(float sysScale) {
    SYSTEM_SCALE_FACTOR.set(sysScale);
  }

  /**
   * The user scale factor, see {@link ScaleType#USR_SCALE}.
   */
  private static final MutableNotNullValue<Float> userScaleFactor = new MutableNotNullValue<>(() -> {
    Float factor = DEBUG_USER_SCALE_FACTOR.get();
    if (factor != null) {
      return factor;
    }
    return computeUserScaleFactor(JreHiDpiUtil.isJreHiDPIEnabled() ? 1f : SYSTEM_SCALE_FACTOR.get());
  });

  @TestOnly
  public static void setUserScaleFactorForTest(float value) {
    setUserScaleFactorProperty(value);
  }

  private static void setUserScaleFactorProperty(float value) {
    Float oldValue = userScaleFactor.get();
    if (oldValue == value) {
      return;
    }

    userScaleFactor.set(value);
    getLogger().info("User scale factor: " + value);
    PROPERTY_CHANGE_SUPPORT.firePropertyChange(USER_SCALE_FACTOR_PROPERTY, oldValue, value);
  }

  /**
   * @return the scale factor of {@code fontSize} relative to the standard font size (currently 12pt)
   */
  public static float getFontScale(float fontSize) {
    return fontSize / DEF_SYSTEM_FONT_SIZE;
  }

  /**
   * Sets the user scale factor.
   * The method is used by the IDE, it's not recommended to call the method directly from the client code.
   * For debugging purposes, the following JVM system property can be used:
   * ide.ui.scale=[float]
   * or the IDE registry keys (for backward compatibility):
   * ide.ui.scale.override=[boolean]
   * ide.ui.scale=[float]
   *
   * @return the result
   */
  @ApiStatus.Internal
  public static float setUserScaleFactor(float scale) {
    Float factor = DEBUG_USER_SCALE_FACTOR.get();
    if (factor != null) {
      float debugScale = factor;
      if (scale == debugScale) {
        // set the debug value as is, or otherwise ignore
        setUserScaleFactorProperty(debugScale);
      }
      return debugScale;
    }

    scale = computeUserScaleFactor(scale);
    setUserScaleFactorProperty(scale);
    return scale;
  }

  private static float computeUserScaleFactor(float scale) {
    if (!SystemProperties.getBooleanProperty("hidpi", true)) {
      return 1f;
    }

    scale = discreteScale(scale);

    // Downgrading user scale below 1.0 may be uncomfortable (tiny icons),
    // whereas some users prefer font size slightly below normal which is ok.
    if (scale < 1 && sysScale() >= 1) {
      scale = 1;
    }

    // Ignore the correction when UIUtil.DEF_SYSTEM_FONT_SIZE is overridden, see UIUtil.initSystemFontData.
    if (SystemInfo.isLinux && scale == 1.25f && DEF_SYSTEM_FONT_SIZE == 12) {
      // Default UI font size for Unity and Gnome is 15. Scaling factor 1.25f works badly on Linux
      scale = 1f;
    }
    return scale;
  }

  private static float discreteScale(float scale) {
    return Math.round(scale / DISCRETE_SCALE_RESOLUTION) * DISCRETE_SCALE_RESOLUTION;
  }

  /**
   * Returns the system scale factor, corresponding to the default monitor device.
   */
  public static float sysScale() {
    return SYSTEM_SCALE_FACTOR.get();
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
   * Returns the system scale factor, corresponding to the graphics configuration.
   * In the IDE-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(@Nullable GraphicsConfiguration gc) {
    if (JreHiDpiUtil.isJreHiDPIEnabled() && gc != null) {
      if (gc.getDevice().getType() == GraphicsDevice.TYPE_RASTER_SCREEN) {
        if (SystemInfo.isMac && JreHiDpiUtil.isJreHiDPI_earlierVersion()) {
          return DetectRetinaKit.isOracleMacRetinaDevice(gc.getDevice()) ? 2f : 1f;
        }
        return (float)gc.getDefaultTransform().getScaleX();
      }
    }
    return sysScale();
  }

  /**
   * @return 'f' scaled by the user scale factor
   */
  public static float scale(float f) {
    return f * userScaleFactor.get();
  }

  /**
   * @return 'i' scaled by the user scale factor
   */
  public static int scale(int i) {
    return Math.round(userScaleFactor.get() * i);
  }

  public static int scaleFontSize(float fontSize) {
    if (userScaleFactor.get() == 1.25f) return (int)(fontSize * 1.34f);
    if (userScaleFactor.get() == 1.75f) return (int)(fontSize * 1.67f);
    return (int)scale(fontSize);
  }

  private static float getScreenScale() {
    int dpi = 96;
    try {
      dpi = Toolkit.getDefaultToolkit().getScreenResolution();
    }
    catch (HeadlessException ignored) {
    }
    return discreteScale(dpi / 96f);
  }

  @NotNull
  public static Pair<String, Integer> getSystemFontData() {
    return systemFontData.getValue();
  }

  /**
   * Returns the system scale factor, corresponding to the graphics.
   * For BufferedImage's graphics, the scale is taken from the graphics itself.
   * In the IDE-managed HiDPI mode defaults to {@link #sysScale()}
   */
  public static float sysScale(@Nullable Graphics2D g) {
    if (JreHiDpiUtil.isJreHiDPIEnabled() && g != null) {
      GraphicsConfiguration gc = g.getDeviceConfiguration();
      if (gc == null ||
          gc.getDevice().getType() == GraphicsDevice.TYPE_IMAGE_BUFFER ||
          gc.getDevice().getType() == GraphicsDevice.TYPE_PRINTER)
      {
        // in this case gc doesn't provide a valid scale
        return Math.abs((float)g.getTransform().getScaleX());
      }
      return sysScale(gc);
    }
    return sysScale();
  }

  public static double sysScale(@Nullable ScaleContext context) {
    return context == null ? sysScale() : context.getScale(ScaleType.SYS_SCALE);
  }

  /**
   * Returns whether the provided scale assumes HiDPI-awareness.
   */
  public static boolean isHiDPI(double scale) {
    // Scale below 1.0 is impractical, it's rather accepted for debug purpose.
    // Treat it as "hidpi" to correctly manage images which have different user and real size
    // (for scale below 1.0 the real size will be smaller).
    return scale != 1f;
  }

  /**
   * Returns whether the {@link ScaleType#USR_SCALE} scale factor assumes HiDPI-awareness.
   * An equivalent of {@code isHiDPI(scale(1f))}
   */
  public static boolean isUsrHiDPI() {
    return isHiDPI(scale(1f));
  }
}
