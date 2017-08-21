/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.BundleBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.ui.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import sun.java2d.SunGraphicsEnvironment;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.RoundRectangle2D;
import java.awt.im.InputContext;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

/**
 * @author max
 */
@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public class UIUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.UIUtil");

  public static final String BORDER_LINE = "<hr size=1 noshade>";

  private static final StyleSheet DEFAULT_HTML_KIT_CSS;

  static {
    blockATKWrapper();
    // save the default JRE CSS and ..
    HTMLEditorKit kit = new HTMLEditorKit();
    DEFAULT_HTML_KIT_CSS = kit.getStyleSheet();
    // .. erase global ref to this CSS so no one can alter it
    kit.setStyleSheet(null);

    // Applied to all JLabel instances, including subclasses. Supported in JBSDK only.
    UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", UIUtil.JBHtmlEditorKit.createStyleSheet());
  }

  public static void decorateFrame(@NotNull JRootPane pane) {
    if (Registry.is("ide.mac.allowDarkWindowDecorations")) {
      pane.putClientProperty("jetbrains.awt.windowDarkAppearance", isUnderDarcula());
    }
  }

  private static void blockATKWrapper() {
    /*
     * The method should be called before java.awt.Toolkit.initAssistiveTechnologies()
     * which is called from Toolkit.getDefaultToolkit().
     */
    if (!(SystemInfo.isLinux && Registry.is("linux.jdk.accessibility.atkwrapper.block"))) return;

    if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
      // Replace AtkWrapper with a dummy Object. It'll be instantiated & GC'ed right away, a NOP.
      System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object");
      LOG.info(ScreenReader.ATK_WRAPPER + " is blocked, see IDEA-149219");
    }
  }

  public static int getMultiClickInterval() {
    Object property = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
    if (property instanceof Integer) {
      return (Integer)property;
    }
    return 500;
  }

  private static final AtomicNotNullLazyValue<Boolean> X_RENDER_ACTIVE = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      if (!SystemInfo.isXWindow) {
        return false;
      }
      try {
        final Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass("sun.awt.X11GraphicsEnvironment");
        final Method method = clazz.getMethod("isXRenderAvailable");
        return (Boolean)method.invoke(null);
      }
      catch (Throwable e) {
        return false;
      }
    }
  };

  private static final String[] STANDARD_FONT_SIZES =
    {"8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};

  public static void applyStyle(@NotNull ComponentStyle componentStyle, @NotNull Component comp) {
    if (!(comp instanceof JComponent)) return;

    JComponent c = (JComponent)comp;

    if (isUnderAquaBasedLookAndFeel()) {
      c.putClientProperty("JComponent.sizeVariant", StringUtil.toLowerCase(componentStyle.name()));
    }
    FontSize fontSize = componentStyle == ComponentStyle.MINI
                        ? FontSize.MINI
                        : componentStyle == ComponentStyle.SMALL
                          ? FontSize.SMALL
                          : FontSize.NORMAL;
    c.setFont(getFont(fontSize, c.getFont()));
    Container p = c.getParent();
    if (p != null) {
      SwingUtilities.updateComponentTreeUI(p);
    }
  }

  public static Cursor getTextCursor(final Color backgroundColor) {
    return SystemInfo.isMac && ColorUtil.isDark(backgroundColor) ?
           MacUIUtil.getInvertedTextCursor() : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
  }

  /**
   * Draws two horizontal lines, the first at {@code topY}, the second at {@code bottomY}.
   * The purpose of this method (and the ground of the name) is to draw two lines framing a horizontal filled rectangle.
   *
   * @param g       Graphics context to draw with.
   * @param startX  x-start point.
   * @param endX    x-end point.
   * @param topY    y-coordinate of the first line.
   * @param bottomY y-coordinate of the second line.
   * @param color   color of the lines.
   */
  public static void drawFramingLines(@NotNull Graphics2D g, int startX, int endX, int topY, int bottomY, @NotNull Color color) {
    drawLine(g, startX, topY, endX, topY, null, color);
    drawLine(g, startX, bottomY, endX, bottomY, null, color);
  }

  private static final GrayFilter DEFAULT_GRAY_FILTER = new GrayFilter(true, 70);
  private static final GrayFilter DARCULA_GRAY_FILTER = new GrayFilter(true, 20);

  public static GrayFilter getGrayFilter() {
    return isUnderDarcula() ? DARCULA_GRAY_FILTER : DEFAULT_GRAY_FILTER;
  }

  public static boolean isAppleRetina() {
    return isRetina() && SystemInfo.isAppleJvm;
  }

  public static Couple<Color> getCellColors(JTable table, boolean isSel, int row, int column) {
    return Couple.of(isSel ? table.getSelectionForeground() : table.getForeground(),
                                 isSel
                                 ? table.getSelectionBackground()
                                 : isUnderNimbusLookAndFeel() && row % 2 == 1 ? TRANSPARENT_COLOR : table.getBackground());
  }

  public static void fixOSXEditorBackground(@NotNull JTable table) {
    if (!SystemInfo.isMac) return;

    if (table.isEditing()) {
      int column = table.getEditingColumn();
      int row = table.getEditingRow();
      Component renderer = column>=0 && row >= 0 ? table.getCellRenderer(row, column)
        .getTableCellRendererComponent(table, table.getValueAt(row, column), true, table.hasFocus(), row, column) : null;
      Component component = table.getEditorComponent();
      if (component != null && renderer != null) {
        changeBackGround(component, renderer.getBackground());
      }
    }
  }

  public static boolean isDialogFont(Font font) {
    return Font.DIALOG.equals(font.getFamily(Locale.US));
  }

  public enum FontSize {NORMAL, SMALL, MINI}

  public enum ComponentStyle {LARGE, REGULAR, SMALL, MINI}

  public enum FontColor {NORMAL, BRIGHTER}

  public static final char MNEMONIC = BundleBase.MNEMONIC;
  @NonNls public static final String HTML_MIME = "text/html";
  @NonNls public static final String JSLIDER_ISFILLED = "JSlider.isFilled";
  @NonNls public static final String ARIAL_FONT_NAME = "Arial";
  @NonNls public static final String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";
  @NonNls public static final String CENTER_TOOLTIP_DEFAULT = "ToCenterTooltip";
  @NonNls public static final String CENTER_TOOLTIP_STRICT = "ToCenterTooltip.default";

  private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("<\\s*([^<>/ ]+)([^<>]*)/\\s*>", Pattern.CASE_INSENSITIVE);

  @NonNls private static final String FOCUS_PROXY_KEY = "isFocusProxy";

  public static final Key<Integer> KEEP_BORDER_SIDES = Key.create("keepBorderSides");
  private static final Key<UndoManager> UNDO_MANAGER = Key.create("undoManager");
  private static final AbstractAction REDO_ACTION = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      UndoManager manager = getClientProperty(e.getSource(), UNDO_MANAGER);
      if (manager != null && manager.canRedo()) {
        manager.redo();
      }
    }
  };
  private static final AbstractAction UNDO_ACTION = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      UndoManager manager = getClientProperty(e.getSource(), UNDO_MANAGER);
      if (manager != null && manager.canUndo()) {
        manager.undo();
      }
    }
  };

  private static final Color UNFOCUSED_SELECTION_COLOR = Gray._212;
  private static final Color ACTIVE_HEADER_COLOR = new Color(160, 186, 213);
  private static final Color INACTIVE_HEADER_COLOR = Gray._128;
  private static final Color BORDER_COLOR = Color.LIGHT_GRAY;

  public static final Color CONTRAST_BORDER_COLOR = new JBColor(new NotNullProducer<Color>() {
    final Color color = new JBColor(0x9b9b9b, 0x4b4b4b);
    @NotNull
    @Override
    public Color produce() {
      if (SystemInfo.isMac && isUnderIntelliJLaF()) {
        return Gray.xC9;
      }
      return color;
    }
  });

  public static final Color SIDE_PANEL_BACKGROUND = new JBColor(new NotNullProducer<Color>() {
    final JBColor myDefaultValue = new JBColor(new Color(0xE6EBF0), new Color(0x3E434C));
    @NotNull
    @Override
    public Color produce() {
      Color color = UIManager.getColor("SidePanel.background");
      return color == null ? myDefaultValue : color;
    }
  });

  public static final Color AQUA_SEPARATOR_FOREGROUND_COLOR = new JBColor(Gray._223, Gray.x51);
  public static final Color AQUA_SEPARATOR_BACKGROUND_COLOR = new JBColor(Gray._240, Gray.x51);
  public static final Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);

  public static final int DEFAULT_HGAP = 10;
  public static final int DEFAULT_VGAP = 4;
  public static final int LARGE_VGAP = 12;

  public static final Insets PANEL_REGULAR_INSETS = new Insets(8, 12, 8, 12);
  public static final Insets PANEL_SMALL_INSETS = new Insets(5, 8, 5, 8);


  public static final Border DEBUG_MARKER_BORDER = new Border() {
    @Override
    public Insets getBorderInsets(Component c) {
      return new Insets(0, 0, 0, 0);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics g2 = g.create();
      try {
        g2.setColor(JBColor.RED);
        drawDottedRectangle(g2, x, y, x + width - 1, y + height - 1);
      }
      finally {
        g2.dispose();
      }
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  };

  private static volatile Pair<String, Integer> ourSystemFontData;

  public static final float DEF_SYSTEM_FONT_SIZE = 12f; // TODO: consider 12 * 1.33 to compensate JDK's 72dpi font scale

  @NonNls private static final String ROOT_PANE = "JRootPane.future";

  private static final Ref<Boolean> ourRetina = Ref.create(SystemInfo.isMac ? null : false);

  private UIUtil() {
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the default monitor device is HiDPI.
   * (analogue of {@link #isRetina()} on macOS)
   */
  public static boolean isJreHiDPI() {
    return isJreHiDPI((GraphicsConfiguration)null);
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics configuration represents a HiDPI device.
   * (analogue of {@link #isRetina(Graphics2D)} on macOS)
   */
  public static boolean isJreHiDPI(@Nullable GraphicsConfiguration gc) {
    return isJreHiDPIEnabled() && JBUI.isHiDPI(JBUI.sysScale(gc));
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics represents a HiDPI device.
   * (analogue of {@link #isRetina(Graphics2D)} on macOS)
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

  private static Boolean jreHiDPI;
  private static boolean jreHiDPI_earlierVersion;

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled.
   * (True for macOS JDK >= 7.10 versions)
   *
   * @see JBUI.ScaleType
   */
  public static boolean isJreHiDPIEnabled() {
    if (jreHiDPI != null) {
      return jreHiDPI;
    }
    jreHiDPI = false;
    jreHiDPI_earlierVersion = true;
    if (SystemInfo.isLinux) {
      return false; // pending support
    }
    if (SystemInfo.isJetBrainsJvm) {
      try {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (ge instanceof SunGraphicsEnvironment) {
          Method m = ReflectionUtil.getDeclaredMethod(SunGraphicsEnvironment.class, "isUIScaleOn");
          jreHiDPI = (Boolean)m.invoke(ge);
          jreHiDPI_earlierVersion = false;
        }
      }
      catch (Throwable ignore) {
      }
    }
    if (SystemInfo.isMac) {
      jreHiDPI = !SystemInfo.isAppleJvm;
    }
    return jreHiDPI;
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

  /**
   * Utility class for retina routine
   */
  static final class DetectRetinaKit {

    private static final Map<GraphicsDevice, Boolean> devicesToRetinaSupportCacheMap = ContainerUtil.createWeakMap();

    /**
     * The best way to understand whether we are on a retina device is [NSScreen backingScaleFactor]
     * But we should not invoke it from any thread. We do not have access to the AppKit thread
     * on the other hand. So let's use a dedicated method. It is rather safe because it caches a
     * value that has been got on AppKit previously.
     */
    static boolean isOracleMacRetinaDevice (GraphicsDevice device) {

      if (SystemInfo.isAppleJvm) return false;

      Boolean isRetina  = devicesToRetinaSupportCacheMap.get(device);

      if (isRetina != null){
        return isRetina;
      }

      Method getScaleFactorMethod = null;
      try {
        getScaleFactorMethod = Class.forName("sun.awt.CGraphicsDevice").getMethod("getScaleFactor");
      } catch (ClassNotFoundException e) {
        // not an Oracle Mac JDK or API has been changed
        LOG.debug("CGraphicsDevice.getScaleFactor(): not an Oracle Mac JDK or API has been changed");
      } catch (NoSuchMethodException e) {
        LOG.debug("CGraphicsDevice.getScaleFactor(): not an Oracle Mac JDK or API has been changed");
      } catch (Exception e) {
        LOG.debug(e);
        LOG.debug("CGraphicsDevice.getScaleFactor(): probably it is Java 9");
      }

      try {
        isRetina =  getScaleFactorMethod == null || (Integer)getScaleFactorMethod.invoke(device) != 1;
      } catch (IllegalAccessException e) {
        LOG.debug("CGraphicsDevice.getScaleFactor(): Access issue");
        isRetina = false;
      } catch (InvocationTargetException e) {
        LOG.debug("CGraphicsDevice.getScaleFactor(): Invocation issue");
        isRetina = false;
      } catch (IllegalArgumentException e) {
        LOG.debug("object is not an instance of declaring class: " + device.getClass().getName());
        isRetina = false;
      }

      devicesToRetinaSupportCacheMap.put(device, isRetina);

      return isRetina;
    }

    /*
      Could be quite easily implemented with [NSScreen backingScaleFactor]
      and JNA
     */
    //private static boolean isAppleRetina (Graphics2D g2d) {
    //  return false;
    //}

    /**
     * For JDK6 we have a dedicated property which does not allow to understand anything
     * per device but could be useful for image creation. We will get true in case
     * if at least one retina device is present.
     */
    private static boolean hasAppleRetinaDevice() {
      return (Float)Toolkit.getDefaultToolkit()
        .getDesktopProperty(
          "apple.awt.contentScaleFactor") != 1.0f;
    }

    /**
     * This method perfectly detects retina Graphics2D for jdk7+
     * For Apple JDK6 it returns false.
     * @param g graphics to be tested
     * @return false if the device of the Graphics2D is not a retina device,
     * jdk is an Apple JDK or Oracle API has been changed.
     */
    private static boolean isMacRetina(Graphics2D g) {
      GraphicsDevice device = g.getDeviceConfiguration().getDevice();
      return isOracleMacRetinaDevice(device);
    }

    /**
     * Checks that at least one retina device is present.
     * Do not use this method if your are going to make decision for a particular screen.
     * isRetina(Graphics2D) is more preferable
     *
     * @return true if at least one device is a retina device
     */
    private static boolean isRetina() {
      if (SystemInfo.isAppleJvm) {
        return hasAppleRetinaDevice();
      }

      // Oracle JDK

      if (SystemInfo.isMac) {
        GraphicsEnvironment e
          = GraphicsEnvironment.getLocalGraphicsEnvironment();

        GraphicsDevice[] devices = e.getScreenDevices();

        //now get the configurations for each device
        for (GraphicsDevice device : devices) {
          if (isOracleMacRetinaDevice(device)) {
            return true;
          }
        }
      }

      return false;
    }
  }

  public static boolean isRetina (Graphics2D graphics) {
    if (SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.7")) {
      return DetectRetinaKit.isMacRetina(graphics);
    }
    return isRetina();
  }

  //public static boolean isMacRetina(Graphics2D g) {
  //  return DetectRetinaKit.isMacRetina(g);
  //}

  public static boolean isRetina() {
    if (GraphicsEnvironment.isHeadless()) return false;

    //Temporary workaround for HiDPI on Windows/Linux
    if ("true".equalsIgnoreCase(System.getProperty("is.hidpi"))) {
      return true;
    }

    if (Registry.is("new.retina.detection")) {
      return DetectRetinaKit.isRetina();
    } else {
      synchronized (ourRetina) {
        if (ourRetina.isNull()) {
          ourRetina.set(false); // in case HiDPIScaledImage.drawIntoImage is not called for some reason

          if (SystemInfo.isJavaVersionAtLeast("1.6.0_33") && SystemInfo.isAppleJvm) {
            if (!"false".equals(System.getProperty("ide.mac.retina"))) {
              ourRetina.set(IsRetina.isRetina());
              return ourRetina.get();
            }
          }
          else if (SystemInfo.isJavaVersionAtLeast("1.7.0_40") /*&& !SystemInfo.isOracleJvm*/) {
            try {
              GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
              final GraphicsDevice device = env.getDefaultScreenDevice();
              Integer scale = ReflectionUtil.getField(device.getClass(), device, int.class, "scale");
              if (scale != null && scale.intValue() == 2) {
                ourRetina.set(true);
                return true;
              }
            }
            catch (AWTError ignore) {
            }
            catch (Exception ignore) {
            }
          }
          ourRetina.set(false);
        }

        return ourRetina.get();
      }
    }
  }

  public static boolean hasLeakingAppleListeners() {
    // in version 1.6.0_29 Apple introduced a memory leak in JViewport class - they add a PropertyChangeListeners to the CToolkit
    // but never remove them:
    // JViewport.java:
    // public JViewport() {
    //   ...
    //   final Toolkit toolkit = Toolkit.getDefaultToolkit();
    //   if(toolkit instanceof CToolkit)
    //   {
    //     final boolean isRunningInHiDPI = ((CToolkit)toolkit).runningInHiDPI();
    //     if(isRunningInHiDPI) setScrollMode(0);
    //     toolkit.addPropertyChangeListener("apple.awt.contentScaleFactor", new PropertyChangeListener() { ... });
    //   }
    // }

    return SystemInfo.isMac && System.getProperty("java.runtime.version").startsWith("1.6.0_29");
  }

  public static void removeLeakingAppleListeners() {
    if (!hasLeakingAppleListeners()) return;

    Toolkit toolkit = Toolkit.getDefaultToolkit();
    String name = "apple.awt.contentScaleFactor";
    for (PropertyChangeListener each : toolkit.getPropertyChangeListeners(name)) {
      toolkit.removePropertyChangeListener(name, each);
    }
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       the client property key
   * @return {@code true} if the property of the specified component is set to {@code true}
   */
  public static boolean isClientPropertyTrue(Object component, @NotNull Object key) {
    return Boolean.TRUE.equals(getClientProperty(component, key));
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       the client property key that specifies a return type
   * @return the property value from the specified component or {@code null}
   */
  public static Object getClientProperty(Object component, @NotNull Object key) {
    return component instanceof JComponent ? ((JComponent)component).getClientProperty(key) : null;
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @return the property value from the specified component or {@code null}
   */
  public static <T> T getClientProperty(Object component, @NotNull Class<T> type) {
    return ObjectUtils.tryCast(getClientProperty(component, (Object)type), type);
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       the client property key that specifies a return type
   * @return the property value from the specified component or {@code null}
   */
  public static <T> T getClientProperty(Object component, @NotNull Key<T> key) {
    //noinspection unchecked
    return (T)getClientProperty(component, (Object)key);
  }

  public static <T> void putClientProperty(@NotNull JComponent component, @NotNull Key<T> key, T value) {
    component.putClientProperty(key, value);
  }

  public static String getHtmlBody(@NotNull String text) {
    int htmlIndex = 6 + text.indexOf("<html>");
    if (htmlIndex < 6) {
      return text.replaceAll("\n", "<br>");
    }
    int htmlCloseIndex = text.indexOf("</html>", htmlIndex);
    if (htmlCloseIndex < 0) {
      htmlCloseIndex = text.length();
    }
    int bodyIndex = 6 + text.indexOf("<body>", htmlIndex);
    if (bodyIndex < 6) {
      return text.substring(htmlIndex, htmlCloseIndex);
    }
    int bodyCloseIndex = text.indexOf("</body>", bodyIndex);
    if (bodyCloseIndex < 0) {
      bodyCloseIndex = text.length();
    }
    return text.substring(bodyIndex, Math.min(bodyCloseIndex, htmlCloseIndex));
  }

  public static String getHtmlBody(Html html) {
    String result = getHtmlBody(html.getText());
    return html.isKeepFont() ? result : result.replaceAll("<font(.*?)>", "").replaceAll("</font>", "");
  }

  public static void drawLinePickedOut(Graphics graphics, int x, int y, int x1, int y1) {
    if (x == x1) {
      int minY = Math.min(y, y1);
      int maxY = Math.max(y, y1);
      graphics.drawLine(x, minY + 1, x1, maxY - 1);
    }
    else if (y == y1) {
      int minX = Math.min(x, x1);
      int maxX = Math.max(x, x1);
      graphics.drawLine(minX + 1, y, maxX - 1, y1);
    }
    else {
      drawLine(graphics, x, y, x1, y1);
    }
  }

  public static boolean isReallyTypedEvent(KeyEvent e) {
    char c = e.getKeyChar();
    if (c < 0x20 || c == 0x7F) return false;

    if (SystemInfo.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }

    return !e.isAltDown() && !e.isControlDown();
  }

  public static int getStringY(@NotNull final String string, @NotNull final Rectangle bounds, @NotNull final Graphics2D g) {
    final int centerY = bounds.height / 2;
    final Font font = g.getFont();
    final FontRenderContext frc = g.getFontRenderContext();
    final Rectangle stringBounds = font.getStringBounds(string, frc).getBounds();

    return (int)(centerY - stringBounds.height / 2.0 - stringBounds.y);
  }

  /**
   * @param string {@code String} to examine
   * @param font {@code Font} that is used to render the string
   * @param graphics {@link Graphics} that should be used to render the string
   * @return height of the tallest glyph in a string. If string is empty, returns 0
   */
  public static int getHighestGlyphHeight(@NotNull String string, @NotNull Font font, @NotNull Graphics graphics) {
    FontRenderContext frc = ((Graphics2D)graphics).getFontRenderContext();
    GlyphVector gv = font.createGlyphVector(frc, string);
    int maxHeight = 0;
    for (int i = 0; i < string.length(); i ++) {
      maxHeight = Math.max(maxHeight, (int)gv.getGlyphMetrics(i).getBounds2D().getHeight());
    }
    return maxHeight;
  }

  public static void setEnabled(Component component, boolean enabled, boolean recursively) {
    setEnabled(component, enabled, recursively, false);
  }

  public static void setEnabled(Component component, boolean enabled, boolean recursively, final boolean visibleOnly) {
    JBIterable<Component> all = recursively ? uiTraverser(component).expandAndFilter(
      visibleOnly ? new Condition<Component>() {
        @Override
        public boolean value(Component c) {
          return c.isVisible();
        }
      } : Conditions.<Component>alwaysTrue()).traverse() : JBIterable.of(component);
    Color fg = enabled ? getLabelForeground() : getLabelDisabledForeground();
    for (Component c : all) {
      c.setEnabled(enabled);
      if (fg != null && c instanceof JLabel) {
        c.setForeground(fg);
      }
    }
  }

  public static void drawLine(Graphics g, int x1, int y1, int x2, int y2) {
    g.drawLine(x1, y1, x2, y2);
  }

  public static void drawLine(Graphics2D g, int x1, int y1, int x2, int y2, @Nullable Color bgColor, @Nullable Color fgColor) {
    Color oldFg = g.getColor();
    Color oldBg = g.getBackground();
    if (fgColor != null) {
      g.setColor(fgColor);
    }
    if (bgColor != null) {
      g.setBackground(bgColor);
    }
    drawLine(g, x1, y1, x2, y2);
    if (fgColor != null) {
      g.setColor(oldFg);
    }
    if (bgColor != null) {
      g.setBackground(oldBg);
    }
  }

  public static void drawWave(Graphics2D g, Rectangle rectangle) {
    WavePainter.forColor(g.getColor()).paint(g, (int)rectangle.getMinX(), (int) rectangle.getMaxX(), (int) rectangle.getMaxY());
  }

  @NotNull
  public static String[] splitText(String text, FontMetrics fontMetrics, int widthLimit, char separator) {
    ArrayList<String> lines = new ArrayList<String>();
    String currentLine = "";
    StringBuilder currentAtom = new StringBuilder();

    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      currentAtom.append(ch);

      if (ch == separator) {
        currentLine += currentAtom.toString();
        currentAtom.setLength(0);
      }

      String s = currentLine + currentAtom;
      int width = fontMetrics.stringWidth(s);

      if (width >= widthLimit - fontMetrics.charWidth('w')) {
        if (!currentLine.isEmpty()) {
          lines.add(currentLine);
          currentLine = "";
        }
        else {
          lines.add(currentAtom.toString());
          currentAtom.setLength(0);
        }
      }
    }

    String s = currentLine + currentAtom;
    if (!s.isEmpty()) {
      lines.add(s);
    }

    return ArrayUtil.toStringArray(lines);
  }

  public static void setActionNameAndMnemonic(@NotNull String text, @NotNull Action action) {
    assignMnemonic(text, action);

    text = text.replaceAll("&", "");
    action.putValue(Action.NAME, text);
  }
  public static void assignMnemonic(@NotNull String text, @NotNull Action action) {
    int mnemoPos = text.indexOf('&');
    if (mnemoPos >= 0 && mnemoPos < text.length() - 2) {
      String mnemoChar = text.substring(mnemoPos + 1, mnemoPos + 2).trim();
      if (mnemoChar.length() == 1) {
        action.putValue(Action.MNEMONIC_KEY, Integer.valueOf(mnemoChar.charAt(0)));
      }
    }
  }


  public static Font getLabelFont(@NotNull FontSize size) {
    return getFont(size, null);
  }

  @NotNull
  public static Font getFont(@NotNull FontSize size, @Nullable Font base) {
    if (base == null) base = getLabelFont();

    return base.deriveFont(getFontSize(size));
  }

  public static float getFontSize(FontSize size) {
    int defSize = getLabelFont().getSize();
    switch (size) {
      case SMALL:
        return Math.max(defSize - JBUI.scale(2f), JBUI.scale(11f));
      case MINI:
        return Math.max(defSize - JBUI.scale(4f), JBUI.scale(9f));
      default:
        return defSize;
    }
  }

  public static Color getLabelFontColor(FontColor fontColor) {
    Color defColor = getLabelForeground();
    if (fontColor == FontColor.BRIGHTER) {
      return new JBColor(new Color(Math.min(defColor.getRed() + 50, 255), Math.min(defColor.getGreen() + 50, 255), Math.min(
        defColor.getBlue() + 50, 255)), defColor.darker());
    }
    return defColor;
  }

  private static final Map<Class, Ref<Method>> ourDefaultIconMethodsCache = new ConcurrentHashMap<Class, Ref<Method>>();
  public static int getCheckBoxTextHorizontalOffset(@NotNull JCheckBox cb) {
    // logic copied from javax.swing.plaf.basic.BasicRadioButtonUI.paint
    ButtonUI ui = cb.getUI();
    String text = cb.getText();

    Icon buttonIcon = cb.getIcon();
    if (buttonIcon == null && ui != null) {
      if (ui instanceof BasicRadioButtonUI) {
        buttonIcon = ((BasicRadioButtonUI)ui).getDefaultIcon();
      }
      else if (isUnderAquaLookAndFeel()) {
        // inheritors of AquaButtonToggleUI
        Ref<Method> cached = ourDefaultIconMethodsCache.get(ui.getClass());
        if (cached == null) {
          cached = Ref.create(ReflectionUtil.findMethod(Arrays.asList(ui.getClass().getMethods()), "getDefaultIcon", JComponent.class));
          ourDefaultIconMethodsCache.put(ui.getClass(), cached);
          if (!cached.isNull()) {
            cached.get().setAccessible(true);
          }
        }
        Method method = cached.get();
        if (method != null) {
          try {
            buttonIcon = (Icon)method.invoke(ui, cb);
          }
          catch (Exception e) {
            cached.set(null);
          }
        }
      }
    }

    Dimension size = new Dimension();
    Rectangle viewRect = new Rectangle();
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Insets i = cb.getInsets();

    size = cb.getSize(size);
    viewRect.x = i.left;
    viewRect.y = i.top;
    viewRect.width = size.width - (i.right + viewRect.x);
    viewRect.height = size.height - (i.bottom + viewRect.y);
    iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;
    textRect.x = textRect.y = textRect.width = textRect.height = 0;

    SwingUtilities.layoutCompoundLabel(
      cb, cb.getFontMetrics(cb.getFont()), text, buttonIcon,
      cb.getVerticalAlignment(), cb.getHorizontalAlignment(),
      cb.getVerticalTextPosition(), cb.getHorizontalTextPosition(),
      viewRect, iconRect, textRect,
      text == null ? 0 : cb.getIconTextGap());

    return textRect.x;
  }

  public static int getScrollBarWidth() {
    return UIManager.getInt("ScrollBar.width");
  }

  public static Font getLabelFont() {
    return UIManager.getFont("Label.font");
  }

  public static Color getLabelBackground() {
    return UIManager.getColor("Label.background");
  }

  public static Color getLabelForeground() {
    return UIManager.getColor("Label.foreground");
  }

  public static Color getLabelDisabledForeground() {
    final Color color = UIManager.getColor("Label.disabledForeground");
    if (color != null) return color;
    return UIManager.getColor("Label.disabledText");
  }

  @NotNull
  public static String removeMnemonic(@NotNull String s) {
    if (s.indexOf('&') != -1) {
      s = StringUtil.replace(s, "&", "");
    }
    if (s.indexOf('_') != -1) {
      s = StringUtil.replace(s, "_", "");
    }
    if (s.indexOf(MNEMONIC) != -1) {
      s = StringUtil.replace(s, String.valueOf(MNEMONIC), "");
    }
    return s;
  }

  public static int getDisplayMnemonicIndex(@NotNull String s) {
    int idx = s.indexOf('&');
    if (idx >= 0 && idx != s.length() - 1 && idx == s.lastIndexOf('&')) return idx;

    idx = s.indexOf(MNEMONIC);
    if (idx >= 0 && idx != s.length() - 1 && idx == s.lastIndexOf(MNEMONIC)) return idx;

    return -1;
  }

  public static String replaceMnemonicAmpersand(final String value) {
    return BundleBase.replaceMnemonicAmpersand(value);
  }

  public static Color getTableHeaderBackground() {
    return UIManager.getColor("TableHeader.background");
  }

  public static Color getTreeTextForeground() {
    return UIManager.getColor("Tree.textForeground");
  }

  public static Color getTreeSelectionBackground() {
    if (isUnderNimbusLookAndFeel()) {
      Color color = UIManager.getColor("Tree.selectionBackground");
      if (color != null) return color;
      color = UIManager.getColor("nimbusSelectionBackground");
      if (color != null) return color;
    }
    return UIManager.getColor("Tree.selectionBackground");
  }

  public static Color getTreeTextBackground() {
    return UIManager.getColor("Tree.textBackground");
  }

  public static Color getListSelectionForeground() {
    final Color color = UIManager.getColor("List.selectionForeground");
    if (color == null) {
      return UIManager.getColor("List[Selected].textForeground");  // Nimbus
    }
    return color;
  }

  public static Color getFieldForegroundColor() {
    return UIManager.getColor("field.foreground");
  }

  public static Color getTableSelectionBackground() {
    if (isUnderNimbusLookAndFeel()) {
      Color color = UIManager.getColor("Table[Enabled+Selected].textBackground");
      if (color != null) return color;
      color = UIManager.getColor("nimbusSelectionBackground");
      if (color != null) return color;
    }
    return UIManager.getColor("Table.selectionBackground");
  }

  public static Color getActiveTextColor() {
    return UIManager.getColor("textActiveText");
  }

  public static Color getInactiveTextColor() {
    return UIManager.getColor("textInactiveText");
  }

  public static Color getSlightlyDarkerColor(Color c) {
    float[] hsl = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), new float[3]);
    return new Color(Color.HSBtoRGB(hsl[0], hsl[1], hsl[2] - .08f > 0 ? hsl[2] - .08f : hsl[2]));
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getTextFieldBackground()
   */
  public static Color getActiveTextFieldBackgroundColor() {
    return getTextFieldBackground();
  }

  public static Color getInactiveTextFieldBackgroundColor() {
    return UIManager.getColor("TextField.inactiveBackground");
  }

  public static Font getTreeFont() {
    return UIManager.getFont("Tree.font");
  }

  public static Font getListFont() {
    return UIManager.getFont("List.font");
  }

  public static Color getTreeSelectionForeground() {
    return UIManager.getColor("Tree.selectionForeground");
  }

  public static Color getTreeForeground(boolean selected, boolean hasFocus) {
    if (!selected) {
      return getTreeForeground();
    }
    Color fg = UIManager.getColor("Tree.selectionInactiveForeground");
    if (!hasFocus && fg != null) {
      return fg;
    }
    return getTreeSelectionForeground();
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getInactiveTextColor()
   */
  public static Color getTextInactiveTextColor() {
    return getInactiveTextColor();
  }

  public static void installPopupMenuColorAndFonts(final JComponent contentPane) {
    LookAndFeel.installColorsAndFont(contentPane, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
  }

  public static void installPopupMenuBorder(final JComponent contentPane) {
    LookAndFeel.installBorder(contentPane, "PopupMenu.border");
  }

  public static Color getTreeSelectionBorderColor() {
    return UIManager.getColor("Tree.selectionBorderColor");
  }

  public static int getTreeRightChildIndent() {
    return UIManager.getInt("Tree.rightChildIndent");
  }

  public static int getTreeLeftChildIndent() {
    return UIManager.getInt("Tree.leftChildIndent");
  }

  public static Color getToolTipBackground() {
    return UIManager.getColor("ToolTip.background");
  }

  public static Color getToolTipForeground() {
    return UIManager.getColor("ToolTip.foreground");
  }

  public static Color getComboBoxDisabledForeground() {
    return UIManager.getColor("ComboBox.disabledForeground");
  }

  public static Color getComboBoxDisabledBackground() {
    return UIManager.getColor("ComboBox.disabledBackground");
  }

  public static Color getButtonSelectColor() {
    return UIManager.getColor("Button.select");
  }

  public static Integer getPropertyMaxGutterIconWidth(final String propertyPrefix) {
    return (Integer)UIManager.get(propertyPrefix + ".maxGutterIconWidth");
  }

  public static Color getMenuItemDisabledForeground() {
    return UIManager.getColor("MenuItem.disabledForeground");
  }

  public static Object getMenuItemDisabledForegroundObject() {
    return UIManager.get("MenuItem.disabledForeground");
  }

  public static Object getTabbedPanePaintContentBorder(final JComponent c) {
    return c.getClientProperty("TabbedPane.paintContentBorder");
  }

  public static boolean isMenuCrossMenuMnemonics() {
    return UIManager.getBoolean("Menu.crossMenuMnemonic");
  }

  public static Color getTableBackground() {
    // Under GTK+ L&F "Table.background" often has main panel color, which looks ugly
    return isUnderGTKLookAndFeel() ? getTreeTextBackground() : UIManager.getColor("Table.background");
  }

  public static Color getTableBackground(final boolean isSelected) {
    return isSelected ? getTableSelectionBackground() : getTableBackground();
  }

  public static Color getTableSelectionForeground() {
    if (isUnderNimbusLookAndFeel()) {
      return UIManager.getColor("Table[Enabled+Selected].textForeground");
    }
    return UIManager.getColor("Table.selectionForeground");
  }

  public static Color getTableForeground() {
    return UIManager.getColor("Table.foreground");
  }

  public static Color getTableForeground(final boolean isSelected) {
    return isSelected ? getTableSelectionForeground() : getTableForeground();
  }

  public static Color getTableGridColor() {
    return UIManager.getColor("Table.gridColor");
  }

  public static Color getListBackground() {
    if (isUnderNimbusLookAndFeel()) {
      final Color color = UIManager.getColor("List.background");
      //noinspection UseJBColor
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
    // Under GTK+ L&F "Table.background" often has main panel color, which looks ugly
    return isUnderGTKLookAndFeel() ? getTreeTextBackground() : UIManager.getColor("List.background");
  }

  public static Color getListBackground(boolean isSelected) {
    return isSelected ? getListSelectionBackground() : getListBackground();
  }

  public static Color getListForeground() {
    return UIManager.getColor("List.foreground");
  }

  public static Color getListForeground(boolean isSelected) {
    return isSelected ? getListSelectionForeground() : getListForeground();
  }

  public static Color getPanelBackground() {
    return UIManager.getColor("Panel.background");
  }

  public static Color getEditorPaneBackground() {
    return UIManager.getColor("EditorPane.background");
  }

  public static Color getTreeBackground() {
    return UIManager.getColor("Tree.background");
  }

  public static Color getTreeForeground() {
    return UIManager.getColor("Tree.foreground");
  }

  public static Color getTableFocusCellBackground() {
    return UIManager.getColor(TABLE_FOCUS_CELL_BACKGROUND_PROPERTY);
  }

  public static Color getListSelectionBackground() {
    if (isUnderNimbusLookAndFeel()) {
      return UIManager.getColor("List[Selected].textBackground");  // Nimbus
    }
    return UIManager.getColor("List.selectionBackground");
  }

  public static Color getListUnfocusedSelectionBackground() {
    return new JBColor(UNFOCUSED_SELECTION_COLOR, new Color(13, 41, 62));
  }

  public static Color getTreeSelectionBackground(boolean focused) {
    return focused ? getTreeSelectionBackground() : getTreeUnfocusedSelectionBackground();
  }

  public static Color getTreeUnfocusedSelectionBackground() {
    Color background = getTreeTextBackground();
    return ColorUtil.isDark(background) ? new JBColor(Gray._30, new Color(13, 41, 62)) : UNFOCUSED_SELECTION_COLOR;
  }

  public static Color getTextFieldForeground() {
    return UIManager.getColor("TextField.foreground");
  }

  public static Color getTextFieldBackground() {
    return UIManager.getColor(isUnderGTKLookAndFeel() ? "EditorPane.background" : "TextField.background");
  }

  public static Font getButtonFont() {
    return UIManager.getFont("Button.font");
  }

  public static Font getToolTipFont() {
    return UIManager.getFont("ToolTip.font");
  }

  public static Color getTabbedPaneBackground() {
    return UIManager.getColor("TabbedPane.background");
  }

  public static void setSliderIsFilled(final JSlider slider, final boolean value) {
    slider.putClientProperty("JSlider.isFilled", Boolean.valueOf(value));
  }

  public static Color getLabelTextForeground() {
    return UIManager.getColor("Label.textForeground");
  }

  public static Color getControlColor() {
    return UIManager.getColor("control");
  }

  public static Font getOptionPaneMessageFont() {
    return UIManager.getFont("OptionPane.messageFont");
  }

  public static Font getMenuFont() {
    return UIManager.getFont("Menu.font");
  }

  public static Color getSeparatorForeground() {
    return UIManager.getColor("Separator.foreground");
  }

  public static Color getSeparatorBackground() {
    return UIManager.getColor("Separator.background");
  }

  public static Color getSeparatorShadow() {
    return UIManager.getColor("Separator.shadow");
  }

  public static Color getSeparatorHighlight() {
    return UIManager.getColor("Separator.highlight");
  }

  public static Color getSeparatorColorUnderNimbus() {
    return UIManager.getColor("nimbusBlueGrey");
  }

  public static Color getSeparatorColor() {
    Color separatorColor = getSeparatorForeground();
    if (isUnderAlloyLookAndFeel()) {
      separatorColor = getSeparatorShadow();
    }
    if (isUnderNimbusLookAndFeel()) {
      separatorColor = getSeparatorColorUnderNimbus();
    }
    //under GTK+ L&F colors set hard
    if (isUnderGTKLookAndFeel()) {
      separatorColor = Gray._215;
    }
    return separatorColor;
  }

  public static Border getTableFocusCellHighlightBorder() {
    return UIManager.getBorder("Table.focusCellHighlightBorder");
  }

  public static void setLineStyleAngled(final ClientPropertyHolder component) {
    component.putClientProperty("JTree.lineStyle", "Angled");
  }

  public static void setLineStyleAngled(final JTree component) {
    component.putClientProperty("JTree.lineStyle", "Angled");
  }

  public static Color getTableFocusCellForeground() {
    return UIManager.getColor("Table.focusCellForeground");
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getPanelBackground() instead
   */
  public static Color getPanelBackgound() {
    return getPanelBackground();
  }

  public static Border getTextFieldBorder() {
    return UIManager.getBorder("TextField.border");
  }

  public static Border getButtonBorder() {
    return UIManager.getBorder("Button.border");
  }

  @NotNull
  public static Icon getErrorIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.errorIcon"), AllIcons.General.ErrorDialog);
  }

  @NotNull
  public static Icon getInformationIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.informationIcon"), AllIcons.General.InformationDialog);
  }

  @NotNull
  public static Icon getQuestionIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.questionIcon"), AllIcons.General.QuestionDialog);
  }

  @NotNull
  public static Icon getWarningIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.warningIcon"), AllIcons.General.WarningDialog);
  }

  public static Icon getBalloonInformationIcon() {
    return AllIcons.General.BalloonInformation;
  }

  public static Icon getBalloonWarningIcon() {
    return AllIcons.General.BalloonWarning;
  }

  public static Icon getBalloonErrorIcon() {
    return AllIcons.General.BalloonError;
  }

  public static Icon getRadioButtonIcon() {
    return UIManager.getIcon("RadioButton.icon");
  }

  public static Icon getTreeNodeIcon(boolean expanded, boolean selected, boolean focused) {
    boolean white = selected && focused || isUnderDarcula();

    Icon selectedIcon = getTreeSelectedExpandedIcon();
    Icon notSelectedIcon = getTreeExpandedIcon();

    int width = Math.max(selectedIcon.getIconWidth(), notSelectedIcon.getIconWidth());
    int height = Math.max(selectedIcon.getIconWidth(), notSelectedIcon.getIconWidth());

    return new CenteredIcon(expanded ? (white ? getTreeSelectedExpandedIcon() : getTreeExpandedIcon())
                                     : (white ? getTreeSelectedCollapsedIcon() : getTreeCollapsedIcon()),
                            width, height, false);
  }

  public static Icon getTreeCollapsedIcon() {
    return UIManager.getIcon("Tree.collapsedIcon");
  }

  public static Icon getTreeExpandedIcon() {
    return UIManager.getIcon("Tree.expandedIcon");
  }

  public static Icon getTreeIcon(boolean expanded) {
    return expanded ? getTreeExpandedIcon() : getTreeCollapsedIcon();
  }

  public static Icon getTreeSelectedCollapsedIcon() {
    if (isUnderAquaBasedLookAndFeel() ||
        isUnderNimbusLookAndFeel() ||
        isUnderGTKLookAndFeel() ||
        isUnderDarcula() ||
        isUnderIntelliJLaF() &&
        !isUnderWin10LookAndFeel()) {
      return AllIcons.Mac.Tree_white_right_arrow;
    }
    return getTreeCollapsedIcon();
  }

  public static Icon getTreeSelectedExpandedIcon() {
    if (isUnderAquaBasedLookAndFeel() ||
        isUnderNimbusLookAndFeel() ||
        isUnderGTKLookAndFeel() ||
        isUnderDarcula() ||
        isUnderIntelliJLaF() &&
        !isUnderWin10LookAndFeel()) {
      return AllIcons.Mac.Tree_white_down_arrow;
    }
    return getTreeExpandedIcon();
  }

  public static Border getTableHeaderCellBorder() {
    return UIManager.getBorder("TableHeader.cellBorder");
  }

  public static Color getWindowColor() {
    return UIManager.getColor("window");
  }

  public static Color getTextAreaForeground() {
    return UIManager.getColor("TextArea.foreground");
  }

  public static Color getOptionPaneBackground() {
    return UIManager.getColor("OptionPane.background");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderAlloyLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Alloy");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderAlloyIDEALookAndFeel() {
    return isUnderAlloyLookAndFeel() && UIManager.getLookAndFeel().getName().contains("IDEA");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderWindowsLookAndFeel() {
    return SystemInfo.isWindows && UIManager.getLookAndFeel().getName().equals("Windows");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderWindowsClassicLookAndFeel() {
    return UIManager.getLookAndFeel().getName().equals("Windows Classic");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderNimbusLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Nimbus");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderAquaLookAndFeel() {
    return SystemInfo.isMac && UIManager.getLookAndFeel().getName().contains("Mac OS X");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderJGoodiesLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("JGoodies");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderAquaBasedLookAndFeel() {
    return SystemInfo.isMac && (isUnderAquaLookAndFeel() || isUnderDarcula() || isUnderIntelliJLaF());
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderDarcula() {
    return UIManager.getLookAndFeel().getName().contains("Darcula");
  }

  public static boolean isUnderDefaultMacTheme() {
    return SystemInfo.isMac && isUnderIntelliJLaF();
  }

  public static boolean isUnderWin10LookAndFeel() {
    return SystemInfo.isWindows && isUnderIntelliJLaF() && Registry.is("ide.intellij.laf.win10.ui");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderIntelliJLaF() {
    return UIManager.getLookAndFeel().getName().contains("IntelliJ");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderGTKLookAndFeel() {
    return SystemInfo.isXWindow && UIManager.getLookAndFeel().getName().contains("GTK");
  }

  public static final Color GTK_AMBIANCE_TEXT_COLOR = new Color(223, 219, 210);
  public static final Color GTK_AMBIANCE_BACKGROUND_COLOR = new Color(67, 66, 63);

  @SuppressWarnings("HardCodedStringLiteral")
  @Nullable
  public static String getGtkThemeName() {
    final LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && "GTKLookAndFeel".equals(laf.getClass().getSimpleName())) {
      try {
        final Method method = laf.getClass().getDeclaredMethod("getGtkThemeName");
        method.setAccessible(true);
        final Object theme = method.invoke(laf);
        if (theme != null) {
          return theme.toString();
        }
      }
      catch (Exception ignored) {
      }
    }
    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isMurrineBasedTheme() {
    final String gtkTheme = getGtkThemeName();
    return "Ambiance".equalsIgnoreCase(gtkTheme) ||
           "Radiance".equalsIgnoreCase(gtkTheme) ||
           "Dust".equalsIgnoreCase(gtkTheme) ||
           "Dust Sand".equalsIgnoreCase(gtkTheme);
  }

  public static Color shade(final Color c, final double factor, final double alphaFactor) {
    assert factor >= 0 : factor;
    //noinspection UseJBColor
    return new Color(
      Math.min((int)Math.round(c.getRed() * factor), 255),
      Math.min((int)Math.round(c.getGreen() * factor), 255),
      Math.min((int)Math.round(c.getBlue() * factor), 255),
      Math.min((int)Math.round(c.getAlpha() * alphaFactor), 255)
    );
  }

  public static Color mix(final Color c1, final Color c2, final double factor) {
    assert 0 <= factor && factor <= 1.0 : factor;
    final double backFactor = 1.0 - factor;
    //noinspection UseJBColor
    return new Color(
      Math.min((int)Math.round(c1.getRed() * backFactor + c2.getRed() * factor), 255),
      Math.min((int)Math.round(c1.getGreen() * backFactor + c2.getGreen() * factor), 255),
      Math.min((int)Math.round(c1.getBlue() * backFactor + c2.getBlue() * factor), 255)
    );
  }

  public static boolean isFullRowSelectionLAF() {
    return isUnderGTKLookAndFeel();
  }

  public static boolean isUnderNativeMacLookAndFeel() {
    return isUnderAquaLookAndFeel() || isUnderDarcula();
  }

  public static int getListCellHPadding() {
    return isUnderNativeMacLookAndFeel() ? 7 : 2;
  }

  public static int getListCellVPadding() {
    return 1;
  }

  public static Insets getListCellPadding() {
    return new Insets(getListCellVPadding(), getListCellHPadding(), getListCellVPadding(), getListCellHPadding());
  }

  public static Insets getListViewportPadding() {
    return isUnderNativeMacLookAndFeel() ? new Insets(1, 0, 1, 0) : new Insets(5, 5, 5, 5);
  }

  public static boolean isToUseDottedCellBorder() {
    return !isUnderNativeMacLookAndFeel();
  }

  public static boolean isControlKeyDown(MouseEvent mouseEvent) {
    return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  public static String[] getValidFontNames(final boolean familyName) {
    Set<String> result = new TreeSet<String>();

    // adds fonts that can display symbols at [A, Z] + [a, z] + [0, 9]
    for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
      try {
        if (isValidFont(font)) {
          result.add(familyName ? font.getFamily() : font.getName());
        }
      }
      catch (Exception ignore) {
        // JRE has problems working with the font. Just skip.
      }
    }

    // add label font (if isn't listed among above)
    Font labelFont = getLabelFont();
    if (labelFont != null && isValidFont(labelFont)) {
      result.add(familyName ? labelFont.getFamily() : labelFont.getName());
    }

    return ArrayUtil.toStringArray(result);
  }

  public static String[] getStandardFontSizes() {
    return STANDARD_FONT_SIZES;
  }

  public static boolean isValidFont(@NotNull Font font) {
    try {
      return font.canDisplay('a') &&
             font.canDisplay('z') &&
             font.canDisplay('A') &&
             font.canDisplay('Z') &&
             font.canDisplay('0') &&
             font.canDisplay('1');
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  public static void setupEnclosingDialogBounds(final JComponent component) {
    component.revalidate();
    component.repaint();
    final Window window = SwingUtilities.windowForComponent(component);
    if (window != null &&
        (window.getSize().height < window.getMinimumSize().height || window.getSize().width < window.getMinimumSize().width)) {
      window.pack();
    }
  }

  public static String displayPropertiesToCSS(Font font, Color fg) {
    @NonNls StringBuilder rule = new StringBuilder("body {");
    if (font != null) {
      rule.append(" font-family: ");
      rule.append(font.getFamily());
      rule.append(" ; ");
      rule.append(" font-size: ");
      rule.append(font.getSize());
      rule.append("pt ;");
      if (font.isBold()) {
        rule.append(" font-weight: 700 ; ");
      }
      if (font.isItalic()) {
        rule.append(" font-style: italic ; ");
      }
    }
    if (fg != null) {
      rule.append(" color: #");
      appendColor(fg, rule);
      rule.append(" ; ");
    }
    rule.append(" }");
    return rule.toString();
  }

  public static void appendColor(final Color color, final StringBuilder sb) {
    if (color.getRed() < 16) sb.append('0');
    sb.append(Integer.toHexString(color.getRed()));
    if (color.getGreen() < 16) sb.append('0');
    sb.append(Integer.toHexString(color.getGreen()));
    if (color.getBlue() < 16) sb.append('0');
    sb.append(Integer.toHexString(color.getBlue()));
  }

  public static void drawDottedRectangle(Graphics g, Rectangle r) {
    drawDottedRectangle(g, r.x, r.y, r.x + r.width, r.y + r.height);
  }

  /**
   * @param g  graphics.
   * @param x  top left X coordinate.
   * @param y  top left Y coordinate.
   * @param x1 right bottom X coordinate.
   * @param y1 right bottom Y coordinate.
   */
  public static void drawDottedRectangle(Graphics g, int x, int y, int x1, int y1) {
    int i1;
    for (i1 = x; i1 <= x1; i1 += 2) {
      drawLine(g, i1, y, i1, y);
    }

    for (i1 = y + (i1 != x1 + 1 ? 2 : 1); i1 <= y1; i1 += 2) {
      drawLine(g, x1, i1, x1, i1);
    }

    for (i1 = x1 - (i1 != y1 + 1 ? 2 : 1); i1 >= x; i1 -= 2) {
      drawLine(g, i1, y1, i1, y1);
    }

    for (i1 = y1 - (i1 != x - 1 ? 2 : 1); i1 >= y; i1 -= 2) {
      drawLine(g, x, i1, x, i1);
    }
  }

  /**
   * Should be invoked only in EDT.
   *
   * @param g       Graphics surface
   * @param startX  Line start X coordinate
   * @param endX    Line end X coordinate
   * @param lineY   Line Y coordinate
   * @param bgColor Background color (optional)
   * @param fgColor Foreground color (optional)
   * @param opaque  If opaque the image will be dr
   */
  public static void drawBoldDottedLine(final Graphics2D g,
                                        final int startX,
                                        final int endX,
                                        final int lineY,
                                        final Color bgColor,
                                        final Color fgColor,
                                        final boolean opaque) {
    if (SystemInfo.isMac && !isRetina() || SystemInfo.isLinux) {
      drawAppleDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
    else {
      drawBoringDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
  }

  public static void drawSearchMatch(final Graphics2D g,
                                     final float startX,
                                     final float endX,
                                     final int height) {
    Color c1 = new Color(255, 234, 162);
    Color c2 = new Color(255, 208, 66);
    drawSearchMatch(g, startX, endX, height, c1, c2);
  }

  public static void drawSearchMatch(Graphics2D g, float startXf, float endXf, int height, Color c1, Color c2) {
    final boolean drawRound = endXf - startXf > 4;

    GraphicsConfig config = new GraphicsConfig(g);
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
    g.setPaint(getGradientPaint(startXf, 2, c1, startXf, height - 5, c2));

    if (isJreHiDPI(g)) {
      GraphicsConfig c = GraphicsUtil.setupRoundedBorderAntialiasing(g);
      g.fill(new RoundRectangle2D.Float(startXf, 2, endXf - startXf, height - 4, 5, 5));
      c.restore();
      config.restore();
      return;
    }

    int startX = (int)startXf;
    int endX = (int)endXf;

    g.fillRect(startX, 3, endX - startX, height - 5);

    if (drawRound) {
      g.drawLine(startX - 1, 4, startX - 1, height - 4);
      g.drawLine(endX, 4, endX, height - 4);

      g.setColor(new Color(100, 100, 100, 50));
      g.drawLine(startX - 1, 4, startX - 1, height - 4);
      g.drawLine(endX, 4, endX, height - 4);

      g.drawLine(startX, 3, endX - 1, 3);
      g.drawLine(startX, height - 3, endX - 1, height - 3);
    }

    config.restore();
  }

  public static void drawRectPickedOut(Graphics2D g, int x, int y, int w, int h) {
    g.drawLine(x + 1, y, x + w - 1, y);
    g.drawLine(x + w, y + 1, x + w, y + h - 1);
    g.drawLine(x + w - 1, y + h, x + 1, y + h);
    g.drawLine(x, y + 1, x, y + h - 1);
  }

  private static void drawBoringDottedLine(final Graphics2D g,
                                           final int startX,
                                           final int endX,
                                           final int lineY,
                                           final Color bgColor,
                                           final Color fgColor,
                                           final boolean opaque) {
    final Color oldColor = g.getColor();

    // Fill 2 lines with background color
    if (opaque && bgColor != null) {
      g.setColor(bgColor);

      drawLine(g, startX, lineY, endX, lineY);
      drawLine(g, startX, lineY + 1, endX, lineY + 1);
    }

    // Draw dotted line:
    //
    // CCC CCC CCC ...
    // CCC CCC CCC ...
    //
    // (where "C" - colored pixel, " " - white pixel)

    final int step = 4;
    final int startPosCorrection = startX % step < 3 ? 0 : 1;

    g.setColor(fgColor != null ? fgColor : oldColor);
    // Now draw bold line segments
    for (int dotXi = (startX / step + startPosCorrection) * step; dotXi < endX; dotXi += step) {
      g.drawLine(dotXi, lineY, dotXi + 1, lineY);
      g.drawLine(dotXi, lineY + 1, dotXi + 1, lineY + 1);
    }

    // restore color
    g.setColor(oldColor);
  }

  public static void drawGradientHToolbarBackground(final Graphics g, final int width, final int height) {
    final Graphics2D g2d = (Graphics2D)g;
    g2d.setPaint(getGradientPaint(0, 0, Gray._215, 0, height, Gray._200));
    g2d.fillRect(0, 0, width, height);
  }

  public static void drawHeader(Graphics g, int x, int width, int height, boolean active, boolean drawTopLine) {
    drawHeader(g, x, width, height, active, false, drawTopLine, true);
  }

  public static void drawHeader(Graphics g,
                                int x,
                                int width,
                                int height,
                                boolean active,
                                boolean toolWindow,
                                boolean drawTopLine,
                                boolean drawBottomLine) {
    height++;
    GraphicsConfig config = GraphicsUtil.disableAAPainting(g);
    try {
      g.setColor(getPanelBackground());
      g.fillRect(x, 0, width, height);

      boolean jmHiDPI = isJreHiDPI((Graphics2D)g);
      if (jmHiDPI) {
        ((Graphics2D)g).setStroke(new BasicStroke(2f));
      }
      ((Graphics2D)g).setPaint(getGradientPaint(0, 0, Gray.x00.withAlpha(5), 0, height, Gray.x00.withAlpha(20)));
      g.fillRect(x, 0, width, height);

      if (active) {
        g.setColor(new Color(100, 150, 230, toolWindow ? 50 : 30));
        g.fillRect(x, 0, width, height);
      }
      g.setColor(SystemInfo.isMac && isUnderIntelliJLaF() ? Gray.xC9 : Gray.x00.withAlpha(toolWindow ? 90 : 50));
      if (drawTopLine) g.drawLine(x, 0, width, 0);
      if (drawBottomLine) g.drawLine(x, height - (jmHiDPI ? 1 : 2), width, height - (jmHiDPI ? 1 : 2));

      if (SystemInfo.isMac && isUnderIntelliJLaF()) {
        g.setColor(Gray.xC9);
      } else {
        g.setColor(isUnderDarcula() ? CONTRAST_BORDER_COLOR : Gray.xFF.withAlpha(100));
      }

      g.drawLine(x, 0, width, 0);
    } finally {
      config.restore();
    }
  }

  public static void drawDoubleSpaceDottedLine(final Graphics2D g,
                                               final int start,
                                               final int end,
                                               final int xOrY,
                                               final Color fgColor,
                                               boolean horizontal) {

    g.setColor(fgColor);
    for (int dot = start; dot < end; dot += 3) {
      if (horizontal) {
        g.drawLine(dot, xOrY, dot, xOrY);
      }
      else {
        g.drawLine(xOrY, dot, xOrY, dot);
      }
    }
  }

  private static void drawAppleDottedLine(final Graphics2D g,
                                          final int startX,
                                          final int endX,
                                          final int lineY,
                                          final Color bgColor,
                                          final Color fgColor,
                                          final boolean opaque) {
    final Color oldColor = g.getColor();

    // Fill 3 lines with background color
    if (opaque && bgColor != null) {
      g.setColor(bgColor);

      drawLine(g, startX, lineY, endX, lineY);
      drawLine(g, startX, lineY + 1, endX, lineY + 1);
      drawLine(g, startX, lineY + 2, endX, lineY + 2);
    }

    AppleBoldDottedPainter painter = AppleBoldDottedPainter.forColor(ObjectUtils.notNull(fgColor, oldColor));
    painter.paint(g, startX, endX, lineY);
  }

  /** This method is intended to use when user settings are not accessible yet.
   *  Use it to set up default RenderingHints.
   */
  public static void applyRenderingHints(final Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    Toolkit tk = Toolkit.getDefaultToolkit();
    //noinspection HardCodedStringLiteral
    Map map = (Map)tk.getDesktopProperty("awt.font.desktophints");
    if (map != null) {
      g2d.addRenderingHints(map);
    }
  }

  /**
   * Creates a HiDPI-aware BufferedImage in device scale.
   *
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in device scale
   */
  @NotNull
  public static BufferedImage createImage(int width, int height, int type) {
    if (isJreHiDPI()) {
      return RetinaImage.create(width, height, type);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(width, height, type);
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the graphics config scale.
   *
   * @param gc the graphics config
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in the graphics scale
   */
  @NotNull
  public static BufferedImage createImage(GraphicsConfiguration gc, int width, int height, int type) {
    if (isJreHiDPI(gc)) {
      return RetinaImage.create(gc, width, height, type);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(width, height, type);
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the graphics device scale.
   *
   * @param g the graphics of the target device
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in the graphics scale
   */
  @NotNull
  public static BufferedImage createImage(Graphics g, int width, int height, int type) {
    if (g instanceof Graphics2D) {
      Graphics2D g2d = (Graphics2D)g;
      if (isJreHiDPI(g2d)) {
        return RetinaImage.create(g2d, width, height, type);
      }
      //noinspection UndesirableClassUsage
      return new BufferedImage(width, height, type);
    }
    return createImage(width, height, type);  }

  /**
   * Creates a HiDPI-aware BufferedImage in the component scale.
   *
   * @param comp the component associated with the target graphics device
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in the component scale
   */
  @NotNull
  public static BufferedImage createImage(Component comp, int width, int height, int type) {
    return comp != null ?
           createImage(comp.getGraphicsConfiguration(), width, height, type) :
           createImage(width, height, type);
  }

  /**
   * @deprecated use {@link #createImage(Graphics, int, int, int)}
   */
  @NotNull
  public static BufferedImage createImageForGraphics(Graphics2D g, int width, int height, int type) {
    return createImage(g, width, height, type);
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, ImageObserver)}
   */
  public static void drawImage(Graphics g, Image image, int x, int y, ImageObserver observer) {
    drawImage(g, image, x, y, -1, -1, observer);
  }


  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, int, int, ImageObserver)}
   * When {@code dstBounds} is null, the image bounds are used instead.
   */
  public static void drawImage(Graphics g, Image image, @Nullable Rectangle dstBounds, ImageObserver observer) {
      drawImage(g, image, dstBounds, null, observer);
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, int, int, int, int, int, int, ImageObserver)}
   * When {@code dstBounds} or {@code srcBounds} is null, the image bounds are used instead.
   */
  public static void drawImage(Graphics g, Image image, @Nullable Rectangle dstBounds, @Nullable Rectangle srcBounds, ImageObserver observer) {
    Image drawImage = image;
    if (image instanceof JBHiDPIScaledImage) {
      drawImage = ((JBHiDPIScaledImage)image).getDelegate();
      if (drawImage == null) {
        drawImage = image;
      }
    }
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
    if (dw == -1 && dh == -1) {
      dw = ImageUtil.getUserWidth(image);
      dh = ImageUtil.getUserHeight(image);
    }
    int sx = 0;
    int sy = 0;
    int sw = -1;
    int sh = -1;
    if (srcBounds != null) {
      sx = srcBounds.x;
      sy = srcBounds.y;
      sw = srcBounds.width;
      sh = srcBounds.height;
    }
    if (sw == -1 && sh == -1) {
      sw = ImageUtil.getRealWidth(image);
      sh = ImageUtil.getRealHeight(image);
    }
    g.drawImage(drawImage, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, observer);
  }

  /**
   * Note, the method interprets [x,y,width,height] as the destination and source bounds for hidpi-unaware images
   * which doesn't conform to the {@link Graphics#drawImage(Image, int, int, int, int, ImageObserver)} method contract.
   *
   * @deprecated use {@link #drawImage(Graphics, Image, Rectangle, Rectangle, ImageObserver)}
   */
  @Deprecated
  public static void drawImage(Graphics g, Image image, int x, int y, int width, int height, ImageObserver observer) {
    if (image instanceof JBHiDPIScaledImage) {
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img == null) {
        img = image;
      }
      int dstw = width;
      int dsth = height;
      if (width == -1 && height == -1) {
        dstw = ImageUtil.getUserWidth(image);
        dsth = ImageUtil.getUserHeight(image);
      }
      int srcw = ImageUtil.getRealWidth(image);
      int srch = ImageUtil.getRealHeight(image);
      g.drawImage(img, x, y, x + dstw, y + dsth, 0, 0, srcw, srch, observer);
    }
    else if (width == -1 && height == -1) {
      g.drawImage(image, x, y, observer);
    }
    else {
      g.drawImage(image, x, y, x + width, y + height, 0, 0, width, height, observer);
    }
  }

  public static void drawImage(Graphics g, BufferedImage image, BufferedImageOp op, int x, int y) {
    if (image instanceof JBHiDPIScaledImage) {
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img == null) {
        img = image;
      }
      if (op != null && img instanceof BufferedImage) img = op.filter((BufferedImage)img, null);
      int dstw = ImageUtil.getUserWidth(image);
      int dsth = ImageUtil.getUserHeight(image);
      int srcw = ImageUtil.getRealWidth(image);
      int srch = ImageUtil.getRealHeight(image);
      g.drawImage(img, x, y, x + dstw, y + dsth, 0, 0, srcw, srch, null);
    } else {
      ((Graphics2D)g).drawImage(image, op, x, y);
    }
  }

  public static void paintWithXorOnRetina(@NotNull Dimension size, @NotNull Graphics g, Consumer<Graphics2D> paintRoutine) {
    paintWithXorOnRetina(size, g, true, paintRoutine);
  }

  /**
   * Direct painting into component's graphics with XORMode is broken on retina-mode so we need to paint into an intermediate buffer first.
   */
  public static void paintWithXorOnRetina(@NotNull Dimension size,
                                          @NotNull Graphics g,
                                          boolean useRetinaCondition,
                                          Consumer<Graphics2D> paintRoutine) {
    if (!useRetinaCondition || !isJreHiDPI((Graphics2D)g) || Registry.is("ide.mac.retina.disableDrawingFix")) {
      paintRoutine.consume((Graphics2D)g);
    }
    else {
      Rectangle rect = g.getClipBounds();
      if (rect == null) rect = new Rectangle(size);

      //noinspection UndesirableClassUsage
      Image image = new BufferedImage(rect.width * 2, rect.height * 2, BufferedImage.TYPE_INT_RGB);
      Graphics2D imageGraphics = (Graphics2D)image.getGraphics();

      imageGraphics.scale(2, 2);
      imageGraphics.translate(-rect.x, -rect.y);
      imageGraphics.setClip(rect.x, rect.y, rect.width, rect.height);

      paintRoutine.consume(imageGraphics);
      image.flush();
      imageGraphics.dispose();

      ((Graphics2D)g).scale(0.5, 0.5);
      g.drawImage(image, rect.x * 2, rect.y * 2, null);
    }
  }

  /**
   * Configures composite to use for drawing text with the given graphics container.
   * <p/>
   * The whole idea is that <a href="http://en.wikipedia.org/wiki/X_Rendering_Extension">XRender-based</a> pipeline doesn't support
   * {@link AlphaComposite#SRC} and we should use {@link AlphaComposite#SRC_OVER} instead.
   *
   * @param g target graphics container
   */
  public static void setupComposite(@NotNull Graphics2D g) {
    g.setComposite(X_RENDER_ACTIVE.getValue() ? AlphaComposite.SrcOver : AlphaComposite.Src);
  }

  /** @see #pump() */
  @TestOnly
  public static void dispatchAllInvocationEvents() {
    //noinspection StatementWithEmptyBody
    while(dispatchInvocationEvent());
  }

  @TestOnly
  public static boolean dispatchInvocationEvent() {
    assert EdtInvocationManager.getInstance().isEventDispatchThread() : Thread.currentThread() + "; EDT: "+getEventQueueThread();
    final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    AWTEvent event = eventQueue.peekEvent();
    if (event == null) return false;
    try {
      event = eventQueue.getNextEvent();
      if (event instanceof InvocationEvent) {
        eventQueue.getClass().getDeclaredMethod("dispatchEvent", AWTEvent.class).invoke(eventQueue, event);
      }
    }
    catch (InvocationTargetException e) {
      ExceptionUtil.rethrowAllAsUnchecked(e.getCause());
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return true;
  }

  private static Thread getEventQueueThread() {
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    try {
      Method method = ReflectionUtil.getDeclaredMethod(EventQueue.class, "getDispatchThread");
      return (Thread)method.invoke(eventQueue);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** @see #dispatchAllInvocationEvents() */
  @TestOnly
  public static void pump() {
    assert !SwingUtilities.isEventDispatchThread();
    final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        queue.offer(queue);
      }
    });
    try {
      queue.take();
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
  }

  public static void addAwtListener(final AWTEventListener listener, long mask, Disposable parent) {
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
      }
    });
  }

  public static void addParentChangeListener(@NotNull Component component, @NotNull PropertyChangeListener listener) {
    component.addPropertyChangeListener("ancestor", listener);
  }

  public static void removeParentChangeListener(@NotNull Component component, @NotNull PropertyChangeListener listener) {
    component.removePropertyChangeListener("ancestor", listener);
  }

  public static void drawVDottedLine(Graphics2D g, int lineX, int startY, int endY, @Nullable final Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      drawLine(g, lineX, startY, lineX, endY);
    }

    g.setColor(fgColor);
    for (int i = (startY / 2) * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  public static void drawHDottedLine(Graphics2D g, int startX, int endX, int lineY, @Nullable final Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      drawLine(g, startX, lineY, endX, lineY);
    }

    g.setColor(fgColor);

    for (int i = (startX / 2) * 2; i < endX; i += 2) {
      g.drawRect(i, lineY, 0, 0);
    }
  }

  public static void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2, @Nullable final Color bgColor, final Color fgColor) {
    if (x1 == x2) {
      drawVDottedLine(g, x1, y1, y2, bgColor, fgColor);
    }
    else if (y1 == y2) {
      drawHDottedLine(g, x1, x2, y1, bgColor, fgColor);
    }
    else {
      throw new IllegalArgumentException("Only vertical or horizontal lines are supported");
    }
  }

  public static void drawStringWithHighlighting(Graphics g, String s, int x, int y, Color foreground, Color highlighting) {
    g.setColor(highlighting);
    boolean isRetina = isJreHiDPI((Graphics2D)g);
    float scale = 1 / JBUI.sysScale((Graphics2D)g);
    for (float i = x - 1; i <= x + 1; i += isRetina ? scale : 1) {
      for (float j = y - 1; j <= y + 1; j += isRetina ? scale : 1) {
        ((Graphics2D)g).drawString(s, i, j);
      }
    }
    g.setColor(foreground);
    g.drawString(s, x, y);
  }

  /**
   * Draws a centered string in the passed rectangle.
   * @param g the {@link Graphics} instance to draw to
   * @param rect the {@link Rectangle} to use as bounding box
   * @param str the string to draw
   * @param horzCentered if true, the string will be centered horizontally
   * @param vertCentered if true, the string will be centered vertically
   */
  public static void drawCenteredString(Graphics2D g, Rectangle rect, String str, boolean horzCentered, boolean vertCentered) {
    FontMetrics fm = g.getFontMetrics(g.getFont());
    int textWidth = fm.stringWidth(str) - 1;
    int x = horzCentered ? Math.max(rect.x, rect.x + (rect.width - textWidth) / 2) : rect.x;
    int y = vertCentered ? Math.max(rect.y, rect.y + rect.height / 2 + fm.getAscent() * 2 / 5) : rect.y;
    Shape oldClip = g.getClip();
    g.clip(rect);
    g.drawString(str, x, y);
    g.setClip(oldClip);
  }

  /**
   * Draws a centered string in the passed rectangle.
   * @param g the {@link Graphics} instance to draw to
   * @param rect the {@link Rectangle} to use as bounding box
   * @param str the string to draw
   */
  public static void drawCenteredString(Graphics2D g, Rectangle rect, String str) {
    drawCenteredString(g, rect, str, true, true);
  }

  public static boolean isFocusAncestor(@NotNull final JComponent component) {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) return false;
    if (owner == component) return true;
    return SwingUtilities.isDescendingFrom(owner, component);
  }


  public static boolean isCloseClick(MouseEvent e) {
    return isCloseClick(e, MouseEvent.MOUSE_PRESSED);
  }

  public static boolean isCloseClick(MouseEvent e, int effectiveType) {
    if (e.isPopupTrigger() || e.getID() != effectiveType) return false;
    return e.getButton() == MouseEvent.BUTTON2 || e.getButton() == MouseEvent.BUTTON1 && e.isShiftDown();
  }

  public static boolean isActionClick(MouseEvent e) {
    return isActionClick(e, MouseEvent.MOUSE_PRESSED);
  }

  public static boolean isActionClick(MouseEvent e, int effectiveType) {
    return isActionClick(e, effectiveType, false);
  }

  public static boolean isActionClick(MouseEvent e, int effectiveType, boolean allowShift) {
    if (!allowShift && isCloseClick(e) || e.isPopupTrigger() || e.getID() != effectiveType) return false;
    return e.getButton() == MouseEvent.BUTTON1;
  }

  @NotNull
  public static Color getBgFillColor(@NotNull Component c) {
    final Component parent = findNearestOpaque(c);
    return parent == null ? c.getBackground() : parent.getBackground();
  }

  @Nullable
  public static Component findNearestOpaque(Component c) {
    return findParentByCondition(c, new Condition<Component>() {
      @Override
      public boolean value(Component component) {
        return component.isOpaque();
      }
    });
  }

  @Nullable
  public static Component findParentByCondition(@Nullable Component c, @NotNull Condition<Component> condition) {
    Component eachParent = c;
    while (eachParent != null) {
      if (condition.value(eachParent)) return eachParent;
      eachParent = eachParent.getParent();
    }
    return null;
  }

  @Deprecated
  public static <T extends Component> T findParentByClass(@NotNull Component c, Class<T> cls) {
    return getParentOfType(cls, c);
  }

  //x and y should be from {0, 0} to {parent.getWidth(), parent.getHeight()}
  @Nullable
  public static Component getDeepestComponentAt(@NotNull Component parent, int x, int y) {
    Component component = SwingUtilities.getDeepestComponentAt(parent, x, y);
    if (component != null && component.getParent() instanceof JRootPane) {//GlassPane case
      JRootPane rootPane = (JRootPane)component.getParent();
      Point point = SwingUtilities.convertPoint(parent, new Point(x, y), rootPane.getLayeredPane());
      component = SwingUtilities.getDeepestComponentAt(rootPane.getLayeredPane(), point.x, point.y);
      if (component == null) {
        point = SwingUtilities.convertPoint(parent, new Point(x, y), rootPane.getContentPane());
        component = SwingUtilities.getDeepestComponentAt(rootPane.getContentPane(), point.x, point.y);
      }
    }
    return component;
  }

  @Language("HTML")
  public static String getCssFontDeclaration(@NotNull Font font) {
    return getCssFontDeclaration(font, null, null, null);
  }

  @Language("HTML")
  public static String getCssFontDeclaration(@NotNull Font font, @Nullable Color fgColor, @Nullable Color linkColor, @Nullable String liImg) {
    StringBuilder builder = new StringBuilder().append("<style>\n");
    String familyAndSize = "font-family:'" + font.getFamily() + "'; font-size:" + font.getSize() + "pt;";

    builder.append("body, div, td, p {").append(familyAndSize);
    if (fgColor != null) builder.append(" color:#").append(ColorUtil.toHex(fgColor)).append(';');
    builder.append("}\n");

    builder.append("a {").append(familyAndSize);
    if (linkColor != null) builder.append(" color:#").append(ColorUtil.toHex(linkColor)).append(';');
    builder.append("}\n");

    builder.append("code {font-size:").append(font.getSize()).append("pt;}\n");

    URL resource = liImg != null ? SystemInfo.class.getResource(liImg) : null;
    if (resource != null) {
      builder.append("ul {list-style-image:url('").append(StringUtil.escapeCharCharacters(resource.toExternalForm())).append("');}\n");
    }

    return builder.append("</style>").toString();
  }

  public static boolean isWinLafOnVista() {
    return SystemInfo.isWinVistaOrNewer && "Windows".equals(UIManager.getLookAndFeel().getName());
  }

  public static boolean isStandardMenuLAF() {
    return isWinLafOnVista() ||
           isUnderNimbusLookAndFeel() ||
           isUnderGTKLookAndFeel();
  }

  public static Color getFocusedFillColor() {
    return toAlpha(getListSelectionBackground(), 100);
  }

  public static Color getFocusedBoundsColor() {
    return getBoundsColor();
  }

  public static Color getBoundsColor() {
    return getBorderColor();
  }

  public static Color getBoundsColor(boolean focused) {
    return focused ? getFocusedBoundsColor() : getBoundsColor();
  }

  public static Color toAlpha(final Color color, final int alpha) {
    Color actual = color != null ? color : Color.black;
    return new Color(actual.getRed(), actual.getGreen(), actual.getBlue(), alpha);
  }

  /**
   * @param component to check whether it can be focused or not
   * @return {@code true} if component is not {@code null} and can be focused
   * @see Component#isRequestFocusAccepted(boolean, boolean, sun.awt.CausedFocusEvent.Cause)
   */
  public static boolean isFocusable(JComponent component) {
    return component != null && component.isFocusable() && component.isEnabled() && component.isShowing();
  }

  @Deprecated
  public static void requestFocus(@NotNull final JComponent c) {
    if (c.isShowing()) {
      c.requestFocus();
    }
    else {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          c.requestFocus();
        }
      });
    }
  }

  //todo maybe should do for all kind of listeners via the AWTEventMulticaster class

  public static void dispose(final Component c) {
    if (c == null) return;

    final MouseListener[] mouseListeners = c.getMouseListeners();
    for (MouseListener each : mouseListeners) {
      c.removeMouseListener(each);
    }

    final MouseMotionListener[] motionListeners = c.getMouseMotionListeners();
    for (MouseMotionListener each : motionListeners) {
      c.removeMouseMotionListener(each);
    }

    final MouseWheelListener[] mouseWheelListeners = c.getMouseWheelListeners();
    for (MouseWheelListener each : mouseWheelListeners) {
      c.removeMouseWheelListener(each);
    }

    if (c instanceof AbstractButton) {
      final ActionListener[] listeners = ((AbstractButton)c).getActionListeners();
      for (ActionListener listener : listeners) {
        ((AbstractButton)c).removeActionListener(listener);
      }
    }
  }

  public static void disposeProgress(final JProgressBar progress) {
    if (!isUnderNativeMacLookAndFeel()) return;

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        progress.setUI(null);
      }
    });
  }

  @Nullable
  public static Component findUltimateParent(Component c) {
    if (c == null) return null;

    Component eachParent = c;
    while (true) {
      if (eachParent.getParent() == null) return eachParent;
      eachParent = eachParent.getParent();
    }
  }

  public static Color getHeaderActiveColor() {
    return ACTIVE_HEADER_COLOR;
  }

  public static Color getHeaderInactiveColor() {
    return INACTIVE_HEADER_COLOR;
  }

  /**
   * @deprecated use {@link JBColor#border()}
   */
  public static Color getBorderColor() {
    return isUnderDarcula() ? Gray._50 : BORDER_COLOR;
  }

  public static Font getTitledBorderFont() {
    Font defFont = getLabelFont();
    return defFont.deriveFont(defFont.getSize() - 1f);
  }

  /**
   * @deprecated use getBorderColor instead
   */
  public static Color getBorderInactiveColor() {
    return getBorderColor();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  public static Color getBorderActiveColor() {
    return getBorderColor();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  public static Color getBorderSeparatorColor() {
    return getBorderColor();
  }

  @Nullable
  public static StyleSheet loadStyleSheet(@Nullable URL url) {
    if (url == null) return null;
    try {
      StyleSheet styleSheet = new StyleSheet();
      styleSheet.loadRules(new InputStreamReader(url.openStream(), CharsetToolkit.UTF8), url);
      return styleSheet;
    }
    catch (IOException e) {
      LOG.warn(url + " loading failed", e);
      return null;
    }
  }

  public static HTMLEditorKit getHTMLEditorKit() {
    return getHTMLEditorKit(true);
  }

  public static HTMLEditorKit getHTMLEditorKit(boolean noGapsBetweenParagraphs) {
    return new JBHtmlEditorKit(noGapsBetweenParagraphs);
  }

  public static class JBHtmlEditorKit extends HTMLEditorKit {
    private final StyleSheet style;

    public JBHtmlEditorKit() {
      this(true);
    }

    public JBHtmlEditorKit(boolean noGapsBetweenParagraphs) {
      style = createStyleSheet();
      if (noGapsBetweenParagraphs) style.addRule("p { margin-top: 0; }");
    }

    @Override
    public StyleSheet getStyleSheet() {
      return style;
    }

    public static StyleSheet createStyleSheet() {
      StyleSheet style = new StyleSheet();
      style.addStyleSheet(isUnderDarcula() ? (StyleSheet)UIManager.getDefaults().get("StyledEditorKit.JBDefaultStyle") : DEFAULT_HTML_KIT_CSS);
      style.addRule("code { font-size: 100%; }"); // small by Swing's default
      style.addRule("small { font-size: small; }"); // x-small by Swing's default
      return style;
    }

    @Override
    public void install(final JEditorPane pane) {
      super.install(pane);
      if (pane != null) {
        // JEditorPane.HONOR_DISPLAY_PROPERTIES must be set after HTMLEditorKit is completely installed
        pane.addPropertyChangeListener("editorKit", new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent e) {
            Font font = getLabelFont();
            assert font instanceof FontUIResource;
            if (SystemInfo.isWindows) {
              font = getFontWithFallback("Tahoma", font.getStyle(), font.getSize());
            }
            // In case JBUI user scale factor changes, the font will be auto-updated by BasicTextUI.installUI()
            // with a font of the properly scaled size. And is then propagated to CSS, making HTML text scale dynamically.
            pane.setFont(font);

            // let CSS font properties inherit from the pane's font
            pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            pane.removePropertyChangeListener(this);
          }
        });
      }
    }
  }

  public static FontUIResource getFontWithFallback(@NotNull Font font) {
    return getFontWithFallback(font.getFamily(), font.getStyle(), font.getSize());
  }

  public static FontUIResource getFontWithFallback(@NotNull String familyName, @JdkConstants.FontStyle int style, int size) {
    Font fontWithFallback = new StyleContext().getFont(familyName, style, size);
    return fontWithFallback instanceof FontUIResource ? (FontUIResource)fontWithFallback : new FontUIResource(fontWithFallback);
  }

  //Escape error-prone HTML data (if any) when we use it in renderers, see IDEA-170768
  public static <T> T htmlInjectionGuard(T toRender) {
    if (toRender instanceof String && ((String)toRender).toLowerCase(Locale.US).startsWith("<html>")) {
      //noinspection unchecked
      return (T) ("<html>" + StringUtil.escapeXml((String)toRender));
    }
    return toRender;
  }

  public static void removeScrollBorder(final Component c) {
    for (JScrollPane scrollPane : uiTraverser(c).filter(JScrollPane.class)) {
      if (!uiParents(scrollPane, true)
        .takeWhile(Conditions.notEqualTo(c))
        .filter(Conditions.not(Conditions.instanceOf(JPanel.class, JLayeredPane.class)))
        .isEmpty()) continue;

      Integer keepBorderSides = getClientProperty(scrollPane, KEEP_BORDER_SIDES);
      if (keepBorderSides != null) {
        if (scrollPane.getBorder() instanceof LineBorder) {
          Color color = ((LineBorder)scrollPane.getBorder()).getLineColor();
          scrollPane.setBorder(new SideBorder(color, keepBorderSides.intValue()));
        }
        else {
          scrollPane.setBorder(new SideBorder(getBoundsColor(), keepBorderSides.intValue()));
        }
      }
      else {
        scrollPane.setBorder(new SideBorder(getBoundsColor(), SideBorder.NONE));
      }
    }
  }

  public static Point getCenterPoint(Dimension container, Dimension child) {
    return getCenterPoint(new Rectangle(container), child);
  }

  public static Point getCenterPoint(Rectangle container, Dimension child) {
    return new Point(
      container.x + (container.width - child.width) / 2,
      container.y + (container.height - child.height) / 2
    );
  }

  public static String toHtml(String html) {
    return toHtml(html, 0);
  }

  @NonNls
  public static String toHtml(String html, final int hPadding) {
    html = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
    Font font = getLabelFont();
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : JBUI.scale(11);
    return "<html><style>body { font-family: "
           + family + "; font-size: "
           + size + ";} ul li {list-style-type:circle;}</style>"
           + addPadding(html, hPadding) + "</html>";
  }

  public static String addPadding(final String html, int hPadding) {
    return String.format("<p style=\"margin: 0 %dpx 0 %dpx;\">%s</p>", hPadding, hPadding, html);
  }

  @NotNull
  public static String convertSpace2Nbsp(@NotNull String html) {
    @NonNls StringBuilder result = new StringBuilder();
    int currentPos = 0;
    int braces = 0;
    while (currentPos < html.length()) {
      String each = html.substring(currentPos, currentPos + 1);
      if ("<".equals(each)) {
        braces++;
      }
      else if (">".equals(each)) {
        braces--;
      }

      if (" ".equals(each) && braces == 0) {
        result.append("&nbsp;");
      }
      else {
        result.append(each);
      }
      currentPos++;
    }

    return result.toString();
  }

  /**
   * Please use Application.invokeLater() with a modality state (or GuiUtils, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings. For those, use GuiUtils, application.invoke* or TransactionGuard methods.<p/>
   *
   * On AWT thread, invoked runnable immediately, otherwise do {@link SwingUtilities#invokeLater(Runnable)} on it.
   */
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    if (EdtInvocationManager.getInstance().isEventDispatchThread()) {
      runnable.run();
    }
    else {
      EdtInvocationManager.getInstance().invokeLater(runnable);
    }
  }

  /**
   * Please use Application.invokeAndWait() with a modality state (or GuiUtils, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings.<p/>
   *
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   * DO NOT INVOKE THIS METHOD FROM UNDER READ ACTION.
   *
   * @param runnable a runnable to invoke
   * @see #invokeAndWaitIfNeeded(ThrowableRunnable)
   */
  public static void invokeAndWaitIfNeeded(@NotNull Runnable runnable) {
    if (EdtInvocationManager.getInstance().isEventDispatchThread()) {
      runnable.run();
    }
    else {
      try {
        EdtInvocationManager.getInstance().invokeAndWait(runnable);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  /**
   * Please use Application.invokeAndWait() with a modality state (or GuiUtils, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings.<p/>
   *
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   * DO NOT INVOKE THIS METHOD FROM UNDER READ ACTION.
   *
   * @param computable a runnable to invoke
   * @see #invokeAndWaitIfNeeded(ThrowableRunnable)
   */
  public static <T> T invokeAndWaitIfNeeded(@NotNull final Computable<T> computable) {
    final Ref<T> result = Ref.create();
    invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        result.set(computable.compute());
      }
    });
    return result.get();
  }

  /**
   * Please use Application.invokeAndWait() with a modality state (or GuiUtils, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings.<p/>
   *
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   * DO NOT INVOKE THIS METHOD FROM UNDER READ ACTION.
   *
   * @param runnable a runnable to invoke
   * @see #invokeAndWaitIfNeeded(ThrowableRunnable)
   */
  public static void invokeAndWaitIfNeeded(@NotNull final ThrowableRunnable runnable) throws Throwable {
    if (EdtInvocationManager.getInstance().isEventDispatchThread()) {
      runnable.run();
    }
    else {
      final Ref<Throwable> ref = Ref.create();
      EdtInvocationManager.getInstance().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          try {
            runnable.run();
          }
          catch (Throwable throwable) {
            ref.set(throwable);
          }
        }
      });
      if (!ref.isNull()) throw ref.get();
    }
  }

  public static boolean isFocusProxy(@Nullable Component c) {
    return c instanceof JComponent && Boolean.TRUE.equals(((JComponent)c).getClientProperty(FOCUS_PROXY_KEY));
  }

  public static void setFocusProxy(JComponent c, boolean isProxy) {
    c.putClientProperty(FOCUS_PROXY_KEY, isProxy ? Boolean.TRUE : null);
  }

  public static void maybeInstall(InputMap map, String action, KeyStroke stroke) {
    if (map.get(stroke) == null) {
      map.put(stroke, action);
    }
  }

  /**
   * Avoid blinking while changing background.
   *
   * @param component  component.
   * @param background new background.
   */
  public static void changeBackGround(final Component component, final Color background) {
    final Color oldBackGround = component.getBackground();
    if (background == null || !background.equals(oldBackGround)) {
      component.setBackground(background);
    }
  }

  private static String systemLaFClassName;

  public static String getSystemLookAndFeelClassName() {
    if (systemLaFClassName != null) {
      return systemLaFClassName;
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
          return systemLaFClassName = name;
        }
      }
      catch (Exception ignore) {
      }
    }
    return systemLaFClassName = UIManager.getSystemLookAndFeelClassName();
  }

  public static void initDefaultLAF() {
    try {
      UIManager.setLookAndFeel(getSystemLookAndFeelClassName());
      initSystemFontData();
    }
    catch (Exception ignore) {}
  }

  public static void initSystemFontData() {
    if (ourSystemFontData != null) return;

    // With JB Linux JDK the label font comes properly scaled based on Xft.dpi settings.
    Font font = getLabelFont();

    Float forcedScale = null;
    if (Registry.is("ide.ui.scale.override")) {
      forcedScale = Float.valueOf((float)Registry.get("ide.ui.scale").asDouble());
    }
    else if (SystemInfo.isLinux && !SystemInfo.isJetBrainsJvm) {
      // With Oracle JDK: derive scale from X server DPI
      float scale = getScreenScale();
      if (scale > 1f) {
        forcedScale = Float.valueOf(scale);
      }
      // Or otherwise leave the detected font. It's undetermined if it's scaled or not.
      // If it is (likely with GTK DE), then the UI scale will be derived from it,
      // if it's not, then IDEA will start unscaled. This lets the users of GTK DEs
      // not to bother about X server DPI settings. Users of other DEs (like KDE)
      // will have to set X server DPI to meet their display.
    }
    else if (SystemInfo.isWindows) {
      //noinspection HardCodedStringLiteral
      Font winFont = (Font)Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
      if (winFont != null) {
        font = winFont; // comes scaled
      }
    }
    if (forcedScale != null) {
      // With forced scale, we derive font from a hard-coded value as we cannot be sure
      // the system font comes unscaled.
      font = font.deriveFont(DEF_SYSTEM_FONT_SIZE * forcedScale.floatValue());
    }
    ourSystemFontData = Pair.create(font.getName(), font.getSize());
  }

  @Nullable
  public static Pair<String, Integer> getSystemFontData() {
    return ourSystemFontData;
  }

  private static float getScreenScale() {
    int dpi = 96;
    try {
      dpi = Toolkit.getDefaultToolkit().getScreenResolution();
    }
    catch (HeadlessException ignored) {
    }
    float scale = 1f;
    if (dpi < 120) scale = 1f;
    else if (dpi < 144) scale = 1.25f;
    else if (dpi < 168) scale = 1.5f;
    else if (dpi < 192) scale = 1.75f;
    else scale = 2f;

    return scale;
  }

  public static void addKeyboardShortcut(final JComponent target, final AbstractButton button, final KeyStroke keyStroke) {
    target.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (button.isEnabled()) {
            button.doClick();
          }
        }
      },
      keyStroke,
      JComponent.WHEN_FOCUSED
    );
  }

  public static void installComboBoxCopyAction(JComboBox comboBox) {
    final ComboBoxEditor editor = comboBox.getEditor();
    final Component editorComponent = editor != null ? editor.getEditorComponent() : null;
    if (!(editorComponent instanceof JTextComponent)) return;
    final InputMap inputMap = ((JTextComponent)editorComponent).getInputMap();
    for (KeyStroke keyStroke : inputMap.allKeys()) {
      if (DefaultEditorKit.copyAction.equals(inputMap.get(keyStroke))) {
        comboBox.getInputMap().put(keyStroke, DefaultEditorKit.copyAction);
      }
    }
    comboBox.getActionMap().put(DefaultEditorKit.copyAction, new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (!(e.getSource() instanceof JComboBox)) return;
        final JComboBox comboBox = (JComboBox)e.getSource();
        final String text;
        final Object selectedItem = comboBox.getSelectedItem();
        if (selectedItem instanceof String) {
          text = (String)selectedItem;
        }
        else {
          final Component component =
            comboBox.getRenderer().getListCellRendererComponent(new JList(), selectedItem, 0, false, false);
          if (component instanceof JLabel) {
            text = ((JLabel)component).getText();
          }
          else if (component != null) {
            final String str = component.toString();
            // skip default Component.toString and handle SimpleColoredComponent case
            text = str == null || str.startsWith(component.getClass().getName() + "[") ? null : str;
          }
          else {
            text = null;
          }
        }
        if (text != null) {
          final JTextField textField = new JTextField(text);
          textField.selectAll();
          textField.copy();
        }
      }
    });
  }

  @Nullable
  public static ComboPopup getComboBoxPopup(@NotNull JComboBox comboBox) {
    final ComboBoxUI ui = comboBox.getUI();
    if (ui instanceof BasicComboBoxUI) {
      return ReflectionUtil.getField(BasicComboBoxUI.class, ui, ComboPopup.class, "popup");
    }

    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static void fixFormattedField(JFormattedTextField field) {
    if (SystemInfo.isMac) {
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      final int commandKeyMask = toolkit.getMenuShortcutKeyMask();
      final InputMap inputMap = field.getInputMap();
      final KeyStroke copyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, commandKeyMask);
      inputMap.put(copyKeyStroke, "copy-to-clipboard");
      final KeyStroke pasteKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_V, commandKeyMask);
      inputMap.put(pasteKeyStroke, "paste-from-clipboard");
      final KeyStroke cutKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_X, commandKeyMask);
      inputMap.put(cutKeyStroke, "cut-to-clipboard");
    }
  }

  public static boolean isPrinting(Graphics g) {
    return g instanceof PrintGraphics;
  }

  public static int getSelectedButton(ButtonGroup group) {
    Enumeration<AbstractButton> enumeration = group.getElements();
    int i = 0;
    while (enumeration.hasMoreElements()) {
      AbstractButton button = enumeration.nextElement();
      if (group.isSelected(button.getModel())) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public static void setSelectedButton(ButtonGroup group, int index) {
    Enumeration<AbstractButton> enumeration = group.getElements();
    int i = 0;
    while (enumeration.hasMoreElements()) {
      AbstractButton button = enumeration.nextElement();
      group.setSelected(button.getModel(), index == i);
      i++;
    }
  }

  public static boolean isSelectionButtonDown(@NotNull MouseEvent e) {
    return e.isShiftDown() || e.isControlDown() || e.isMetaDown();
  }

  public static boolean isToggleListSelectionEvent(@NotNull MouseEvent e) {
    return SwingUtilities.isLeftMouseButton(e) && (SystemInfo.isMac ? e.isMetaDown() : e.isControlDown()) && !e.isPopupTrigger();
  }

  @SuppressWarnings("deprecation")
  public static void setComboBoxEditorBounds(int x, int y, int width, int height, JComponent editor) {
    if (SystemInfo.isMac && isUnderAquaLookAndFeel()) {
      // fix for too wide combobox editor, see AquaComboBoxUI.layoutContainer:
      // it adds +4 pixels to editor width. WTF?!
      editor.reshape(x, y, width - 4, height - 1);
    }
    else {
      editor.reshape(x, y, width, height);
    }
  }

  public static int fixComboBoxHeight(final int height) {
    return SystemInfo.isMac && isUnderAquaLookAndFeel() ? 28 : height;
  }

  public static final int LIST_FIXED_CELL_HEIGHT = 20;

  /**
   * The main difference from javax.swing.SwingUtilities#isDescendingFrom(Component, Component) is that this method
   * uses getInvoker() instead of getParent() when it meets JPopupMenu
   * @param child child component
   * @param parent parent component
   * @return true if parent if a top parent of child, false otherwise
   *
   * @see SwingUtilities#isDescendingFrom(Component, Component)
   */
  public static boolean isDescendingFrom(@Nullable Component child, @NotNull Component parent) {
    while (child != null && child != parent) {
      child =  child instanceof JPopupMenu  ? ((JPopupMenu)child).getInvoker()
                                            : child.getParent();
    }
    return child == parent;
  }

  /**
   * Searches above in the component hierarchy starting from the specified component.
   * Note that the initial component is also checked.
   *
   * @param type      expected class
   * @param component initial component
   * @return a component of the specified type, or {@code null} if the search is failed
   * @see SwingUtilities#getAncestorOfClass
   */
  @Nullable
  public static <T> T getParentOfType(@NotNull Class<? extends T> type, Component component) {
    while (component != null) {
      if (type.isInstance(component)) {
        //noinspection unchecked
        return (T)component;
      }
      component = component.getParent();
    }
    return null;
  }

  @NotNull
  public static JBIterable<Component> uiParents(@Nullable Component c, boolean strict) {
    return strict ? JBIterable.generate(c, COMPONENT_PARENT).skip(1) : JBIterable.generate(c, COMPONENT_PARENT);
  }

  @NotNull
  public static JBIterable<Component> uiChildren(@Nullable Component component) {
    if (!(component instanceof Container)) return JBIterable.empty();
    Container container = (Container)component;
    return JBIterable.of(container.getComponents());
  }

  @NotNull
  public static JBTreeTraverser<Component> uiTraverser(@Nullable Component component) {
    return new JBTreeTraverser<Component>(COMPONENT_CHILDREN).withRoot(component);
  }

  public static final Key<Iterable<? extends Component>> NOT_IN_HIERARCHY_COMPONENTS = Key.create("NOT_IN_HIERARCHY_COMPONENTS");

  private static final Function<Component, JBIterable<Component>> COMPONENT_CHILDREN = new Function<Component, JBIterable<Component>>() {
    @Override
    public JBIterable<Component> fun(@NotNull Component c) {
      JBIterable<Component> result;
      if (c instanceof JMenu) {
        result = JBIterable.of(((JMenu)c).getMenuComponents());
      }
      else if (c instanceof JComboBox && isUnderAquaLookAndFeel()) {
        // On Mac JComboBox instances have children: com.apple.laf.AquaComboBoxButton and javax.swing.CellRendererPane.
        // Disabling these children results in ugly UI: WEB-10733
        result = JBIterable.empty();
      }
      else {
        result = uiChildren(c);
      }
      if (c instanceof JComponent) {
        JComponent jc = (JComponent)c;
        Iterable<? extends Component> orphans = getClientProperty(jc, NOT_IN_HIERARCHY_COMPONENTS);
        if (orphans != null) {
          result = result.append(orphans);
        }
        JPopupMenu jpm = jc.getComponentPopupMenu();
        if (jpm != null && jpm.isVisible() && jpm.getInvoker() == jc) {
          result = result.append(Collections.singletonList(jpm));
        }
      }
      return result;
    }
  };

  private static final Function.Mono<Component> COMPONENT_PARENT = new Function.Mono<Component>() {
    @Override
    public Component fun(Component c) {
      return c.getParent();
    }
  };


  public static void scrollListToVisibleIfNeeded(@NotNull final JList list) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        final int selectedIndex = list.getSelectedIndex();
        if (selectedIndex >= 0) {
          final Rectangle visibleRect = list.getVisibleRect();
          final Rectangle cellBounds = list.getCellBounds(selectedIndex, selectedIndex);
          if (!visibleRect.contains(cellBounds)) {
            list.scrollRectToVisible(cellBounds);
          }
        }
      }
    });
  }

  @Nullable
  public static <T extends JComponent> T findComponentOfType(JComponent parent, Class<T> cls) {
    if (parent == null || cls.isAssignableFrom(parent.getClass())) {
      @SuppressWarnings("unchecked") final T t = (T)parent;
      return t;
    }
    for (Component component : parent.getComponents()) {
      if (component instanceof JComponent) {
        T comp = findComponentOfType((JComponent)component, cls);
        if (comp != null) return comp;
      }
    }
    return null;
  }

  public static <T extends JComponent> List<T> findComponentsOfType(JComponent parent, Class<T> cls) {
    final ArrayList<T> result = new ArrayList<T>();
    findComponentsOfType(parent, cls, result);
    return result;
  }

  private static <T extends JComponent> void findComponentsOfType(JComponent parent, Class<T> cls, ArrayList<T> result) {
    if (parent == null) return;
    if (cls.isAssignableFrom(parent.getClass())) {
      @SuppressWarnings("unchecked") final T t = (T)parent;
      result.add(t);
    }
    for (Component c : parent.getComponents()) {
      if (c instanceof JComponent) {
        findComponentsOfType((JComponent)c, cls, result);
      }
    }
  }

  public static class TextPainter {
    private final List<Pair<String, LineInfo>> myLines = new ArrayList<Pair<String, LineInfo>>();
    private boolean myDrawShadow;
    private Color myShadowColor;
    private float myLineSpacing;

    public TextPainter() {
      myDrawShadow = /*isUnderAquaLookAndFeel() ||*/ isUnderDarcula();
      myShadowColor = isUnderDarcula() ? Gray._0.withAlpha(100) : Gray._220;
      myLineSpacing = 1.0f;
    }

    public TextPainter withShadow(final boolean drawShadow) {
      myDrawShadow = drawShadow;
      return this;
    }

    public TextPainter withShadow(final boolean drawShadow, final Color shadowColor) {
      myDrawShadow = drawShadow;
      myShadowColor = shadowColor;
      return this;
    }

    public TextPainter withLineSpacing(final float lineSpacing) {
      myLineSpacing = lineSpacing;
      return this;
    }

    public TextPainter appendLine(final String text) {
      if (text == null || text.isEmpty()) return this;
      myLines.add(Pair.create(text, new LineInfo()));
      return this;
    }

    public TextPainter underlined(@Nullable final Color color) {
      if (!myLines.isEmpty()) {
        final LineInfo info = myLines.get(myLines.size() - 1).getSecond();
        info.underlined = true;
        info.underlineColor = color;
      }

      return this;
    }

    public TextPainter withBullet(final char c) {
      if (!myLines.isEmpty()) {
        final LineInfo info = myLines.get(myLines.size() - 1).getSecond();
        info.withBullet = true;
        info.bulletChar = c;
      }

      return this;
    }

    public TextPainter withBullet() {
      return withBullet('\u2022');
    }

    public TextPainter underlined() {
      return underlined(null);
    }

    public TextPainter smaller() {
      if (!myLines.isEmpty()) {
        myLines.get(myLines.size() - 1).getSecond().smaller = true;
      }

      return this;
    }

    public TextPainter center() {
      if (!myLines.isEmpty()) {
        myLines.get(myLines.size() - 1).getSecond().center = true;
      }

      return this;
    }

    /**
     * _position(block width, block height) => (x, y) of the block
     */
    public void draw(@NotNull final Graphics g, final PairFunction<Integer, Integer, Couple<Integer>> _position) {
      GraphicsUtil.setupAntialiasing(g, true, true);
      final int[] maxWidth = {0};
      final int[] height = {0};
      final int[] maxBulletWidth = {0};
      ContainerUtil.process(myLines, new Processor<Pair<String, LineInfo>>() {
        @Override
        public boolean process(final Pair<String, LineInfo> pair) {
          final LineInfo info = pair.getSecond();
          Font old = null;
          if (info.smaller) {
            old = g.getFont();
            g.setFont(old.deriveFont(old.getSize() * 0.70f));
          }

          final FontMetrics fm = g.getFontMetrics();

          final int bulletWidth = info.withBullet ? fm.stringWidth(" " + info.bulletChar) : 0;
          maxBulletWidth[0] = Math.max(maxBulletWidth[0], bulletWidth);

          maxWidth[0] = Math.max(fm.stringWidth(pair.getFirst().replace("<shortcut>", "").replace("</shortcut>", "") + bulletWidth), maxWidth[0]);
          height[0] += (fm.getHeight() + fm.getLeading()) * myLineSpacing;

          if (old != null) {
            g.setFont(old);
          }

          return true;
        }
      });

      final Couple<Integer> position = _position.fun(maxWidth[0] + 20, height[0]);
      assert position != null;

      final int[] yOffset = {position.getSecond()};
      ContainerUtil.process(myLines, new Processor<Pair<String, LineInfo>>() {
        @Override
        public boolean process(final Pair<String, LineInfo> pair) {
          final LineInfo info = pair.getSecond();
          String text = pair.first;
          String shortcut = "";
          if (pair.first.contains("<shortcut>")) {
            shortcut = text.substring(text.indexOf("<shortcut>") + "<shortcut>".length(), text.indexOf("</shortcut>"));
            text = text.substring(0, text.indexOf("<shortcut>"));
          }

          Font old = null;
          if (info.smaller) {
            old = g.getFont();
            g.setFont(old.deriveFont(old.getSize() * 0.70f));
          }

          final int x = position.getFirst() + maxBulletWidth[0] + 10;

          final FontMetrics fm = g.getFontMetrics();
          int xOffset = x;
          if (info.center) {
            xOffset = x + (maxWidth[0] - fm.stringWidth(text)) / 2;
          }

          if (myDrawShadow) {
            int xOff = isUnderDarcula() ? 1 : 0;
            int yOff = 1;
            Color oldColor = g.getColor();
            g.setColor(myShadowColor);

            if (info.withBullet) {
              g.drawString(info.bulletChar + " ", x - fm.stringWidth(" " + info.bulletChar) + xOff, yOffset[0] + yOff);
            }

            g.drawString(text, xOffset + xOff, yOffset[0] + yOff);
            g.setColor(oldColor);
          }

          if (info.withBullet) {
            g.drawString(info.bulletChar + " ", x - fm.stringWidth(" " + info.bulletChar), yOffset[0]);
          }

          g.drawString(text, xOffset, yOffset[0]);
          if (!StringUtil.isEmpty(shortcut)) {
            Color oldColor = g.getColor();
            g.setColor(new JBColor(new Color(82, 99, 155),
                                   new Color(88, 157, 246)));
            g.drawString(shortcut, xOffset + fm.stringWidth(text + (isUnderDarcula() ? " " : "")), yOffset[0]);
            g.setColor(oldColor);
          }

          if (info.underlined) {
            Color c = null;
            if (info.underlineColor != null) {
              c = g.getColor();
              g.setColor(info.underlineColor);
            }

            g.drawLine(x - maxBulletWidth[0] - 10, yOffset[0] + fm.getDescent(), x + maxWidth[0] + 10, yOffset[0] + fm.getDescent());
            if (c != null) {
              g.setColor(c);

            }

            if (myDrawShadow) {
              c = g.getColor();
              g.setColor(myShadowColor);
              g.drawLine(x - maxBulletWidth[0] - 10, yOffset[0] + fm.getDescent() + 1, x + maxWidth[0] + 10,
                         yOffset[0] + fm.getDescent() + 1);
              g.setColor(c);
            }
          }

          yOffset[0] += (fm.getHeight() + fm.getLeading()) * myLineSpacing;

          if (old != null) {
            g.setFont(old);
          }

          return true;
        }
      });
    }

    private static class LineInfo {
      private boolean underlined;
      private boolean withBullet;
      private char bulletChar;
      private Color underlineColor;
      private boolean smaller;
      private boolean center;
    }
  }

  @Nullable
  public static JRootPane getRootPane(Component c) {
    JRootPane root = getParentOfType(JRootPane.class, c);
    if (root != null) return root;
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof JComponent) {
        @SuppressWarnings("unchecked") WeakReference<JRootPane> pane =
          (WeakReference<JRootPane>)((JComponent)eachParent).getClientProperty(ROOT_PANE);
        if (pane != null) return pane.get();
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }

  public static void setFutureRootPane(JComponent c, JRootPane pane) {
    c.putClientProperty(ROOT_PANE, new WeakReference<JRootPane>(pane));
  }

  public static boolean isMeaninglessFocusOwner(@Nullable Component c) {
    if (c == null || !c.isShowing()) return true;

    return c instanceof JFrame || c instanceof JDialog || c instanceof JWindow || c instanceof JRootPane || isFocusProxy(c);
  }

  @NotNull
  public static Timer createNamedTimer(@NonNls @NotNull final String name, int delay, @NotNull ActionListener listener) {
    return new Timer(delay, listener) {
      @Override
      public String toString() {
        return name;
      }
    };
  }
  @NotNull
  public static Timer createNamedTimer(@NonNls @NotNull final String name, int delay) {
    return new Timer(delay, null) {
      @Override
      public String toString() {
        return name;
      }
    };
  }

  public static boolean isDialogRootPane(JRootPane rootPane) {
    if (rootPane != null) {
      final Object isDialog = rootPane.getClientProperty("DIALOG_ROOT_PANE");
      return isDialog instanceof Boolean && ((Boolean)isDialog).booleanValue();
    }
    return false;
  }

  @Nullable
  public static JComponent mergeComponentsWithAnchor(PanelWithAnchor... panels) {
    return mergeComponentsWithAnchor(Arrays.asList(panels));
  }

  @Nullable
  public static JComponent mergeComponentsWithAnchor(Collection<? extends PanelWithAnchor> panels) {
    JComponent maxWidthAnchor = null;
    int maxWidth = 0;
    for (PanelWithAnchor panel : panels) {
      JComponent anchor = panel != null ? panel.getAnchor() : null;
      if (anchor != null) {
        int anchorWidth = anchor.getPreferredSize().width;
        if (maxWidth < anchorWidth) {
          maxWidth = anchorWidth;
          maxWidthAnchor = anchor;
        }
      }
    }
    for (PanelWithAnchor panel : panels) {
      if (panel != null) {
        panel.setAnchor(maxWidthAnchor);
      }
    }
    return maxWidthAnchor;
  }

  public static void setNotOpaqueRecursively(@NotNull Component component) {
    if (!isUnderAquaLookAndFeel()) return;

    if (component.getBackground().equals(getPanelBackground())
        || component instanceof JScrollPane
        || component instanceof JViewport
        || component instanceof JLayeredPane) {
      if (component instanceof JComponent) {
        ((JComponent)component).setOpaque(false);
      }
      if (component instanceof Container) {
        for (Component c : ((Container)component).getComponents()) {
          setNotOpaqueRecursively(c);
        }
      }
    }
  }

  public static void setBackgroundRecursively(@NotNull Component component, @NotNull Color bg) {
    component.setBackground(bg);
    if (component instanceof Container) {
      for (Component c : ((Container)component).getComponents()) {
        setBackgroundRecursively(c, bg);
      }
    }
  }

  /**
   * Adds an empty border with the specified insets to the specified component.
   * If the component already has a border it will be preserved.
   *
   * @param component the component to which border added
   * @param top       the inset from the top
   * @param left      the inset from the left
   * @param bottom    the inset from the bottom
   * @param right     the inset from the right
   */
  public static void addInsets(@NotNull JComponent component, int top, int left, int bottom, int right) {
    addBorder(component, BorderFactory.createEmptyBorder(top, left, bottom, right));
  }

  /**
   * Adds an empty border with the specified insets to the specified component.
   * If the component already has a border it will be preserved.
   *
   * @param component the component to which border added
   * @param insets    the top, left, bottom, and right insets
   */
  public static void addInsets(@NotNull JComponent component, @NotNull Insets insets) {
    addInsets(component, insets.top, insets.left, insets.bottom, insets.right);
  }

  public static void adjustWindowToMinimumSize(final Window window) {
    if (window == null) return;
    final Dimension minSize = window.getMinimumSize();
    final Dimension size = window.getSize();
    final Dimension newSize = new Dimension(Math.max(size.width, minSize.width), Math.max(size.height, minSize.height));

    if (!newSize.equals(size)) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (window.isShowing()) {
            window.setSize(newSize);
          }
        }
      });
    }
  }

  @Nullable
  public static Color getColorAt(final Icon icon, final int x, final int y) {
    if (0 <= x && x < icon.getIconWidth() && 0 <= y && y < icon.getIconHeight()) {
      final BufferedImage image = createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
      icon.paintIcon(null, image.getGraphics(), 0, 0);

      final int[] pixels = new int[1];
      final PixelGrabber pixelGrabber = new PixelGrabber(image, x, y, 1, 1, pixels, 0, 1);
      try {
        pixelGrabber.grabPixels();
        return new Color(pixels[0]);
      }
      catch (InterruptedException ignored) {
      }
    }

    return null;
  }

  public static int getLcdContrastValue() {
    int lcdContrastValue  = Registry.get("lcd.contrast.value").asInteger();

    // Evaluate the value depending on our current theme
    if (lcdContrastValue == 0) {
      if (SystemInfo.isMacIntel64) {
        lcdContrastValue = isUnderDarcula() ? 140 : 230;
      } else {
        Map map = (Map)Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");

        if (map == null) {
          lcdContrastValue = 140;
        } else {
          Object o = map.get(RenderingHints.KEY_TEXT_LCD_CONTRAST);
          lcdContrastValue = o == null ? 140 : (Integer)o;
        }
      }
    }

    if (lcdContrastValue < 100 || lcdContrastValue > 250) {
      // the default value
      lcdContrastValue = 140;
    }

    return lcdContrastValue;
  }

  /**
   * Adds the specified border to the specified component.
   * If the component already has a border it will be preserved.
   * If component or border is not specified nothing happens.
   *
   * @param component the component to which border added
   * @param border    the border to add to the component
   */
  public static void addBorder(JComponent component, Border border) {
    if (component != null && border != null) {
      Border old = component.getBorder();
      if (old != null) {
        border = BorderFactory.createCompoundBorder(border, old);
      }
      component.setBorder(border);
    }
  }

  private static final Color DECORATED_ROW_BG_COLOR = new JBColor(new Color(242, 245, 249), new Color(65, 69, 71));

  public static Color getDecoratedRowColor() {
    return DECORATED_ROW_BG_COLOR;
  }

  @NotNull
  public static Paint getGradientPaint(float x1, float y1, @NotNull Color c1, float x2, float y2, @NotNull Color c2) {
    return Registry.is("ui.no.bangs.and.whistles") ? ColorUtil.mix(c1, c2, .5) : new GradientPaint(x1, y1, c1, x2, y2, c2);
  }

  @Nullable
  public static Point getLocationOnScreen(@NotNull JComponent component) {
    int dx = 0;
    int dy = 0;
    for (Container c = component; c != null; c = c.getParent()) {
      if (c.isShowing()) {
        Point locationOnScreen = c.getLocationOnScreen();
        locationOnScreen.translate(dx, dy);
        return locationOnScreen;
      }
      else {
        Point location = c.getLocation();
        dx += location.x;
        dy += location.y;
      }
    }
    return null;
  }

  @NotNull
  public static Window getActiveWindow() {
    Window[] windows = Window.getWindows();
    for (Window each : windows) {
      if (each.isVisible() && each.isActive()) return each;
    }
    return JOptionPane.getRootFrame();
  }

  public static void suppressFocusStealing (Window window) {
    // Focus stealing is not a problem on Mac
    if (SystemInfo.isMac) return;
    if (Registry.is("suppress.focus.stealing")) {
      setAutoRequestFocus(window, false);
    }
  }

  public static void setAutoRequestFocus (final Window onWindow, final boolean set){
    if (SystemInfo.isMac) return;
    if (SystemInfo.isJavaVersionAtLeast("1.7")) {
      try {
        Method setAutoRequestFocusMethod  = onWindow.getClass().getMethod("setAutoRequestFocus", boolean.class);
        setAutoRequestFocusMethod.invoke(onWindow, set);
      }
      catch (NoSuchMethodException e) { LOG.debug(e); }
      catch (InvocationTargetException e) { LOG.debug(e); }
      catch (IllegalAccessException e) { LOG.debug(e); }
    }
  }

  //May have no usages but it's useful in runtime (Debugger "watches", some logging etc.)
  public static String getDebugText(Component c) {
    StringBuilder builder  = new StringBuilder();
    getAllTextsRecursivelyImpl(c, builder);
    return builder.toString();
  }

  private static void getAllTextsRecursivelyImpl(Component component, StringBuilder builder) {
    String candidate = "";
    if (component instanceof JLabel) candidate = ((JLabel)component).getText();
    if (component instanceof JTextComponent) candidate = ((JTextComponent)component).getText();
    if (component instanceof AbstractButton) candidate = ((AbstractButton)component).getText();
    if (StringUtil.isNotEmpty(candidate)) {
      candidate = candidate.replaceAll("<a href=\"#inspection/[^)]+\\)", "");
      if (builder.length() > 0) builder.append(' ');
      builder.append(StringUtil.removeHtmlTags(candidate).trim());
    }
    if (component instanceof Container) {
      Component[] components = ((Container)component).getComponents();
      for (Component child : components) {
        getAllTextsRecursivelyImpl(child, builder);
      }
    }
  }

  public static boolean isAncestor(@NotNull Component ancestor, @Nullable Component descendant) {
    while (descendant != null) {
      if (descendant == ancestor) {
        return true;
      }
      descendant = descendant.getParent();
    }
    return false;
  }

  public static void resetUndoRedoActions(@NotNull JTextComponent textComponent) {
    UndoManager undoManager = getClientProperty(textComponent, UNDO_MANAGER);
    if (undoManager != null) {
      undoManager.discardAllEdits();
    }
  }

  private static final DocumentAdapter SET_TEXT_CHECKER = new DocumentAdapter() {
    @Override
    protected void textChanged(DocumentEvent e) {
      Document document = e.getDocument();
      if (document instanceof AbstractDocument) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (StackTraceElement element : stackTrace) {
          if (!element.getClassName().equals(JTextComponent.class.getName()) || !element.getMethodName().equals("setText")) continue;
          UndoableEditListener[] undoableEditListeners = ((AbstractDocument)document).getUndoableEditListeners();
          for (final UndoableEditListener listener : undoableEditListeners) {
            if (listener instanceof UndoManager) {
              Runnable runnable = new Runnable() {
                @Override
                public void run() {
                  ((UndoManager)listener).discardAllEdits();
                }
              };
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(runnable);
              return;
            }
          }
        }
      }
    }
  };

  public static void addUndoRedoActions(@NotNull final JTextComponent textComponent) {
    if (textComponent.getClientProperty(UNDO_MANAGER) instanceof UndoManager) {
      return;
    }
    UndoManager undoManager = new UndoManager();
    textComponent.putClientProperty(UNDO_MANAGER, undoManager);
    textComponent.getDocument().addUndoableEditListener(undoManager);
    textComponent.getDocument().addDocumentListener(SET_TEXT_CHECKER);
    textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, SystemInfo.isMac? InputEvent.META_MASK : InputEvent.CTRL_MASK), "undoKeystroke");
    textComponent.getActionMap().put("undoKeystroke", UNDO_ACTION);
    textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, (SystemInfo.isMac? InputEvent.META_MASK : InputEvent.CTRL_MASK) | InputEvent.SHIFT_MASK), "redoKeystroke");
    textComponent.getActionMap().put("redoKeystroke", REDO_ACTION);
  }

  @Nullable
  public static UndoManager getUndoManager(Component component) {
    if (component instanceof JTextComponent) {
      Object o = ((JTextComponent)component).getClientProperty(UNDO_MANAGER);
      if (o instanceof UndoManager) return (UndoManager)o;
    }
    return null;
  }

  public static void playSoundFromResource(final String resourceName) {
    final Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return;
    playSoundFromStream(new Factory<InputStream>() {
      @Override
      public InputStream create() {
        return callerClass.getResourceAsStream(resourceName);
      }
    });
  }

  public static void playSoundFromStream(final Factory<InputStream> streamProducer) {
    new Thread(new Runnable() {
      // The wrapper thread is unnecessary, unless it blocks on the
      // Clip finishing; see comments.
      @Override
      public void run() {
        try {
          Clip clip = AudioSystem.getClip();
          InputStream stream = streamProducer.create();
          if (!stream.markSupported()) stream = new BufferedInputStream(stream);
          AudioInputStream inputStream = AudioSystem.getAudioInputStream(stream);
          clip.open(inputStream);

          clip.start();
        } catch (Exception ignore) {
          LOG.info(ignore);
        }
      }
    },"play sound").start();
  }

  // Experimental!!!
  // It seems to work under Windows
  @Nullable
  public static String getCurrentKeyboardLayout() {
    InputContext instance = InputContext.getInstance();
    Class<? extends InputContext> instanceClass = instance.getClass();
    Class<?> superclass = instanceClass.getSuperclass();
    if (superclass.getName().equals("sun.awt.im.InputContext")) {
      try {
        Object inputMethodLocator = ReflectionUtil.getField(superclass, instance, null, "inputMethodLocator");
        Locale locale = ReflectionUtil.getField(inputMethodLocator.getClass(), inputMethodLocator, Locale.class, "locale");
        return locale.getLanguage().toUpperCase(Locale.getDefault());
      }
      catch (Exception ignored) {
      }
    }
    return null;
  }

  private static Map<String, String> ourRealFontFamilies;

  //Experimental, seems to be reliable under MacOS X only

  public static String getRealFontFamily(String genericFontFamily) {
    if (ourRealFontFamilies != null && ourRealFontFamilies.get(genericFontFamily) != null) {
      return ourRealFontFamilies.get(genericFontFamily);
    }
    String pattern = "Real Font Family";
    List<String> GENERIC = Arrays.asList(Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SANS_SERIF, Font.SERIF);
    int patternSize = 50;
    BufferedImage image = createImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics graphics = image.getGraphics();
    graphics.setFont(new Font(genericFontFamily, Font.PLAIN, patternSize));
    Object patternBounds = graphics.getFontMetrics().getStringBounds(pattern, graphics);
    for (String family: GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
      if (GENERIC.contains(family)) continue;
      graphics.setFont(new Font(family, Font.PLAIN, patternSize));
      if (graphics.getFontMetrics().getStringBounds(pattern, graphics).equals(patternBounds)) {
        if (ourRealFontFamilies == null) {
          ourRealFontFamilies = new HashMap<String, String>();
        }
        ourRealFontFamilies.put(genericFontFamily, family);
        return family;
      }
    }
    return genericFontFamily;
  }

  @NotNull
  public static String rightArrow() {
    return FontUtil.rightArrow(getLabelFont());
  }

  @NotNull
  public static String upArrow(@NotNull String defaultValue) {
    return FontUtil.upArrow(getLabelFont(), defaultValue);
  }

  public static EmptyBorder getTextAlignBorder(@NotNull JToggleButton alignSource) {
    ButtonUI ui = alignSource.getUI();
    int leftGap = alignSource.getIconTextGap();
    Border border = alignSource.getBorder();
    if (border != null) {
      leftGap += border.getBorderInsets(alignSource).left;
    }
    if (ui instanceof BasicRadioButtonUI) {
      leftGap += ((BasicRadioButtonUI)alignSource.getUI()).getDefaultIcon().getIconWidth();
    }
    else {
      Method method = ReflectionUtil.getMethod(ui.getClass(), "getDefaultIcon", JComponent.class);
      if (method != null) {
        try {
          Object o = method.invoke(ui, alignSource);
          if (o instanceof Icon) {
            leftGap += ((Icon)o).getIconWidth();
          }
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
        catch (InvocationTargetException e) {
          e.printStackTrace();
        }
      }
    }
    return new EmptyBorder(0, leftGap, 0, 0);
  }

  /**
   * It is your responsibility to set correct horizontal align (left in case of UI Designer)
   */
  public static void configureNumericFormattedTextField(@NotNull JFormattedTextField textField) {
    NumberFormat format = NumberFormat.getIntegerInstance();
    format.setParseIntegerOnly(true);
    format.setGroupingUsed(false);
    NumberFormatter numberFormatter = new NumberFormatter(format);
    numberFormatter.setMinimum(0);
    textField.setFormatterFactory(new DefaultFormatterFactory(numberFormatter));
    textField.setHorizontalAlignment(SwingConstants.TRAILING);

    textField.setColumns(4);
  }

  /**
   * Returns the first window ancestor of the component.
   * Note that this method returns the component itself if it is a window.
   *
   * @param component the component used to find corresponding window
   * @return the first window ancestor of the component; or {@code null}
   *         if the component is not a window and is not contained inside a window
   */
  @Nullable
  public static Window getWindow(@Nullable Component component) {
    return component == null ? null :
           component instanceof Window ? (Window)component : SwingUtilities.getWindowAncestor(component);
  }

  /**
   * Places the specified window at the top of the stacking order and shows it in front of any other windows.
   * If the window is iconified it will be shown anyway.
   *
   * @param window the window to activate
   */
  public static void toFront(@Nullable Window window) {
    if (window instanceof Frame) {
      ((Frame)window).setState(Frame.NORMAL);
    }
    if (window != null) {
      window.toFront();
    }
  }

  public static Image getDebugImage(Component component) {
    BufferedImage image = createImage(component, component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.RED);
    graphics.fillRect(0, 0, component.getWidth() + 1, component.getHeight() + 1);
    component.paint(graphics);
    return image;
  }

  /**
   * Indicates whether the specified component is scrollable or it contains a scrollable content.
   */
  public static boolean hasScrollPane(@NotNull Component component) {
    return hasComponentOfType(component, JScrollPane.class);
  }

  /**
   * Indicates whether the specified component is instance of one of the specified types
   * or it contains an instance of one of the specified types.
   */
  public static boolean hasComponentOfType(Component component, Class<?>... types) {
    for (Class<?> type : types) {
      if (type.isAssignableFrom(component.getClass())) {
        return true;
      }
    }
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        if (hasComponentOfType(container.getComponent(i), types)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void setColumns(JTextComponent textComponent, int columns) {
    if (textComponent instanceof JTextField) {
      ((JTextField)textComponent).setColumns(columns);
    }
    if (textComponent instanceof JTextArea) {
      ((JTextArea)textComponent).setColumns(columns);
    }
  }

  public static int getLineHeight(@NotNull JTextComponent textComponent) {
    return textComponent.getFontMetrics(textComponent.getFont()).getHeight();
  }

  /**
   * Returns the first focusable component in the specified container.
   * This method returns {@code null} if container is {@code null},
   * or if focus traversal policy cannot be determined,
   * or if found focusable component is not a {@link JComponent}.
   *
   * @param container a container whose first focusable component is to be returned
   * @return the first focusable component or {@code null} if it cannot be found
   */
  public static JComponent getPreferredFocusedComponent(Container container) {
    Container parent = container;
    if (parent == null) return null;
    FocusTraversalPolicy policy = parent.getFocusTraversalPolicy();
    while (policy == null) {
      parent = parent.getParent();
      if (parent == null) return null;
      policy = parent.getFocusTraversalPolicy();
    }
    Component component = policy.getFirstComponent(container);
    return component instanceof JComponent ? (JComponent)component : null;
  }

  /**
   * Calculates a component style from the corresponding client property.
   * The key "JComponent.sizeVariant" is used by Apple's L&F to scale components.
   *
   * @param component a component to process
   * @return a component style of the specified component
   */
  public static ComponentStyle getComponentStyle(Component component) {
    if (component instanceof JComponent) {
      Object property = ((JComponent)component).getClientProperty("JComponent.sizeVariant");
      if ("large".equals(property)) return ComponentStyle.LARGE;
      if ("small".equals(property)) return ComponentStyle.SMALL;
      if ("mini".equals(property)) return ComponentStyle.MINI;
    }
    return ComponentStyle.REGULAR;
  }

  /**
   * KeyEvents for specified keystrokes would be redispatched to target component
   */
  public static void redirectKeystrokes(@NotNull Disposable disposable,
                                        @NotNull final JComponent source,
                                        @NotNull final JComponent target,
                                        @NotNull final KeyStroke... keyStrokes) {
    final KeyAdapter keyAdapter = new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        KeyStroke keyStrokeForEvent = KeyStroke.getKeyStrokeForEvent(e);
        for (KeyStroke stroke : keyStrokes) {
          if (!stroke.isOnKeyRelease() && stroke.equals(keyStrokeForEvent)) target.dispatchEvent(e);
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        KeyStroke keyStrokeForEvent = KeyStroke.getKeyStrokeForEvent(e);
        for (KeyStroke stroke : keyStrokes) {
          if (stroke.isOnKeyRelease() && stroke.equals(keyStrokeForEvent)) target.dispatchEvent(e);
        }
      }
    };
    source.addKeyListener(keyAdapter);
    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        source.removeKeyListener(keyAdapter);
      }
    });
  }

  public static final String CHECKBOX_ROLLOVER_PROPERTY = "JCheckBox.rollOver.rectangle";
  public static final String CHECKBOX_PRESSED_PROPERTY = "JCheckBox.pressed.rectangle";

  public static void repaintViewport(@NotNull JComponent c) {
    if (!c.isDisplayable() || !c.isVisible()) return;

    Container p = c.getParent();
    if (p instanceof JViewport) {
      p.repaint();
    }
  }

  public static void setCursor(Component component, Cursor cursor) {
    // cursor is updated by native code even if component has the same cursor, causing performance problems (IDEA-167733)
    if(component.isCursorSet() && component.getCursor() == cursor) return;
    component.setCursor(cursor);
  }
}
