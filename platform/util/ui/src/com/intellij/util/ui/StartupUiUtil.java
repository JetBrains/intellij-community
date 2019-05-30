// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivitySubNames;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.Function;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import sun.java2d.SunGraphicsEnvironment;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class StartupUiUtil {
  // should be here and not in JBUI to avoid dependency on JBUI class in initSystemFontData method
  public static final boolean SCALE_VERBOSE = Boolean.getBoolean("ide.ui.scale.verbose");

  private static String ourSystemLaFClassName;
  private static StyleSheet ourDefaultHtmlKitCss;

  static {
    // static init it is hell - if this UIUtil static init is not called, null stylesheet added and it leads to NPE on some UI tests
    // e.g. workaround is used in UiDslTest, where UIUtil is not called at all, so, UI tasks like "set comment text" failed because of NPE.
    // (e.g. configurable tests - DatasourceConfigurableTest). It should be fixed, but for now old behaviour is preserved.
    // StartupUtil set it to false, to ensure that init logic is predictable and called in a reliable manner
    if (SystemProperties.getBooleanProperty("idea.ui.util.static.init.enabled", true)) {
      blockATKWrapper();
      configureHtmlKitStylesheet();
    }
  }

  private final static Logger LOG = Logger.getInstance(StartupUiUtil.class);

  @NotNull
  public static String getSystemLookAndFeelClassName() {
    if (ourSystemLaFClassName != null) {
      return ourSystemLaFClassName;
    }

    if (SystemInfo.isLinux) {
      // Normally, GTK LaF is considered "system" when:
      // 1) Gnome session is run
      // 2) gtk lib is available
      // Here we weaken the requirements to only 2) and force GTK LaF
      // installation in order to let it properly scale default font
      // based on Xft.dpi value.
      try {
        String name = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
        Class cls = Class.forName(name);
        LookAndFeel laf = (LookAndFeel)cls.newInstance();
        if (laf.isSupportedLookAndFeel()) { // if gtk lib is available
          return ourSystemLaFClassName = name;
        }
      }
      catch (Exception ignore) {
      }
    }

    return ourSystemLaFClassName = UIManager.getSystemLookAndFeelClassName();
  }

  public static void initDefaultLaF()
    throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {

    blockATKWrapper();

    // separate activity to make clear that it is not our code takes time
    Activity activity = ParallelActivity.PREPARE_APP_INIT.start("init AWT Toolkit");
    Toolkit.getDefaultToolkit();
    activity = activity.endAndStart("configure html kit");

    // this will use toolkit, order of code is critically important
    configureHtmlKitStylesheet();

    activity = activity.endAndStart(ActivitySubNames.INIT_DEFAULT_LAF);
    UIManager.setLookAndFeel(getSystemLookAndFeelClassName());
    activity.end();
  }

  static void configureHtmlKitStylesheet() {
    // save the default JRE CSS and ..
    HTMLEditorKit kit = new HTMLEditorKit();
    ourDefaultHtmlKitCss = kit.getStyleSheet();
    // .. erase global ref to this CSS so no one can alter it
    kit.setStyleSheet(null);

    // Applied to all JLabel instances, including subclasses. Supported in JBR only.
    UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", JBHtmlEditorKit.createStyleSheet());
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderDarcula() {
    return UIManager.getLookAndFeel().getName().contains("Darcula");
  }

  /*
   * The method should be called before java.awt.Toolkit.initAssistiveTechnologies()
   * which is called from Toolkit.getDefaultToolkit().
   */
  static void blockATKWrapper() {
    // registry must be not used here, because this method called before application loading
    if (!SystemInfo.isLinux || !SystemProperties.getBooleanProperty("linux.jdk.accessibility.atkwrapper.block", true)) {
      return;
    }

    if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
      // Replace AtkWrapper with a dummy Object. It'll be instantiated & GC'ed right away, a NOP.
      System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object");
      LOG.info(ScreenReader.ATK_WRAPPER + " is blocked, see IDEA-149219");
    }
  }

  public static StyleSheet getDefaultHtmlKitCss() {
    return ourDefaultHtmlKitCss;
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the default monitor device is HiDPI.
   * (analogue of {@link UIUtil#isRetina()} on macOS)
   */
  public static boolean isJreHiDPI() {
    return isJreHiDPI((GraphicsConfiguration)null);
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics configuration represents a HiDPI device.
   * (analogue of {@link UIUtil#isRetina(Graphics2D)} on macOS)
   */
  public static boolean isJreHiDPI(@Nullable GraphicsConfiguration gc) {
    return isJreHiDPIEnabled() && JBUI.isHiDPI(JBUI.sysScale(gc));
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics represents a HiDPI device.
   * (analogue of {@link UIUtil#isRetina(Graphics2D)} on macOS)
   */
  public static boolean isJreHiDPI(@Nullable Graphics2D g) {
    return isJreHiDPIEnabled() && JBUI.isHiDPI(JBUI.sysScale(g));
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided component is tied to a HiDPI device.
   */
  public static boolean isJreHiDPI(@Nullable Component comp) {
    return isJreHiDPI(comp != null ? comp.getGraphicsConfiguration() : null);
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided system scale context is HiDPI.
   */
  public static boolean isJreHiDPI(@Nullable JBUIScale.ScaleContext ctx) {
    return isJreHiDPIEnabled() && JBUI.isHiDPI(JBUI.sysScale(ctx));
  }

  // accessed from com.intellij.util.ui.TestScaleHelper via reflect
  private static final AtomicReference<Boolean> jreHiDPI = new AtomicReference<>();
  private static volatile boolean jreHiDPI_earlierVersion;

  @TestOnly
  @NotNull
  public static AtomicReference<Boolean> test_jreHiDPI() {
    if (jreHiDPI.get() == null) isJreHiDPIEnabled(); // force init
    return jreHiDPI;
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled.
   * (True for macOS JDK >= 7.10 versions)
   *
   * @see JBUIScale.ScaleType
   */
  public static boolean isJreHiDPIEnabled() {
    if (jreHiDPI.get() != null) return jreHiDPI.get();

    synchronized (jreHiDPI) {
      if (jreHiDPI.get() != null) return jreHiDPI.get();

      jreHiDPI.set(false);
      if (!SystemProperties.getBooleanProperty("hidpi", true)) {
        return false;
      }
      jreHiDPI_earlierVersion = true;
      if (SystemInfo.isJetBrainsJvm) {
        try {
          GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
          if (ge instanceof SunGraphicsEnvironment) {
            Method m = ReflectionUtil.getDeclaredMethod(SunGraphicsEnvironment.class, "isUIScaleEnabled");
            jreHiDPI.set(m != null && (Boolean)m.invoke(ge));
            jreHiDPI_earlierVersion = false;
          }
        }
        catch (Throwable ignore) {
        }
      }
      if (SystemInfoRt.isMac) {
        jreHiDPI.set(true);
      }
      return jreHiDPI.get();
    }
  }

  /**
   * Indicates earlier JBSDK version, not containing HiDPI changes.
   * On macOS such JBSDK supports jreHiDPI, but it's not capable to provide device scale
   * via GraphicsDevice transform matrix (the scale should be retrieved via DetectRetinaKit).
   */
  static boolean isJreHiDPI_earlierVersion() {
    isJreHiDPIEnabled();
    return jreHiDPI_earlierVersion;
  }

  @NotNull
  public static Point getCenterPoint(@NotNull Dimension container, @NotNull Dimension child) {
    return getCenterPoint(new Rectangle(container), child);
  }

  @NotNull
  public static Point getCenterPoint(@NotNull Rectangle container, @NotNull Dimension child) {
    return new Point(
      container.x + (container.width - child.width) / 2,
      container.y + (container.height - child.height) / 2
    );
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, ImageObserver)}.
   *
   * @see #drawImage(Graphics, Image, Rectangle, Rectangle, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g, @NotNull Image image, int x, int y, @Nullable ImageObserver observer) {
    drawImage(g, image, new Rectangle(x, y, -1, -1), null, null, observer);
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, int, int, ImageObserver)}.
   * <p>
   * Note, the method interprets [x,y,width,height] as the destination and source bounds which doesn't conform
   * to the {@link Graphics#drawImage(Image, int, int, int, int, ImageObserver)} method contract. This works
   * just fine for the general-purpose one-to-one drawing, however when the dst and src bounds need to be specific,
   * use {@link #drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver)}.
   */
  @Deprecated
  public static void drawImage(@NotNull Graphics g, @NotNull Image image, int x, int y, int width, int height, @Nullable ImageObserver observer) {
    drawImage(g, image, x, y, width, height, null, observer);
  }

  private static void drawImage(@NotNull Graphics g, @NotNull Image image, int x, int y, int width, int height, @Nullable BufferedImageOp op, ImageObserver observer) {
    Rectangle srcBounds = width >= 0 && height >= 0 ? new Rectangle(x, y, width, height) : null;
    drawImage(g, image, new Rectangle(x, y, width, height), srcBounds, op, observer);
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, int, int, ImageObserver)}.
   *
   * @see #drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g, @NotNull Image image, @Nullable Rectangle dstBounds, @Nullable ImageObserver observer) {
    drawImage(g, image, dstBounds, null, null, observer);
  }

  /**
   * @see #drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g,
                               @NotNull Image image,
                               @Nullable Rectangle dstBounds,
                               @Nullable Rectangle srcBounds,
                               @Nullable ImageObserver observer)
  {
    drawImage(g, image, dstBounds, srcBounds, null, observer);
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, int, int, int, int, int, int, ImageObserver)}.
   * <p>
   * The {@code dstBounds} and {@code srcBounds} are in the user space (just like the width/height of the image).
   * If {@code dstBounds} is null or if its width/height is set to (-1) the image bounds or the image width/height is used.
   * If {@code srcBounds} is null or if its width/height is set to (-1) the image bounds or the image right/bottom area to the provided x/y is used.
   */
  public static void drawImage(@NotNull Graphics g,
                               @NotNull Image image,
                               @Nullable Rectangle dstBounds,
                               @Nullable Rectangle srcBounds,
                               @Nullable BufferedImageOp op,
                               @Nullable ImageObserver observer) {
    int userWidth = ImageUtil.getUserWidth(image);
    int userHeight = ImageUtil.getUserHeight(image);

    int dx = 0;
    int dy = 0;
    int dw = -1;
    int dh = -1;
    if (dstBounds != null) {
      dx = dstBounds.x;
      dy = dstBounds.y;
      dw = dstBounds.width;
      dh = dstBounds.height;
    }
    boolean hasDstSize = dw >= 0 && dh >= 0;

    Graphics2D invG = null;
    double scale = 1;
    if (image instanceof JBHiDPIScaledImage) {
      JBHiDPIScaledImage hidpiImage = (JBHiDPIScaledImage)image;
      Image delegate = hidpiImage.getDelegate();
      if (delegate != null) image = delegate;
      scale = hidpiImage.getScale();

      AffineTransform tx = ((Graphics2D)g).getTransform();
      if (scale == tx.getScaleX()) {
        // The image has the same original scale as the graphics scale. However, the real image
        // scale - userSize/realSize - can suffer from inaccuracy due to the image user size
        // rounding to int (userSize = (int)realSize/originalImageScale). This may case quality
        // loss if the image is drawn via Graphics.drawImage(image, <srcRect>, <dstRect>)
        // due to scaling in Graphics. To avoid that, the image should be drawn directly via
        // Graphics.drawImage(image, 0, 0) on the unscaled Graphics.
        double gScaleX = tx.getScaleX();
        double gScaleY = tx.getScaleY();
        tx.scale(1 / gScaleX, 1 / gScaleY);
        tx.translate(dx * gScaleX, dy * gScaleY);
        dx = dy = 0;
        g = invG = (Graphics2D)g.create();
        invG.setTransform(tx);
      }
    }
    final double _scale = scale;
    Function<Integer, Integer> size = size1 -> (int)Math.round(size1 * _scale);
    try {
      if (op != null && image instanceof BufferedImage) {
        image = op.filter((BufferedImage)image, null);
      }
      if (invG != null && hasDstSize) {
        dw = size.fun(dw);
        dh = size.fun(dh);
      }
      if (srcBounds != null) {
        int sx = size.fun(srcBounds.x);
        int sy = size.fun(srcBounds.y);
        int sw = srcBounds.width >= 0 ? size.fun(srcBounds.width) : size.fun(userWidth) - sx;
        int sh = srcBounds.height >= 0 ? size.fun(srcBounds.height) : size.fun(userHeight) - sy;
        if (!hasDstSize) {
          dw = size.fun(userWidth);
          dh = size.fun(userHeight);
        }
        g.drawImage(image,
                    dx, dy, dx + dw, dy + dh,
                    sx, sy, sx + sw, sy + sh,
                    observer);
      }
      else if (hasDstSize) {
        g.drawImage(image, dx, dy, dw, dh, observer);
      }
      else if (invG == null) {
        g.drawImage(image, dx, dy, userWidth, userHeight, observer);
      }
      else {
        g.drawImage(image, dx, dy, observer);
      }
    }
    finally {
      if (invG != null) invG.dispose();
    }
  }

  /**
   * @see #drawImage(Graphics, Image, int, int, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g, @NotNull BufferedImage image, @Nullable BufferedImageOp op, int x, int y) {
    drawImage(g, image, x, y, -1, -1, op, null);
  }

  public static Font getLabelFont() {
    return UIManager.getFont("Label.font");
  }

  private static volatile Pair<String, Integer> ourSystemFontData;

  public static float DEF_SYSTEM_FONT_SIZE = 12f;

  public static void initSystemFontData(@NotNull Logger log) {
    if (ourSystemFontData != null) return;

    // With JB Linux JDK the label font comes properly scaled based on Xft.dpi settings.
    Font font = getLabelFont();
    if (SystemInfo.isMacOSElCapitan) {
      // Text family should be used for relatively small sizes (<20pt), don't change to Display
      // see more about SF https://medium.com/@mach/the-secret-of-san-francisco-fonts-4b5295d9a745#.2ndr50z2v
      font = new Font(".SF NS Text", font.getStyle(), font.getSize());
    }

    boolean isScaleVerbose = SCALE_VERBOSE;
    if (isScaleVerbose) {
      log.info(String.format("Label font: %s, %d", font.getFontName(), font.getSize()));
    }

    if (SystemInfoRt.isLinux) {
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
        float scale = isJreHiDPIEnabled() ? 1f : JBUI.discreteScale(dpi / 96f); // no scaling in JRE-HiDPI mode
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
    else if (SystemInfoRt.isWindows) {
      //noinspection HardCodedStringLiteral
      Font winFont = (Font)Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
      if (winFont != null) {
        font = winFont; // comes scaled
        if (isScaleVerbose) {
          log.info(String.format("Windows sys font: %s, %d", winFont.getFontName(), winFont.getSize()));
        }
      }
    }
    ourSystemFontData = Pair.create(font.getName(), font.getSize());
    if (isScaleVerbose) {
      log.info(String.format("ourSystemFontData: %s, %d", ourSystemFontData.first, ourSystemFontData.second));
    }
  }

  private static float getScreenScale() {
    int dpi = 96;
    try {
      dpi = Toolkit.getDefaultToolkit().getScreenResolution();
    }
    catch (HeadlessException ignored) {
    }
    return JBUI.discreteScale(dpi / 96f);
  }


  @Nullable
  public static Pair<String, Integer> getSystemFontData() {
    return ourSystemFontData;
  }


}
