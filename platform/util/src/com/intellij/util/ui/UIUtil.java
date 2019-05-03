// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.BundleBase;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivitySubNames;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.JBUIScale.ScaleContext;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.*;
import sun.awt.HeadlessToolkit;
import sun.java2d.SunGraphicsEnvironment;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.UndoableEditListener;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.*;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.html.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.im.InputContext;
import java.awt.image.*;
import java.awt.print.PrinterGraphics;
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
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * @author max
 */
@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public class UIUtil {
  public static final String BORDER_LINE = "<hr size=1 noshade>";

  private static StyleSheet DEFAULT_HTML_KIT_CSS;

  public static final Key<Boolean> LAF_WITH_THEME_KEY = Key.create("Laf.with.ui.theme");

  // should be here and not in JBUI to avoid dependency on JBUI class in initSystemFontData method
  public static final boolean SCALE_VERBOSE = Boolean.getBoolean("ide.ui.scale.verbose");

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

  @NotNull
  // cannot be static because logging maybe not configured yet
  private static Logger getLogger() {
    return Logger.getInstance("#com.intellij.util.ui.UIUtil");
  }

  @Deprecated
  public static void decorateFrame(@NotNull JRootPane pane) {
    decorateWindowHeader(pane);
  }

  public static void decorateWindowHeader(JRootPane pane) {
    if (pane != null && SystemInfo.isMac) {
      pane.putClientProperty("jetbrains.awt.windowDarkAppearance", Registry.is("ide.mac.allowDarkWindowDecorations") && isUnderDarcula());
    }
  }

  // Here we setup window to be checked in IdeEventQueue and reset typeahead state when the window finally appears and gets focus
  public static void markAsTypeAheadAware(Window window) {
    putWindowClientProperty(window, "TypeAheadAwareWindow", Boolean.TRUE);
  }

  public static boolean isTypeAheadAware(Window window) {
    return isWindowClientPropertyTrue(window, "TypeAheadAwareWindow");
  }

  // Here we setup dialog to be suggested in OwnerOptional as owner even if the dialog is not modal
  public static void markAsPossibleOwner(Dialog dialog) {
    putWindowClientProperty(dialog, "PossibleOwner", Boolean.TRUE);
  }

  public static boolean isPossibleOwner(@NotNull Dialog dialog) {
    return isWindowClientPropertyTrue(dialog, "PossibleOwner");
  }

  /*
   * The method should be called before java.awt.Toolkit.initAssistiveTechnologies()
   * which is called from Toolkit.getDefaultToolkit().
   */
  private static void blockATKWrapper() {
    // registry must be not used here, because this method called before application loading
    if (!SystemInfo.isLinux || !SystemProperties.getBooleanProperty("linux.jdk.accessibility.atkwrapper.block", true)) {
      return;
    }

    if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
      // Replace AtkWrapper with a dummy Object. It'll be instantiated & GC'ed right away, a NOP.
      System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object");
      getLogger().info(ScreenReader.ATK_WRAPPER + " is blocked, see IDEA-149219");
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

  @NotNull
  public static Cursor getTextCursor(@NotNull Color backgroundColor) {
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

  @NotNull
  public static RGBImageFilter getGrayFilter() {
    return GrayFilter.namedFilter("grayFilter", new GrayFilter(33, -35, 100));
  }

  @NotNull
  public static RGBImageFilter getTextGrayFilter() {
    return GrayFilter.namedFilter("text.grayFilter", new GrayFilter(20, 0, 100));
  }

  @ApiStatus.Experimental
  public static class GrayFilter extends RGBImageFilter {
    private float brightness;
    private float contrast;
    private int alpha;

    private int origContrast;
    private int origBrightness;

    /**
     * @param brightness in range [-100..100] where 0 has no effect
     * @param contrast in range [-100..100] where 0 has no effect
     * @param alpha in range [0..100] where 0 is transparent, 100 has no effect
     */
    public GrayFilter(int brightness, int contrast, int alpha) {
      setBrightness(brightness);
      setContrast(contrast);
      setAlpha(alpha);
    }

    public GrayFilter() {
      this(0, 0, 100);
    }

    private void setBrightness(int brightness) {
      origBrightness = Math.max(-100, Math.min(100, brightness));
      this.brightness = (float)(Math.pow(origBrightness, 3) / (100f * 100f)); // cubic in [0..100]
    }

    public int getBrightness() {
      return origBrightness;
    }

    private void setContrast(int contrast) {
      origContrast = Math.max(-100, Math.min(100, contrast));
      this.contrast = origContrast / 100f;
    }

    public int getContrast() {
      return origContrast;
    }

    private void setAlpha(int alpha) {
      this.alpha = Math.max(0, Math.min(100, alpha));
    }

    public int getAlpha() {
      return alpha;
    }

    @Override
    @SuppressWarnings("AssignmentReplaceableWithOperatorAssignment")
    public int filterRGB(int x, int y, int rgb) {
      // Use NTSC conversion formula.
      int gray = (int)(0.30 * (rgb >> 16 & 0xff) +
                       0.59 * (rgb >> 8 & 0xff) +
                       0.11 * (rgb & 0xff));

      if (brightness >= 0) {
        gray = (int)((gray + brightness * 255) / (1 + brightness));
      }
      else {
        gray = (int)(gray / (1 - brightness));
      }

      if (contrast >= 0) {
        if (gray >= 127) {
          gray = (int)(gray + (255 - gray) * contrast);
        }
        else {
          gray = (int)(gray - gray * contrast);
        }
      }
      else {
        gray = (int)(127 + (gray - 127) * (contrast + 1));
      }

      int a = ((rgb >> 24) & 0xff) * alpha / 100;

      return (a << 24) | (gray << 16) | (gray << 8) | gray;
    }

    @NotNull
    public GrayFilterUIResource asUIResource() {
      return new GrayFilterUIResource(this);
    }

    public static class GrayFilterUIResource extends GrayFilter implements UIResource {
      public GrayFilterUIResource(@NotNull GrayFilter filter) {
        super(filter.origBrightness, filter.origContrast, filter.alpha);
      }
    }

    @NotNull
    public static GrayFilter namedFilter(@NotNull String resourceName, @NotNull GrayFilter defaultFilter) {
      return ObjectUtils.notNull((GrayFilter)UIManager.get(resourceName), defaultFilter);
    }
  }

  /** @deprecated Apple JRE is no longer supported (to be removed in IDEA 2019) */
  @Deprecated
  public static boolean isAppleRetina() {
    return false;
  }

  @NotNull
  public static Couple<Color> getCellColors(@NotNull JTable table, boolean isSel, int row, int column) {
    return Couple.of(isSel ? table.getSelectionForeground() : table.getForeground(),
                     isSel ? table.getSelectionBackground() : table.getBackground());
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

  public static boolean isDialogFont(@NotNull Font font) {
    return Font.DIALOG.equals(font.getFamily(Locale.US));
  }

  public static boolean isScrolledToTheBottom(JComponent c) {
    JScrollPane scrollPane = c != null ? getParentOfType(JScrollPane.class, c) : null;
    if (scrollPane == null) return true;
    Rectangle viewRect = scrollPane.getViewport().getViewRect();
    return c.getHeight() == viewRect.y + viewRect.height;
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
    public void actionPerformed(@NotNull ActionEvent e) {
      UndoManager manager = getClientProperty(e.getSource(), UNDO_MANAGER);
      if (manager != null && manager.canRedo()) {
        manager.redo();
      }
    }
  };
  private static final AbstractAction UNDO_ACTION = new AbstractAction() {
    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      UndoManager manager = getClientProperty(e.getSource(), UNDO_MANAGER);
      if (manager != null && manager.canUndo()) {
        manager.undo();
      }
    }
  };

  private static final Color ACTIVE_HEADER_COLOR = JBColor.namedColor("HeaderColor.active", 0xa0bad5);
  private static final Color INACTIVE_HEADER_COLOR = JBColor.namedColor("HeaderColor.inactive", Gray._128);

  public static final Color CONTRAST_BORDER_COLOR = JBColor.namedColor("Borders.ContrastBorderColor", new JBColor(Gray.x9B, Gray.x4B));

  public static final Color SIDE_PANEL_BACKGROUND = JBColor.namedColor("SidePanel.background", new JBColor(0xE6EBF0, 0x3E434C));

  public static final Color AQUA_SEPARATOR_FOREGROUND_COLOR = new JBColor(Gray._223, Gray.x51);
  public static final Color AQUA_SEPARATOR_BACKGROUND_COLOR = new JBColor(Gray._240, Gray.x51);
  public static final Color TRANSPARENT_COLOR = Gray.TRANSPARENT;

  public static final int DEFAULT_HGAP = 10;
  public static final int DEFAULT_VGAP = 4;
  public static final int LARGE_VGAP = 12;

  public static final Insets PANEL_REGULAR_INSETS = new Insets(8, 12, 8, 12);
  public static final Insets PANEL_SMALL_INSETS = new Insets(5, 8, 5, 8);

  @Deprecated
  public static final Border DEBUG_MARKER_BORDER = new Border() {
    @NotNull
    @Override
    public Insets getBorderInsets(Component c) {
      return JBUI.emptyInsets();
    }

    @Override
    public void paintBorder(Component c, @NotNull Graphics g, int x, int y, int width, int height) {
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

  public static float DEF_SYSTEM_FONT_SIZE = 12f;

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
      if (SystemInfo.isMac) {
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
    static boolean isOracleMacRetinaDevice(GraphicsDevice device) {
      Boolean isRetina  = devicesToRetinaSupportCacheMap.get(device);

      if (isRetina != null) {
        return isRetina;
      }

      Method getScaleFactorMethod = null;
      try {
        getScaleFactorMethod = Class.forName("sun.awt.CGraphicsDevice").getMethod("getScaleFactor");
      } catch (ClassNotFoundException | NoSuchMethodException e) {
        // not an Oracle Mac JDK or API has been changed
        getLogger().debug("CGraphicsDevice.getScaleFactor(): not an Oracle Mac JDK or API has been changed");
      }
      catch (Exception e) {
        getLogger().debug(e);
        getLogger().debug("CGraphicsDevice.getScaleFactor(): probably it is Java 9");
      }

      try {
        isRetina =  getScaleFactorMethod == null || (Integer)getScaleFactorMethod.invoke(device) != 1;
      } catch (IllegalAccessException e) {
        getLogger().debug("CGraphicsDevice.getScaleFactor(): Access issue");
        isRetina = false;
      } catch (InvocationTargetException e) {
        getLogger().debug("CGraphicsDevice.getScaleFactor(): Invocation issue");
        isRetina = false;
      } catch (IllegalArgumentException e) {
        getLogger().debug("object is not an instance of declaring class: " + device.getClass().getName());
        isRetina = false;
      }

      devicesToRetinaSupportCacheMap.put(device, isRetina);

      return isRetina;
    }

    /**
     * This method perfectly detects retina Graphics2D for jdk7+
     * @param g graphics to be tested
     * @return false if the device of the Graphics2D is not a retina device,
     * jdk is an Apple JDK or Oracle API has been changed.
     */
    private static boolean isMacRetina(@NotNull Graphics2D g) {
      GraphicsConfiguration configuration = g.getDeviceConfiguration();
      if (configuration == null) {
        return false;
      }

      GraphicsDevice device = configuration.getDevice();
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

  public static boolean isRetina(@NotNull Graphics2D graphics) {
    return SystemInfo.isMac ? DetectRetinaKit.isMacRetina(graphics) : isRetina();
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
    }
    else {
      synchronized (ourRetina) {
        if (ourRetina.isNull()) {
          ourRetina.set(false); // in case HiDPIScaledImage.drawIntoImage is not called for some reason

          try {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            final GraphicsDevice device = env.getDefaultScreenDevice();
            Integer scale = ReflectionUtil.getField(device.getClass(), device, int.class, "scale");
            if (scale != null && scale.intValue() == 2) {
              ourRetina.set(true);
              return true;
            }
          }
          catch (AWTError | Exception ignore) { }
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

  public static boolean isWindowClientPropertyTrue(Window window, @NotNull Object key) {
    return Boolean.TRUE.equals(getWindowClientProperty(window, key));
  }

  public static Object getWindowClientProperty(Window window, @NotNull Object key) {
    if (window instanceof RootPaneContainer) {
      JRootPane pane = ((RootPaneContainer)window).getRootPane();
      if (pane != null) {
        return pane.getClientProperty(key);
      }
    }
    return null;
  }

  public static void putWindowClientProperty(Window window, @NotNull Object key, Object value) {
    if (window instanceof RootPaneContainer) {
      JRootPane pane = ((RootPaneContainer)window).getRootPane();
      if (pane != null) {
        pane.putClientProperty(key, value);
      }
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

  @NotNull
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

  @NotNull
  public static String getHtmlBody(@NotNull Html html) {
    String result = getHtmlBody(html.getText());
    return html.isKeepFont() ? result : result.replaceAll("<font(.*?)>", "").replaceAll("</font>", "");
  }

  public static void drawLinePickedOut(@NotNull Graphics graphics, int x, int y, int x1, int y1) {
    if (x == x1) {
      int minY = Math.min(y, y1);
      int maxY = Math.max(y, y1);
      drawLine(graphics, x, minY + 1, x1, maxY - 1);
    }
    else if (y == y1) {
      int minX = Math.min(x, x1);
      int maxX = Math.max(x, x1);
      drawLine(graphics, minX + 1, y, maxX - 1, y1);
    }
    else {
      drawLine(graphics, x, y, x1, y1);
    }
  }

  public static boolean isReallyTypedEvent(@NotNull KeyEvent e) {
    char c = e.getKeyChar();
    if (c < 0x20 || c == 0x7F) return false;

    // Allow input of special characters on Windows in Persian keyboard layout using Ctrl+Shift+1..4
    if (SystemInfo.isWindows && c >= 0x200C && c <= 0x200F) return true;

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

  public static void drawLabelDottedRectangle(@NotNull final JLabel label, @NotNull final Graphics g) {
    drawLabelDottedRectangle(label, g, null);
  }

  public static void drawLabelDottedRectangle(@NotNull final JLabel label, @NotNull final Graphics g, @Nullable Rectangle bounds) {
    if (bounds == null) {
      bounds = getLabelTextBounds(label);
    }
    // JLabel draws the text relative to the baseline. So, we must ensure
    // we draw the dotted rectangle relative to that same baseline.
    FontMetrics fm = label.getFontMetrics(label.getFont());
    int baseLine = label.getUI().getBaseline(label, label.getWidth(), label.getHeight());
    int textY = baseLine - fm.getLeading() - fm.getAscent();
    int textHeight = fm.getHeight();
    drawDottedRectangle(g, bounds.x, textY, bounds.x + bounds.width - 1, textY + textHeight - 1);
  }

  @NotNull
  public static Rectangle getLabelTextBounds(@NotNull final JLabel label) {
    final Dimension size = label.getPreferredSize();
    Icon icon = label.getIcon();
    final Point point = new Point(0, 0);
    final Insets insets = label.getInsets();
    if (icon != null) {
      if (label.getHorizontalTextPosition() == SwingConstants.TRAILING) {
        point.x += label.getIconTextGap();
        point.x += icon.getIconWidth();
      } else if (label.getHorizontalTextPosition() == SwingConstants.LEADING) {
        size.width -= icon.getIconWidth();
      }
    }
    point.x += insets.left;
    point.y += insets.top;
    size.width -= point.x;
    size.width -= insets.right;
    size.height -= insets.bottom;

    return new Rectangle(point, size);
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

  public static void setEnabled(@NotNull Component component, boolean enabled, boolean recursively) {
    setEnabled(component, enabled, recursively, false);
  }

  public static void setEnabled(@NotNull Component component, boolean enabled, boolean recursively, final boolean visibleOnly) {
    JBIterable<Component> all = recursively ? uiTraverser(component).expandAndFilter(
      visibleOnly ? (Condition<Component>)Component::isVisible : Conditions.alwaysTrue()).traverse() : JBIterable.of(component);
    Color fg = enabled ? getLabelForeground() : getLabelDisabledForeground();
    for (Component c : all) {
      c.setEnabled(enabled);
      if (c instanceof JLabel) {
        c.setForeground(fg);
      }
    }
  }

  /**
   * @deprecated Use {@link LinePainter2D#paint(Graphics2D, double, double, double, double)} instead.
   */
  @Deprecated
  public static void drawLine(@NotNull Graphics g, int x1, int y1, int x2, int y2) {
    LinePainter2D.paint((Graphics2D)g, x1, y1, x2, y2);
  }

  public static void drawLine(@NotNull Graphics2D g, int x1, int y1, int x2, int y2, @Nullable Color bgColor, @Nullable Color fgColor) {
    Color oldFg = g.getColor();
    Color oldBg = g.getBackground();
    if (fgColor != null) {
      g.setColor(fgColor);
    }
    if (bgColor != null) {
      g.setBackground(bgColor);
    }
    LinePainter2D.paint(g, x1, y1, x2, y2);
    if (fgColor != null) {
      g.setColor(oldFg);
    }
    if (bgColor != null) {
      g.setBackground(oldBg);
    }
  }

  public static void drawWave(@NotNull Graphics2D g, @NotNull Rectangle rectangle) {
    WavePainter.forColor(g.getColor()).paint(g, (int)rectangle.getMinX(), (int) rectangle.getMaxX(), (int) rectangle.getMaxY());
  }

  @NotNull
  public static String[] splitText(@NotNull String text, @NotNull FontMetrics fontMetrics, int widthLimit, char separator) {
    ArrayList<String> lines = new ArrayList<>();
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


  @NotNull
  public static Font getLabelFont(@NotNull FontSize size) {
    return getFont(size, null);
  }

  @NotNull
  public static Font getFont(@NotNull FontSize size, @Nullable Font base) {
    if (base == null) base = getLabelFont();

    return base.deriveFont(getFontSize(size));
  }

  public static float getFontSize(@NotNull FontSize size) {
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

  @NotNull
  public static Color getLabelFontColor(@NotNull FontColor fontColor) {
    Color defColor = getLabelForeground();
    if (fontColor == FontColor.BRIGHTER) {
      return new JBColor(new Color(Math.min(defColor.getRed() + 50, 255), Math.min(defColor.getGreen() + 50, 255), Math.min(
        defColor.getBlue() + 50, 255)), defColor.darker());
    }
    return defColor;
  }

  private static final Map<Class, Ref<Method>> ourDefaultIconMethodsCache = new ConcurrentHashMap<>();
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

  @NotNull
  public static Color getLabelForeground() {
    return JBColor.namedColor("Label.foreground", new JBColor(Gray._0, Gray.xBB));
  }

  @NotNull
  public static Color getLabelDisabledForeground() {
    return JBColor.namedColor("Label.disabledForeground", JBColor.GRAY);
  }

  @NotNull
  public static Color getContextHelpForeground() {
    return JBColor.namedColor("Label.infoForeground", new JBColor(Gray.x78, Gray.x8C));
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

  @NotNull
  @Deprecated
  public static Color getTreeTextForeground() {
    return getTreeForeground();
  }

  @NotNull
  @Deprecated
  public static Color getTreeTextBackground() {
    return getTreeBackground();
  }

  public static Color getFieldForegroundColor() {
    return UIManager.getColor("field.foreground");
  }

  public static Color getActiveTextColor() {
    return UIManager.getColor("textActiveText");
  }

  @NotNull
  public static Color getInactiveTextColor() {
    return JBColor.namedColor("Component.infoForeground", new JBColor(Gray.x99, Gray.x78));
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getTextFieldBackground()
   */
  @Deprecated
  public static Color getActiveTextFieldBackgroundColor() {
    return getTextFieldBackground();
  }

  public static Color getInactiveTextFieldBackgroundColor() {
    return UIManager.getColor("TextField.inactiveBackground");
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getInactiveTextColor()
   */
  @NotNull
  @Deprecated
  public static Color getTextInactiveTextColor() {
    return getInactiveTextColor();
  }

  public static void installPopupMenuColorAndFonts(@NotNull JComponent contentPane) {
    LookAndFeel.installColorsAndFont(contentPane, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
  }

  public static void installPopupMenuBorder(@NotNull JComponent contentPane) {
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

  @NotNull
  public static Color getToolTipBackground() {
    return JBColor.namedColor("ToolTip.background", new JBColor(Gray.xF2, new Color(0x3c3f41)));
  }

  @NotNull
  public static Color getToolTipActionBackground() {
    return JBColor.namedColor("ToolTip.Actions.background", new JBColor(Gray.xEB, new Color(0x43474a)));
  }

  @NotNull
  public static Color getToolTipForeground() {
    return JBColor.namedColor("ToolTip.foreground", new JBColor(Gray.x00, Gray.xBB));
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

  public static Integer getPropertyMaxGutterIconWidth(@NotNull String propertyPrefix) {
    return (Integer)UIManager.get(propertyPrefix + ".maxGutterIconWidth");
  }

  public static Color getMenuItemDisabledForeground() {
    return UIManager.getColor("MenuItem.disabledForeground");
  }

  public static Object getMenuItemDisabledForegroundObject() {
    return UIManager.get("MenuItem.disabledForeground");
  }

  public static Object getTabbedPanePaintContentBorder(@NotNull final JComponent c) {
    return c.getClientProperty("TabbedPane.paintContentBorder");
  }

  public static boolean isMenuCrossMenuMnemonics() {
    return UIManager.getBoolean("Menu.crossMenuMnemonic");
  }

  public static Color getTableGridColor() {
    return UIManager.getColor("Table.gridColor");
  }

  @NotNull
  public static Color getPanelBackground() {
    return JBColor.PanelBackground;
  }

  public static Color getEditorPaneBackground() {
    return UIManager.getColor("EditorPane.background");
  }

  public static Color getTableFocusCellBackground() {
    return UIManager.getColor(TABLE_FOCUS_CELL_BACKGROUND_PROPERTY);
  }

  public static Color getTextFieldForeground() {
    return UIManager.getColor("TextField.foreground");
  }

  public static Color getTextFieldBackground() {
    return UIManager.getColor("TextField.background");
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

  public static void setSliderIsFilled(@NotNull final JSlider slider, final boolean value) {
    slider.putClientProperty("JSlider.isFilled", value);
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

  @NotNull
  @Deprecated
  public static Color getSeparatorForeground() {
    return JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground();
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

  @NotNull
  @Deprecated
  public static Color getSeparatorColor() {
    return JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground();
  }

  public static Border getTableFocusCellHighlightBorder() {
    return UIManager.getBorder("Table.focusCellHighlightBorder");
  }

  @Deprecated
  public static void setLineStyleAngled(@NotNull final ClientPropertyHolder component) {
  }

  @Deprecated
  public static void setLineStyleAngled(@NotNull final JTree component) {
  }

  public static Color getTableFocusCellForeground() {
    return UIManager.getColor("Table.focusCellForeground");
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getPanelBackground() instead
   */
  @NotNull
  @Deprecated
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

  @NotNull
  public static Icon getBalloonInformationIcon() {
    return AllIcons.General.BalloonInformation;
  }

  @NotNull
  public static Icon getBalloonWarningIcon() {
    return AllIcons.General.BalloonWarning;
  }

  @NotNull
  public static Icon getBalloonErrorIcon() {
    return AllIcons.General.BalloonError;
  }

  public static Icon getRadioButtonIcon() {
    return UIManager.getIcon("RadioButton.icon");
  }

  @NotNull
  public static Icon getTreeNodeIcon(boolean expanded, boolean selected, boolean focused) {
    boolean white = selected && focused || isUnderDarcula();

    Icon expandedDefault = getTreeExpandedIcon();
    Icon collapsedDefault = getTreeCollapsedIcon();
    Icon expandedSelected = getTreeSelectedExpandedIcon();
    Icon collapsedSelected = getTreeSelectedCollapsedIcon();

    int width = Math.max(
      Math.max(expandedDefault.getIconWidth(), collapsedDefault.getIconWidth()),
      Math.max(expandedSelected.getIconWidth(), collapsedSelected.getIconWidth()));
    int height = Math.max(
      Math.max(expandedDefault.getIconHeight(), collapsedDefault.getIconHeight()),
      Math.max(expandedSelected.getIconHeight(), collapsedSelected.getIconHeight()));

    return new CenteredIcon(!white
                            ? expanded ? expandedDefault : collapsedDefault
                            : expanded ? expandedSelected : collapsedSelected,
                            width, height, false);
  }

  @NotNull
  public static Icon getTreeCollapsedIcon() {
    return UIManager.getIcon("Tree.collapsedIcon");
  }

  @NotNull
  public static Icon getTreeExpandedIcon() {
    return UIManager.getIcon("Tree.expandedIcon");
  }

  @Deprecated
  public static Icon getTreeIcon(boolean expanded) {
    return expanded ? getTreeExpandedIcon() : getTreeCollapsedIcon();
  }

  @NotNull
  public static Icon getTreeSelectedCollapsedIcon() {
    Icon icon = UIManager.getIcon("Tree.collapsedSelectedIcon");
    return icon != null ? icon : getTreeCollapsedIcon();
  }

  @NotNull
  public static Icon getTreeSelectedExpandedIcon() {
    Icon icon = UIManager.getIcon("Tree.expandedSelectedIcon");
    return icon != null ? icon : getTreeExpandedIcon();
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

  /**
   * Alloy Look-n-Feel is deprecated and does not supported by IntelliJ Platform
   * @return false
   * @deprecated
   */
  @Deprecated
  public static boolean isUnderAlloyLookAndFeel() {
    return false;
  }

  /**
   * Alloy Look-n-Feel is deprecated and does not supported by IntelliJ Platform
   * @return false
   * @deprecated
   */
  @Deprecated
  public static boolean isUnderAlloyIDEALookAndFeel() {
    return false;
  }

  @Deprecated
  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderWindowsLookAndFeel() {
    return SystemInfo.isWindows && UIManager.getLookAndFeel().getName().equals("Windows");
  }

  @Deprecated
  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderWindowsClassicLookAndFeel() {
    return UIManager.getLookAndFeel().getName().equals("Windows Classic");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderAquaLookAndFeel() {
    return SystemInfo.isMac && UIManager.getLookAndFeel().getName().contains("Mac OS X");
  }


  /**
   * Nimbus Look-n-Feel is deprecated and does not supported by IntelliJ Platform
   * @return false
   * @deprecated
   */
  @Deprecated
  public static boolean isUnderNimbusLookAndFeel() {
    return false;
  }

  /**
   * JGoodies Look-n-Feel is deprecated and does not supported by IntelliJ Platform
   * @return false
   * @deprecated
   */
  @Deprecated
  public static boolean isUnderJGoodiesLookAndFeel() {
    return false;
  }

  public static boolean isUnderAquaBasedLookAndFeel() {
    return SystemInfo.isMac && (isUnderAquaLookAndFeel() || isUnderDarcula() || isUnderIntelliJLaF());
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderDarcula() {
    return UIManager.getLookAndFeel().getName().contains("Darcula");
  }

  public static boolean isUnderDefaultMacTheme() {
    return SystemInfo.isMac && isUnderIntelliJLaF() && Registry.is("ide.intellij.laf.macos.ui") && !isCustomTheme();
  }

  public static boolean isUnderWin10LookAndFeel() {
    return SystemInfo.isWindows && isUnderIntelliJLaF() && Registry.is("ide.intellij.laf.win10.ui") && !isCustomTheme();
  }

  private static boolean isCustomTheme() {
    LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
    if (lookAndFeel instanceof UserDataHolder) {
      Boolean value = ((UserDataHolder)lookAndFeel).getUserData(LAF_WITH_THEME_KEY);
      return value != null && value.booleanValue();
    }
    return false;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderIntelliJLaF() {
    return UIManager.getLookAndFeel().getName().contains("IntelliJ");
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isUnderGTKLookAndFeel() {
    return SystemInfo.isXWindow && UIManager.getLookAndFeel().getName().contains("GTK");
  }

  public static boolean isGraphite() {
    if (!SystemInfo.isMac) return false;
    try {
      // https://developer.apple.com/library/mac/documentation/Cocoa/Reference/ApplicationKit/Classes/NSCell_Class/index.html#//apple_ref/doc/c_ref/NSGraphiteControlTint
      // NSGraphiteControlTint = 6
      return Foundation.invoke("NSColor", "currentControlTint").intValue() == 6;
    } catch (Exception e) {
      return false;
    }
  }

  @Deprecated
  public static final Color GTK_AMBIANCE_TEXT_COLOR = new Color(223, 219, 210);

  @Deprecated
  public static final Color GTK_AMBIANCE_BACKGROUND_COLOR = new Color(67, 66, 63);

  @Deprecated
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

  @NotNull
  public static Font getToolbarFont() {
    return SystemInfo.isMac ? getLabelFont(UIUtil.FontSize.SMALL) : getLabelFont();
  }

  @Deprecated
  @SuppressWarnings("HardCodedStringLiteral")
  public static boolean isMurrineBasedTheme() {
    final String gtkTheme = getGtkThemeName();
    return "Ambiance".equalsIgnoreCase(gtkTheme) ||
           "Radiance".equalsIgnoreCase(gtkTheme) ||
           "Dust".equalsIgnoreCase(gtkTheme) ||
           "Dust Sand".equalsIgnoreCase(gtkTheme);
  }

  @NotNull
  public static Color shade(@NotNull Color c, final double factor, final double alphaFactor) {
    assert factor >= 0 : factor;
    //noinspection UseJBColor
    return new Color(
      Math.min((int)Math.round(c.getRed() * factor), 255),
      Math.min((int)Math.round(c.getGreen() * factor), 255),
      Math.min((int)Math.round(c.getBlue() * factor), 255),
      Math.min((int)Math.round(c.getAlpha() * alphaFactor), 255)
    );
  }

  @NotNull
  public static Color mix(@NotNull Color c1, final Color c2, final double factor) {
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
    return false;
  }

  public static boolean isUnderNativeMacLookAndFeel() {
    return isUnderAquaLookAndFeel() || isUnderDarcula();
  }

  public static int getListCellHPadding() {
    return isUnderDefaultMacTheme() ? 8 :
           isUnderWin10LookAndFeel() ? 2 :
           7;
  }

  public static int getListCellVPadding() {
    return 1;
  }

  @NotNull
  public static Insets getListCellPadding() {
    return JBUI.insets(getListCellVPadding(), getListCellHPadding());
  }

  @NotNull
  public static Insets getListViewportPadding() {
    return isUnderNativeMacLookAndFeel() ? JBUI.insets(1, 0) : JBUI.emptyInsets();
  }

  public static boolean isToUseDottedCellBorder() {
    return !isUnderNativeMacLookAndFeel();
  }

  public static boolean isControlKeyDown(@NotNull MouseEvent mouseEvent) {
    return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  @NotNull
  public static String[] getValidFontNames(final boolean familyName) {
    Set<String> result = new TreeSet<>();

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

  @NotNull
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

  public static void setupEnclosingDialogBounds(@NotNull final JComponent component) {
    component.revalidate();
    component.repaint();
    final Window window = SwingUtilities.windowForComponent(component);
    if (window != null &&
        (window.getSize().height < window.getMinimumSize().height || window.getSize().width < window.getMinimumSize().width)) {
      window.pack();
    }
  }

  @NotNull
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

  public static void appendColor(@NotNull final Color color, @NotNull StringBuilder sb) {
    if (color.getRed() < 16) sb.append('0');
    sb.append(Integer.toHexString(color.getRed()));
    if (color.getGreen() < 16) sb.append('0');
    sb.append(Integer.toHexString(color.getGreen()));
    if (color.getBlue() < 16) sb.append('0');
    sb.append(Integer.toHexString(color.getBlue()));
  }

  public static void drawDottedRectangle(@NotNull Graphics g, @NotNull Rectangle r) {
    drawDottedRectangle(g, r.x, r.y, r.x + r.width, r.y + r.height);
  }

  /**
   * @param g  graphics.
   * @param x  top left X coordinate.
   * @param y  top left Y coordinate.
   * @param x1 right bottom X coordinate.
   * @param y1 right bottom Y coordinate.
   */
  public static void drawDottedRectangle(@NotNull Graphics g, int x, int y, int x1, int y1) {
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
  public static void drawBoldDottedLine(@NotNull Graphics2D g,
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

  @SuppressWarnings("UnregisteredNamedColor")
  public static void drawSearchMatch(@NotNull Graphics2D g,
                                     final float startX,
                                     final float endX,
                                     final int height) {
    Color c1 = JBColor.namedColor("SearchMatch.startBackground", JBColor.namedColor("SearchMatch.startColor", 0xffeaa2));
    Color c2 = JBColor.namedColor("SearchMatch.endBackground", JBColor.namedColor("SearchMatch.endColor", 0xffd042));
    drawSearchMatch(g, startX, endX, height, c1, c2);
  }

  public static void drawSearchMatch(@NotNull Graphics2D g, float startXf, float endXf, int height, Color c1, Color c2) {
    GraphicsConfig config = new GraphicsConfig(g);
    float alpha = JBUI.getInt("SearchMatch.transparency", 70) / 100f;
    alpha = alpha < 0 || alpha > 1 ? 0.7f : alpha;
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
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

    final boolean drawRound = endXf - startXf > 4;
    if (drawRound) {
      drawLine(g ,startX - 1, 4, startX - 1, height - 4);
      drawLine(g ,endX, 4, endX, height - 4);

      g.setColor(new Color(100, 100, 100, 50));
      drawLine(g ,startX - 1, 4, startX - 1, height - 4);
      drawLine(g ,endX, 4, endX, height - 4);

      drawLine(g ,startX, 3, endX - 1, 3);
      drawLine(g ,startX, height - 3, endX - 1, height - 3);
    }

    config.restore();
  }

  public static void drawRectPickedOut(@NotNull Graphics2D g, int x, int y, int w, int h) {
    drawLine(g ,x + 1, y, x + w - 1, y);
    drawLine(g ,x + w, y + 1, x + w, y + h - 1);
    drawLine(g ,x + w - 1, y + h, x + 1, y + h);
    drawLine(g ,x, y + 1, x, y + h - 1);
  }

  private static void drawBoringDottedLine(@NotNull final Graphics2D g,
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
      drawLine(g ,dotXi, lineY, dotXi + 1, lineY);
      drawLine(g ,dotXi, lineY + 1, dotXi + 1, lineY + 1);
    }

    // restore color
    g.setColor(oldColor);
  }

  public static void drawGradientHToolbarBackground(@NotNull Graphics g, final int width, final int height) {
    final Graphics2D g2d = (Graphics2D)g;
    g2d.setPaint(getGradientPaint(0, 0, Gray._215, 0, height, Gray._200));
    g2d.fillRect(0, 0, width, height);
  }

  public static void drawHeader(@NotNull Graphics g, int x, int width, int height, boolean active, boolean drawTopLine) {
    drawHeader(g, x, width, height, active, false, drawTopLine, true);
  }

  public static void drawHeader(@NotNull Graphics g,
                                int x,
                                int width,
                                int height,
                                boolean active,
                                boolean toolWindow,
                                boolean drawTopLine,
                                boolean drawBottomLine) {
    GraphicsConfig config = GraphicsUtil.disableAAPainting(g);
    try {
      g.setColor(JBUI.CurrentTheme.ToolWindow.headerBackground(active));
      g.fillRect(x, 0, width, height);

      g.setColor(JBUI.CurrentTheme.ToolWindow.headerBorderBackground());
      if (drawTopLine) drawLine(g ,x, 0, width, 0);
      if (drawBottomLine) drawLine(g ,x, height - 1, width, height - 1);

    }
    finally {
      config.restore();
    }
  }

  public static void drawDoubleSpaceDottedLine(@NotNull final Graphics2D g,
                                               final int start,
                                               final int end,
                                               final int xOrY,
                                               final Color fgColor,
                                               boolean horizontal) {

    g.setColor(fgColor);
    for (int dot = start; dot < end; dot += 3) {
      if (horizontal) {
        drawLine(g ,dot, xOrY, dot, xOrY);
      }
      else {
        drawLine(g ,xOrY, dot, xOrY, dot);
      }
    }
  }

  private static void drawAppleDottedLine(@NotNull final Graphics2D g,
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
  public static void applyRenderingHints(@NotNull Graphics g) {
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
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
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
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
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
   * Creates a HiDPI-aware BufferedImage in the graphics config scale.
   *
   * @param gc the graphics config
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   * @param rm the rounding mode to apply to width/height (for a HiDPI-aware image, the rounding is applied in the device space)
   *
   * @return a HiDPI-aware BufferedImage in the graphics scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  @NotNull
  public static BufferedImage createImage(GraphicsConfiguration gc, double width, double height, int type, @NotNull RoundingMode rm) {
    if (isJreHiDPI(gc)) {
      return RetinaImage.create(gc, width, height, type, rm);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(rm.round(width), rm.round(height), type);
  }

  /**
   * @see #createImage(GraphicsConfiguration, double, double, int, RoundingMode)
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  @NotNull
  public static BufferedImage createImage(ScaleContext ctx, double width, double height, int type, @NotNull RoundingMode rm) {
    if (isJreHiDPI(ctx)) {
      return RetinaImage.create(ctx, width, height, type, rm);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(rm.round(width), rm.round(height), type);
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
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  @NotNull
  public static BufferedImage createImage(Graphics g, int width, int height, int type) {
    return createImage(g, width, height, type, RoundingMode.FLOOR);
  }

  /**
   * @see #createImage(GraphicsConfiguration, double, double, int, RoundingMode)
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  @NotNull
  public static BufferedImage createImage(Graphics g, double width, double height, int type, @NotNull RoundingMode rm) {
    if (g instanceof Graphics2D) {
      Graphics2D g2d = (Graphics2D)g;
      if (isJreHiDPI(g2d)) {
        return RetinaImage.create(g2d, width, height, type, rm);
      }
      //noinspection UndesirableClassUsage
      return new BufferedImage(rm.round(width), rm.round(height), type);
    }
    return createImage(rm.round(width), rm.round(height), type);
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the component scale.
   *
   * @param comp the component associated with the target graphics device
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in the component scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
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
  @Deprecated
  @NotNull
  public static BufferedImage createImageForGraphics(Graphics2D g, int width, int height, int type) {
    return createImage(g, width, height, type);
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

  public static void paintWithXorOnRetina(@NotNull Dimension size, @NotNull Graphics g, @NotNull Consumer<? super Graphics2D> paintRoutine) {
    paintWithXorOnRetina(size, g, true, paintRoutine);
  }

  /**
   * Direct painting into component's graphics with XORMode is broken on retina-mode so we need to paint into an intermediate buffer first.
   */
  public static void paintWithXorOnRetina(@NotNull Dimension size,
                                          @NotNull Graphics g,
                                          boolean useRetinaCondition,
                                          @NotNull Consumer<? super Graphics2D> paintRoutine) {
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

  /**
   * In tests, consider using {@link com.intellij.testFramework.PlatformTestUtil#dispatchAllInvocationEventsInIdeEventQueue()}
   * @see #pump()
   */
  @TestOnly
  public static void dispatchAllInvocationEvents() {
    assert EdtInvocationManager.getInstance().isEventDispatchThread() : Thread.currentThread() + "; EDT: "+getEventQueueThread();
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    Method dispatchEventMethod = ObjectUtils.notNull(ReflectionUtil.getDeclaredMethod(eventQueue.getClass(), "dispatchEvent", AWTEvent.class));
    for (int i = 1; ; i++) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      try {
        event = eventQueue.getNextEvent();
        if (event instanceof InvocationEvent) {
          dispatchEventMethod.invoke(eventQueue, event);
        }
      }
      catch (InvocationTargetException e) {
        ExceptionUtil.rethrowAllAsUnchecked(e.getCause());
      }
      catch (Exception e) {
        getLogger().error(e);
      }

      if (i % 10000 == 0) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Suspiciously many (" + i + ") AWT events, last dispatched " + event);
      }
    }
  }

  @NotNull
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
    final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    SwingUtilities.invokeLater(() -> {
      //noinspection CollectionAddedToSelf
      queue.offer(queue);
    });
    try {
      queue.take();
    }
    catch (InterruptedException e) {
      getLogger().error(e);
    }
  }

  public static void addAwtListener(@NotNull final AWTEventListener listener, long mask, @NotNull Disposable parent) {
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

  public static void drawVDottedLine(@NotNull Graphics2D g, int lineX, int startY, int endY, @Nullable final Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      drawLine(g, lineX, startY, lineX, endY);
    }

    g.setColor(fgColor);
    for (int i = startY / 2 * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  public static void drawHDottedLine(@NotNull Graphics2D g, int startX, int endX, int lineY, @Nullable final Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      drawLine(g, startX, lineY, endX, lineY);
    }

    g.setColor(fgColor);

    for (int i = startX / 2 * 2; i < endX; i += 2) {
      g.drawRect(i, lineY, 0, 0);
    }
  }

  public static void drawDottedLine(@NotNull Graphics2D g, int x1, int y1, int x2, int y2, @Nullable final Color bgColor, final Color fgColor) {
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

  public static void drawStringWithHighlighting(@NotNull Graphics g, @NotNull String s, int x, int y, Color foreground, Color highlighting) {
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
  public static void drawCenteredString(@NotNull Graphics2D g, @NotNull Rectangle rect, @NotNull String str, boolean horzCentered, boolean vertCentered) {
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
  public static void drawCenteredString(@NotNull Graphics2D g, @NotNull Rectangle rect, @NotNull String str) {
    drawCenteredString(g, rect, str, true, true);
  }

  /**
   * @param component to check whether it has focus within its component hierarchy
   * @return {@code true} if component or one of its children has focus
   * @see Component#isFocusOwner()
   */
  public static boolean isFocusAncestor(@NotNull Component component) {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) return false;
    if (owner == component) return true;
    return SwingUtilities.isDescendingFrom(owner, component);
  }


  public static boolean isCloseClick(@NotNull MouseEvent e) {
    return isCloseClick(e, MouseEvent.MOUSE_PRESSED);
  }

  public static boolean isCloseClick(@NotNull MouseEvent e, int effectiveType) {
    if (e.isPopupTrigger() || e.getID() != effectiveType) return false;
    return e.getButton() == MouseEvent.BUTTON2 || e.getButton() == MouseEvent.BUTTON1 && e.isShiftDown();
  }

  public static boolean isActionClick(@NotNull MouseEvent e) {
    return isActionClick(e, MouseEvent.MOUSE_PRESSED);
  }

  public static boolean isActionClick(@NotNull MouseEvent e, int effectiveType) {
    return isActionClick(e, effectiveType, false);
  }

  public static boolean isActionClick(@NotNull MouseEvent e, int effectiveType, boolean allowShift) {
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
    return findParentByCondition(c, Component::isOpaque);
  }

  @Nullable
  public static Component findParentByCondition(@Nullable Component c, @NotNull Condition<? super Component> condition) {
    Component eachParent = c;
    while (eachParent != null) {
      if (condition.value(eachParent)) return eachParent;
      eachParent = eachParent.getParent();
    }
    return null;
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

  public static void layoutRecursively(@NotNull Component component) {
    if (component instanceof JComponent) {
      component.doLayout();
      for (Component child : ((JComponent)component).getComponents()) {
        layoutRecursively(child);
      }
    }
  }

  @NotNull
  @Language("HTML")
  public static String getCssFontDeclaration(@NotNull Font font) {
    return getCssFontDeclaration(font, null, null, null);
  }

  @NotNull
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
    builder.append("ul {list-style:disc; margin-left:15px;}\n");
    return builder.append("</style>").toString();
  }

  @Deprecated
  public static boolean isWinLafOnVista() {
    return SystemInfo.isWinVistaOrNewer && "Windows".equals(UIManager.getLookAndFeel().getName());
  }

  @Deprecated
  public static boolean isStandardMenuLAF() {
    return isWinLafOnVista() ||
           isUnderGTKLookAndFeel();
  }

  @NotNull
  public static Color getFocusedFillColor() {
    return toAlpha(getListSelectionBackground(), 100);
  }

  @NotNull
  public static Color getFocusedBoundsColor() {
    return getBoundsColor();
  }

  @NotNull
  public static Color getBoundsColor() {
    return JBColor.border();
  }

  @NotNull
  public static Color getBoundsColor(boolean focused) {
    return focused ? getFocusedBoundsColor() : getBoundsColor();
  }

  @NotNull
  public static Color toAlpha(final Color color, final int alpha) {
    Color actual = color != null ? color : Color.black;
    return new Color(actual.getRed(), actual.getGreen(), actual.getBlue(), alpha);
  }

  /**
   * @param component to check whether it can be focused or not
   * @return {@code true} if component is not {@code null} and can be focused
   * @see Component#isRequestFocusAccepted(boolean, boolean, sun.awt.CausedFocusEvent.Cause)
   */
  public static boolean isFocusable(@Nullable Component component) {
    return component != null && component.isFocusable() && component.isEnabled() && component.isShowing();
  }

  @Deprecated
  public static void requestFocus(@NotNull final JComponent c) {
    if (c.isShowing()) {
      c.requestFocus();
    }
    else {
      SwingUtilities.invokeLater(c::requestFocus);
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

  public static void disposeProgress(@NotNull final JProgressBar progress) {
    if (!isUnderNativeMacLookAndFeel()) return;

    SwingUtilities.invokeLater(() -> progress.setUI(null));
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

  @NotNull
  public static Color getHeaderActiveColor() {
    return ACTIVE_HEADER_COLOR;
  }

  @NotNull
  public static Color getFocusedBorderColor() {
    return JBUI.CurrentTheme.Focus.focusColor();
  }

  @NotNull
  public static Color getHeaderInactiveColor() {
    return INACTIVE_HEADER_COLOR;
  }

  /**
   * @deprecated use {@link JBColor#border()}
   */
  @NotNull
  @Deprecated
  public static Color getBorderColor() {
    return JBColor.border();
  }

  @NotNull
  public static Font getTitledBorderFont() {
    Font defFont = getLabelFont();
    return defFont.deriveFont(defFont.getSize() - 1f);
  }

  /**
   * @deprecated use getBorderColor instead
   */
  @NotNull
  @Deprecated
  public static Color getBorderInactiveColor() {
    return JBColor.border();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  @NotNull
  @Deprecated
  public static Color getBorderActiveColor() {
    return JBColor.border();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  @NotNull
  @Deprecated
  public static Color getBorderSeparatorColor() {
    return JBColor.border();
  }

  @Nullable
  public static StyleSheet loadStyleSheet(@Nullable URL url) {
    if (url == null) return null;
    try {
      StyleSheet styleSheet = new StyleSheet();
      styleSheet.loadRules(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8), url);
      return styleSheet;
    }
    catch (IOException e) {
      getLogger().warn(url + " loading failed", e);
      return null;
    }
  }

  @NotNull
  public static HTMLEditorKit getHTMLEditorKit() {
    return getHTMLEditorKit(true);
  }

  @NotNull
  public static HTMLEditorKit getHTMLEditorKit(boolean noGapsBetweenParagraphs) {
    return new JBHtmlEditorKit(noGapsBetweenParagraphs);
  }

  public static class JBHtmlEditorKit extends HTMLEditorKit {
    @Override
    public Cursor getDefaultCursor() {
      return null;
    }

    private final StyleSheet style;
    private final HyperlinkListener myHyperlinkListener;

    public JBHtmlEditorKit() {
      this(true);
    }

    public JBHtmlEditorKit(boolean noGapsBetweenParagraphs) {
      style = createStyleSheet();
      if (noGapsBetweenParagraphs) style.addRule("p { margin-top: 0; }");
      myHyperlinkListener = new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          Element element = e.getSourceElement();
          if (element == null) return;
          if (element.getName().equals("img")) return;

          if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
            setUnderlined(true, element);
          } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
            setUnderlined(false, element);
          }
        }

        private void setUnderlined(boolean underlined, @NotNull Element element) {
          AttributeSet attributes = element.getAttributes();
          Object attribute = attributes.getAttribute(HTML.Tag.A);
          if (attribute instanceof MutableAttributeSet) {
            MutableAttributeSet a = (MutableAttributeSet)attribute;
            a.addAttribute(CSS.Attribute.TEXT_DECORATION, underlined ? "underline" : "none");
            ((StyledDocument)element.getDocument()).setCharacterAttributes(element.getStartOffset(),
                                                                           element.getEndOffset() - element.getStartOffset(),
                                                                           a, false);
          }
        }
      };
    }

    @Override
    public StyleSheet getStyleSheet() {
      return style;
    }

    @Override
    public Document createDefaultDocument() {
      StyleSheet styles = getStyleSheet();
      // static class instead anonymous for exclude $this [memory leak]
      StyleSheet ss = new StyleSheetCompressionThreshold();
      ss.addStyleSheet(styles);

      HTMLDocument doc = new HTMLDocument(ss);
      doc.setParser(getParser());
      doc.setAsynchronousLoadPriority(4);
      doc.setTokenThreshold(100);
      return doc;
    }

    public static StyleSheet createStyleSheet() {
      StyleSheet style = new StyleSheet();
      style.addStyleSheet(isUnderDarcula() ? (StyleSheet)UIManager.getDefaults().get("StyledEditorKit.JBDefaultStyle") : DEFAULT_HTML_KIT_CSS);
      style.addRule("code { font-size: 100%; }"); // small by Swing's default
      style.addRule("small { font-size: small; }"); // x-small by Swing's default
      style.addRule("a { text-decoration: none;}");
      // override too large default margin "ul {margin-left-ltr: 50; margin-right-rtl: 50}" from javax/swing/text/html/default.css
      style.addRule("ul { margin-left-ltr: 10; margin-right-rtl: 10; }");
      // override too large default margin "ol {margin-left-ltr: 50; margin-right-rtl: 50}" from javax/swing/text/html/default.css
      // Select ol margin to have the same indentation as "ul li" and "ol li" elements (seems value 22 suites well)
      style.addRule("ol { margin-left-ltr: 22; margin-right-rtl: 22; }");

      return style;
    }

    @Override
    public void install(final JEditorPane pane) {
      super.install(pane);
      // JEditorPane.HONOR_DISPLAY_PROPERTIES must be set after HTMLEditorKit is completely installed
      pane.addPropertyChangeListener("editorKit", new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent e) {
          // In case JBUI user scale factor changes, the font will be auto-updated by BasicTextUI.installUI()
          // with a font of the properly scaled size. And is then propagated to CSS, making HTML text scale dynamically.

          // The default JEditorPane's font is the label font, seems there's no need to reset it here.
          // If the default font is overridden, more so we should not reset it.
          // However, if the new font is not UIResource - it won't be auto-scaled.
          // [tav] dodo: remove the next two lines in case there're no regressions
          //Font font = getLabelFont();
          //pane.setFont(font);

          // let CSS font properties inherit from the pane's font
          pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
          pane.removePropertyChangeListener(this);
        }
      });
      pane.addHyperlinkListener(myHyperlinkListener);

      List<LinkController> listeners1 = filterLinkControllerListeners(pane.getMouseListeners());
      List<LinkController> listeners2 = filterLinkControllerListeners(pane.getMouseMotionListeners());
      // replace just the original listener
      if (listeners1.size() == 1 && listeners1.equals(listeners2)) {
        LinkController oldLinkController = listeners1.get(0);
        pane.removeMouseListener(oldLinkController);
        pane.removeMouseMotionListener(oldLinkController);
        MouseExitSupportLinkController newLinkController = new MouseExitSupportLinkController();
        pane.addMouseListener(newLinkController);
        pane.addMouseMotionListener(newLinkController);
      }
    }

    @NotNull
    private static List<LinkController> filterLinkControllerListeners(@NotNull Object[] listeners) {
      return ContainerUtil.mapNotNull(listeners, o -> ObjectUtils.tryCast(o, LinkController.class));
    }

    @Override
    public void deinstall(@NotNull JEditorPane c) {
      c.removeHyperlinkListener(myHyperlinkListener);
      super.deinstall(c);
    }
  }

  private static class StyleSheetCompressionThreshold extends StyleSheet {
    @Override
    protected int getCompressionThreshold() {
      return -1;
    }
  }

  // Workaround for https://bugs.openjdk.java.net/browse/JDK-8202529
  private static class MouseExitSupportLinkController extends HTMLEditorKit.LinkController {
    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      mouseMoved(new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiersEx(), -1, -1, e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }
  }

  public static class JBWordWrapHtmlEditorKit extends JBHtmlEditorKit {
    private final HTMLFactory myFactory = new HTMLFactory() {
      @Override
      public View create(Element e) {
        View view = super.create(e);
        if (view instanceof javax.swing.text.html.ParagraphView) {
          // wrap too long words, for example: ATEST_TABLE_SIGNLE_ROW_UPDATE_AUTOCOMMIT_A_FIK
          return new ParagraphView(e) {
            @Override
            protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r) {
              if (r == null) {
                r = new SizeRequirements();
              }
              r.minimum = (int)layoutPool.getMinimumSpan(axis);
              r.preferred = Math.max(r.minimum, (int)layoutPool.getPreferredSpan(axis));
              r.maximum = Integer.MAX_VALUE;
              r.alignment = 0.5f;
              return r;
            }
          };
        }
        return view;
      }
    };

    @Override
    public ViewFactory getViewFactory() {
      return myFactory;
    }
  }

  @NotNull
  public static FontUIResource getFontWithFallback(@NotNull Font font) {
    return getFontWithFallback(font.getFamily(), font.getStyle(), font.getSize());
  }

  @NotNull
  public static FontUIResource getFontWithFallback(@Nullable String familyName, @JdkConstants.FontStyle int style, int size) {
    // On macOS font fallback is implemented in JDK by default
    // (except for explicitly registered fonts, e.g. the fonts we bundle with IDE, for them we don't have a solution now)
    Font fontWithFallback = SystemInfo.isMac ? new Font(familyName, style, size) : new StyleContext().getFont(familyName, style, size);
    return fontWithFallback instanceof FontUIResource ? (FontUIResource)fontWithFallback : new FontUIResource(fontWithFallback);
  }

  //Escape error-prone HTML data (if any) when we use it in renderers, see IDEA-170768
  public static <T> T htmlInjectionGuard(T toRender) {
    if (toRender instanceof String && ((String)toRender).toLowerCase(Locale.US).startsWith("<html>")) {
      //noinspection unchecked
      return (T) ("<html>" + StringUtil.escapeXmlEntities((String)toRender));
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

  @NotNull
  public static String toHtml(@NotNull String html) {
    return toHtml(html, 0);
  }

  @NotNull
  @NonNls
  public static String toHtml(@NotNull String html, final int hPadding) {
    html = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
    Font font = getLabelFont();
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : JBUI.scale(11);
    return "<html><style>body { font-family: "
           + family + "; font-size: "
           + size + ";} ul li {list-style-type:circle;}</style>"
           + addPadding(html, hPadding) + "</html>";
  }

  @NotNull
  public static String addPadding(@NotNull String html, int hPadding) {
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
        getLogger().error(e);
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
    invokeAndWaitIfNeeded((Runnable)() -> result.set(computable.compute()));
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
      EdtInvocationManager.getInstance().invokeAndWait(() -> {
        try {
          runnable.run();
        }
        catch (Throwable throwable) {
          ref.set(throwable);
        }
      });
      if (!ref.isNull()) throw ref.get();
    }
  }

  public static boolean isFocusProxy(@Nullable Component c) {
    return c instanceof JComponent && Boolean.TRUE.equals(((JComponent)c).getClientProperty(FOCUS_PROXY_KEY));
  }

  public static void setFocusProxy(@NotNull JComponent c, boolean isProxy) {
    c.putClientProperty(FOCUS_PROXY_KEY, isProxy ? Boolean.TRUE : null);
  }

  public static void maybeInstall(@NotNull InputMap map, String action, KeyStroke stroke) {
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
  public static void changeBackGround(@NotNull final Component component, final Color background) {
    final Color oldBackGround = component.getBackground();
    if (background == null || !background.equals(oldBackGround)) {
      component.setBackground(background);
    }
  }

  private static String systemLaFClassName;

  @NotNull
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

  private static void configureHtmlKitStylesheet() {
    // save the default JRE CSS and ..
    HTMLEditorKit kit = new HTMLEditorKit();
    DEFAULT_HTML_KIT_CSS = kit.getStyleSheet();
    // .. erase global ref to this CSS so no one can alter it
    kit.setStyleSheet(null);

    // Applied to all JLabel instances, including subclasses. Supported in JBR only.
    UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", JBHtmlEditorKit.createStyleSheet());
  }

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
    else if (SystemInfo.isWindows) {
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
    return JBUI.discreteScale(dpi / 96f);
  }

  public static void addKeyboardShortcut(@NotNull final JComponent target, final AbstractButton button, final KeyStroke keyStroke) {
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

  public static void installComboBoxCopyAction(@NotNull JComboBox comboBox) {
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

  public static void fixFormattedField(@NotNull JFormattedTextField field) {
    if (SystemInfo.isMac) {
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      if (toolkit instanceof HeadlessToolkit) return;
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
    return g instanceof PrintGraphics || g instanceof PrinterGraphics;
  }

  public static int getSelectedButton(@NotNull ButtonGroup group) {
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

  public static void setSelectedButton(@NotNull ButtonGroup group, int index) {
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
  public static void setComboBoxEditorBounds(int x, int y, int width, int height, @NotNull JComponent editor) {
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
  @Contract(pure = true)
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
    return UI_TRAVERSER.withRoot(component).expandAndFilter(o -> !(o instanceof CellRendererPane));
  }

  public static final Key<Iterable<? extends Component>> NOT_IN_HIERARCHY_COMPONENTS = Key.create("NOT_IN_HIERARCHY_COMPONENTS");

  private static final JBTreeTraverser<Component> UI_TRAVERSER = JBTreeTraverser.from((Function<Component, JBIterable<Component>>)c -> {
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
  });

  private static final Function.Mono<Component> COMPONENT_PARENT = new Function.Mono<Component>() {
    @Override
    public Component fun(Component c) {
      return c.getParent();
    }
  };


  public static void scrollListToVisibleIfNeeded(@NotNull final JList list) {
    SwingUtilities.invokeLater(() -> {
      final int selectedIndex = list.getSelectedIndex();
      if (selectedIndex >= 0) {
        final Rectangle visibleRect = list.getVisibleRect();
        final Rectangle cellBounds = list.getCellBounds(selectedIndex, selectedIndex);
        if (!visibleRect.contains(cellBounds)) {
          list.scrollRectToVisible(cellBounds);
        }
      }
    });
  }

  @Nullable
  public static <T extends JComponent> T findComponentOfType(JComponent parent, Class<T> cls) {
    if (parent == null || cls.isInstance(parent)) {
      return cls.cast(parent);
    }
    for (Component component : parent.getComponents()) {
      if (component instanceof JComponent) {
        T comp = findComponentOfType((JComponent)component, cls);
        if (comp != null) return comp;
      }
    }
    return null;
  }

  @NotNull
  public static <T extends JComponent> List<T> findComponentsOfType(JComponent parent, @NotNull Class<? extends T> cls) {
    final ArrayList<T> result = new ArrayList<>();
    findComponentsOfType(parent, cls, result);
    return result;
  }

  private static <T extends JComponent> void findComponentsOfType(JComponent parent, @NotNull Class<T> cls, @NotNull List<? super T> result) {
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
    private final List<Pair<String, LineInfo>> myLines = new ArrayList<>();
    private boolean myDrawShadow;
    private Color myShadowColor;
    private float myLineSpacing;
    private Font myFont;
    private Color myColor;

    public TextPainter() {
      myDrawShadow = /*isUnderAquaLookAndFeel() ||*/ isUnderDarcula();
      myShadowColor = isUnderDarcula() ? Gray._0.withAlpha(100) : Gray._220;
      myLineSpacing = 1.0f;
    }

    @NotNull
    public TextPainter withShadow(boolean drawShadow) {
      myDrawShadow = drawShadow;
      return this;
    }

    @NotNull
    public TextPainter withShadow(boolean drawShadow, Color shadowColor) {
      myDrawShadow = drawShadow;
      myShadowColor = shadowColor;
      return this;
    }

    @NotNull
    public TextPainter withLineSpacing(float lineSpacing) {
      myLineSpacing = lineSpacing;
      return this;
    }

    @NotNull
    public TextPainter withColor(Color color) {
      myColor = color;
      return this;
    }

    @NotNull
    public TextPainter withFont(Font font) {
      myFont = font;
      return this;
    }

    @NotNull
    public TextPainter appendLine(String text) {
      if (text == null || text.isEmpty()) return this;
      myLines.add(Pair.create(text, new LineInfo()));
      return this;
    }

    @NotNull
    public TextPainter underlined(@Nullable Color color) {
      if (!myLines.isEmpty()) {
        LineInfo info = myLines.get(myLines.size() - 1).getSecond();
        info.underlined = true;
        info.underlineColor = color;
      }

      return this;
    }

    @NotNull
    public TextPainter withBullet(char c) {
      if (!myLines.isEmpty()) {
        LineInfo info = myLines.get(myLines.size() - 1).getSecond();
        info.withBullet = true;
        info.bulletChar = c;
      }

      return this;
    }

    @NotNull
    public TextPainter withBullet() {
      return withBullet('\u2022');
    }

    @NotNull
    public TextPainter underlined() {
      return underlined(null);
    }

    @NotNull
    public TextPainter smaller() {
      if (!myLines.isEmpty()) {
        myLines.get(myLines.size() - 1).getSecond().smaller = true;
      }

      return this;
    }

    @NotNull
    public TextPainter center() {
      if (!myLines.isEmpty()) {
        myLines.get(myLines.size() - 1).getSecond().center = true;
      }

      return this;
    }

    /**
     * _position(block width, block height) => (x, y) of the block
     */
    public void draw(@NotNull final Graphics g, @NotNull PairFunction<? super Integer, ? super Integer, ? extends Couple<Integer>> _position) {
      GraphicsUtil.setupAntialiasing(g, true, true);
      Font oldFont = null;
      if (myFont != null) {
        oldFont = g.getFont();
        g.setFont(myFont);
      }
      Color oldColor = null;
      if (myColor != null) {
        oldColor = g.getColor();
        g.setColor(myColor);
      }
      try {
        final int[] maxWidth = {0};
        final int[] height = {0};
        final int[] maxBulletWidth = {0};
        ContainerUtil.process(myLines, pair -> {
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
        });

        final Couple<Integer> position = _position.fun(maxWidth[0] + 20, height[0]);
        assert position != null;

        final int[] yOffset = {position.getSecond()};
        ContainerUtil.process(myLines, pair -> {
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
            Color oldColor1 = g.getColor();
            g.setColor(myShadowColor);

            int yOff = 1;
            if (info.withBullet) {
              g.drawString(info.bulletChar + " ", x - fm.stringWidth(" " + info.bulletChar) + xOff, yOffset[0] + yOff);
            }

            g.drawString(text, xOffset + xOff, yOffset[0] + yOff);
            g.setColor(oldColor1);
          }

          if (info.withBullet) {
            g.drawString(info.bulletChar + " ", x - fm.stringWidth(" " + info.bulletChar), yOffset[0]);
          }

          g.drawString(text, xOffset, yOffset[0]);
          if (!StringUtil.isEmpty(shortcut)) {
            Color oldColor1 = g.getColor();
            g.setColor(JBColor.namedColor("Editor.shortcutForeground", new JBColor(new Color(82, 99, 155), new Color(88, 157, 246))));
            g.drawString(shortcut, xOffset + fm.stringWidth(text + (isUnderDarcula() ? " " : "")), yOffset[0]);
            g.setColor(oldColor1);
          }

          if (info.underlined) {
            Color c = null;
            if (info.underlineColor != null) {
              c = g.getColor();
              g.setColor(info.underlineColor);
            }

            drawLine(g ,x - maxBulletWidth[0] - 10, yOffset[0] + fm.getDescent(), x + maxWidth[0] + 10, yOffset[0] + fm.getDescent());
            if (c != null) {
              g.setColor(c);
            }

            if (myDrawShadow) {
              c = g.getColor();
              g.setColor(myShadowColor);
              drawLine(g ,x - maxBulletWidth[0] - 10, yOffset[0] + fm.getDescent() + 1, x + maxWidth[0] + 10,
                         yOffset[0] + fm.getDescent() + 1);
              g.setColor(c);
            }
          }

          yOffset[0] += (fm.getHeight() + fm.getLeading()) * myLineSpacing;

          if (old != null) {
            g.setFont(old);
          }

          return true;
        });
      }
      finally {
        if (oldFont != null) g.setFont(oldFont);
        if (oldColor != null) g.setColor(oldColor);
      }
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

  public static void setFutureRootPane(@NotNull JComponent c, @NotNull JRootPane pane) {
    c.putClientProperty(ROOT_PANE, new WeakReference<>(pane));
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
  public static JComponent mergeComponentsWithAnchor(@NotNull PanelWithAnchor... panels) {
    return mergeComponentsWithAnchor(Arrays.asList(panels));
  }

  @Nullable
  public static JComponent mergeComponentsWithAnchor(@NotNull Collection<? extends PanelWithAnchor> panels) {
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
      SwingUtilities.invokeLater(() -> {
        if (window.isShowing()) {
          window.setSize(newSize);
        }
      });
    }
  }

  @Nullable
  public static Color getColorAt(@NotNull Icon icon, final int x, final int y) {
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

  @NotNull
  public static Color getDecoratedRowColor() {
    return JBColor.namedColor("Table.stripeColor", DECORATED_ROW_BG_COLOR);
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

  public static void suppressFocusStealing (@NotNull Window window) {
    // Focus stealing is not a problem on Mac
    if (SystemInfo.isMac) return;
    if (Registry.is("suppress.focus.stealing") && Registry.is("suppress.focus.stealing.auto.request.focus")) {
      setAutoRequestFocus(window, false);
    }
  }

  public static void setAutoRequestFocus(@NotNull Window onWindow, final boolean set) {
    if (!SystemInfo.isMac) {
      try {
        onWindow.getClass().getMethod("setAutoRequestFocus", boolean.class).invoke(onWindow, set);
      }
      catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        getLogger().debug(e);
      }
    }
  }

  public static void runWhenWindowOpened(@NotNull Window window, @NotNull Runnable runnable) {
    window.addWindowListener(new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        e.getWindow().removeWindowListener(this);
        runnable.run();
      }
    });
  }

  //May have no usages but it's useful in runtime (Debugger "watches", some logging etc.)
  @NotNull
  public static String getDebugText(Component c) {
    StringBuilder builder  = new StringBuilder();
    getAllTextsRecursivelyImpl(c, builder);
    return builder.toString();
  }

  private static void getAllTextsRecursivelyImpl(Component component, @NotNull StringBuilder builder) {
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
    protected void textChanged(@NotNull DocumentEvent e) {
      Document document = e.getDocument();
      if (document instanceof AbstractDocument) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (StackTraceElement element : stackTrace) {
          if (!element.getClassName().equals(JTextComponent.class.getName()) || !element.getMethodName().equals("setText")) continue;
          UndoableEditListener[] undoableEditListeners = ((AbstractDocument)document).getUndoableEditListeners();
          for (final UndoableEditListener listener : undoableEditListeners) {
            if (listener instanceof UndoManager) {
              Runnable runnable = ((UndoManager)listener)::discardAllEdits;
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

  public static void playSoundFromResource(@NotNull final String resourceName) {
    final Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return;
    playSoundFromStream(() -> callerClass.getResourceAsStream(resourceName));
  }

  public static void playSoundFromStream(@NotNull final Factory<? extends InputStream> streamProducer) {
    // The wrapper thread is unnecessary, unless it blocks on the
    // Clip finishing; see comments.
    new Thread(() -> {
      try {
        Clip clip = AudioSystem.getClip();
        InputStream stream = streamProducer.create();
        if (!stream.markSupported()) stream = new BufferedInputStream(stream);
        AudioInputStream inputStream = AudioSystem.getAudioInputStream(stream);
        clip.open(inputStream);

        clip.start();
      }
      catch (Exception e) {
        getLogger().info(e);
      }
    }, "play sound").start();
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
    int patternSize = 50;
    BufferedImage image = createImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics graphics = image.getGraphics();
    graphics.setFont(new Font(genericFontFamily, Font.PLAIN, patternSize));
    String pattern = "Real Font Family";
    Object patternBounds = graphics.getFontMetrics().getStringBounds(pattern, graphics);
    List<String> GENERIC = Arrays.asList(Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SANS_SERIF, Font.SERIF);
    for (String family: GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
      if (GENERIC.contains(family)) continue;
      graphics.setFont(new Font(family, Font.PLAIN, patternSize));
      if (graphics.getFontMetrics().getStringBounds(pattern, graphics).equals(patternBounds)) {
        if (ourRealFontFamilies == null) {
          ourRealFontFamilies = new HashMap<>();
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

  @NotNull
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
        catch (IllegalAccessException | InvocationTargetException e) {
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

  @NotNull
  public static Image getDebugImage(@NotNull Component component) {
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
  public static boolean hasComponentOfType(@NotNull Component component, @NotNull Class<?>... types) {
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
  @NotNull
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

  public static void setCursor(@NotNull Component component, Cursor cursor) {
    // cursor is updated by native code even if component has the same cursor, causing performance problems (IDEA-167733)
    if(component.isCursorSet() && component.getCursor() == cursor) return;
    component.setCursor(cursor);
  }

  public static boolean haveCommonOwner(Component c1, Component c2) {
    if (c1 == null || c2 == null) return false;
    Window c1Ancestor = findWindowAncestor(c1);
    Window c2Ancestor = findWindowAncestor(c2);

    Set <Window> ownerSet = new HashSet<>();

    Window owner = c1Ancestor;

    while (owner != null && !(owner instanceof JDialog || owner instanceof JFrame)) {
      ownerSet.add(owner);
      owner = owner.getOwner();
    }

    owner = c2Ancestor;

    while (owner != null && !(owner instanceof JDialog || owner instanceof JFrame)) {
      if (ownerSet.contains(owner)) return true;
      owner = owner.getOwner();
    }

    return false;
  }

  private static Window findWindowAncestor(@NotNull Component c) {
    return c instanceof Window ? (Window)c : SwingUtilities.getWindowAncestor(c);
  }

  public static boolean isHelpButton(Component button) {
    return button instanceof JButton && "help".equals(((JComponent)button).getClientProperty("JButton.buttonType"));
  }

  public static void typeAheadUntilFocused(InputEvent event, @NotNull Component component) {
    getLogger().assertTrue(component.isFocusable());
    Method enqueueKeyEventsMethod = ReflectionUtil.getDeclaredMethod(KeyboardFocusManager.class, "enqueueKeyEvents", long.class, Component.class);
    try {
      if (enqueueKeyEventsMethod != null) {
        enqueueKeyEventsMethod.invoke(KeyboardFocusManager.getCurrentKeyboardFocusManager(),
                                      event != null ? event.getWhen() : System.currentTimeMillis(), component);
      }
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      getLogger().debug(e);
    }
  }

  public static boolean isRetina(@NotNull GraphicsDevice device) {
    return UIUtil.DetectRetinaKit.isOracleMacRetinaDevice(device);
  }

  /** Employs a common pattern to use {@code Graphics}. This is a non-distractive approach
   * all modifications on {@code Graphics} are metter only inside the {@code Consumer} block
   *
   * @param originGraphics graphics to work with
   * @param drawingConsumer you can use the Graphics2D object here safely
   */
  public static void useSafely(@NotNull Graphics originGraphics, @NotNull Consumer<? super Graphics2D> drawingConsumer) {
    Graphics2D graphics = (Graphics2D)originGraphics.create();
    try {
      drawingConsumer.consume(graphics);
    }
    finally {
      graphics.dispose();
    }
  }


  private static final class UnfocusedSelection {
    private static final Color BACKGROUND = new JBColor(0xD4D4D4, 0x0D293E);
    private static final Color LIST_BACKGROUND = JBColor.namedColor("List.selectionInactiveBackground", BACKGROUND);
    private static final Color TREE_BACKGROUND = JBColor.namedColor("Tree.selectionInactiveBackground", BACKGROUND);
    private static final Color TABLE_BACKGROUND = JBColor.namedColor("Table.selectionInactiveBackground", BACKGROUND);
  }


  // List

  @NotNull
  public static Font getListFont() {
    Font font = UIManager.getFont("List.font");
    return font != null ? font : getLabelFont();
  }

  // background

  private static final Color LIST_BACKGROUND = JBColor.namedColor("List.background", new JBColor(0xffffff, 0x3c3f41));

  @NotNull
  public static Color getListBackground() {
    return LIST_BACKGROUND;
  }

  private static final JBValue SELECTED_ITEM_ALPHA = new JBValue.UIInteger("List.selectedItemAlpha", 75);

  @NotNull
  public static Color getListSelectionBackground(boolean focused) {
    if (!focused) return UnfocusedSelection.LIST_BACKGROUND;
    Color color = UIManager.getColor("List.selectionBackground");
    double alpha = SELECTED_ITEM_ALPHA.getFloat() / 100.0;
    //noinspection UseJBColor
    return isUnderDefaultMacTheme() && alpha >= 0 && alpha <= 1.0 ? ColorUtil.mix(Color.WHITE, color, alpha) : color;
  }

  @NotNull
  public static Dimension updateListRowHeight(@NotNull Dimension size) {
    size.height = Math.max(size.height, UIManager.getInt("List.rowHeight"));
    return size;
  }

  @NotNull
  public static Color getListBackground(boolean selected, boolean focused) {
    return !selected ? getListBackground() : getListSelectionBackground(focused);
  }

  @NotNull
  @Deprecated
  public static Color getListBackground(boolean selected) {
    return getListBackground(selected, true);
  }

  @NotNull
  @Deprecated
  public static Color getListSelectionBackground() {
    return getListSelectionBackground(true);
  }

  @NotNull
  @Deprecated
  public static Color getListUnfocusedSelectionBackground() {
    return getListSelectionBackground(false);
  }

  // foreground

  @NotNull
  public static Color getListForeground() {
    return UIManager.getColor("List.foreground");
  }

  @NotNull
  public static Color getListSelectionForeground(boolean focused) {
    Color foreground = UIManager.getColor(focused ? "List.selectionForeground" : "List.selectionInactiveForeground");
    if (focused && foreground == null) foreground = UIManager.getColor("List[Selected].textForeground");  // Nimbus
    return foreground != null ? foreground : getListForeground();
  }

  @NotNull
  public static Color getListForeground(boolean selected, boolean focused) {
    return !selected ? getListForeground() : getListSelectionForeground(focused);
  }

  @NotNull
  @Deprecated
  public static Color getListForeground(boolean selected) {
    return getListForeground(selected, true);
  }

  @NotNull
  @Deprecated
  public static Color getListSelectionForeground() {
    return getListSelectionForeground(true);
  }


  // Tree

  @NotNull
  public static Font getTreeFont() {
    Font font = UIManager.getFont("Tree.font");
    return font != null ? font : getLabelFont();
  }

  // background

  @NotNull
  public static Color getTreeBackground() {
    return UIManager.getColor("Tree.background");
  }

  @NotNull
  public static Color getTreeSelectionBackground(boolean focused) {
    if (!focused) return UnfocusedSelection.TREE_BACKGROUND;
    return UIManager.getColor("Tree.selectionBackground");
  }

  @NotNull
  public static Color getTreeBackground(boolean selected, boolean focused) {
    return !selected ? getTreeBackground() : getTreeSelectionBackground(focused);
  }

  @NotNull
  @Deprecated
  public static Color getTreeSelectionBackground() {
    return getTreeSelectionBackground(true);
  }

  @NotNull
  @Deprecated
  public static Color getTreeUnfocusedSelectionBackground() {
    return getTreeSelectionBackground(false);
  }

  // foreground

  @NotNull
  public static Color getTreeForeground() {
    return UIManager.getColor("Tree.foreground");
  }

  @NotNull
  public static Color getTreeSelectionForeground(boolean focused) {
    Color foreground = UIManager.getColor(focused ? "Tree.selectionForeground" : "Tree.selectionInactiveForeground");
    return foreground != null ? foreground : getTreeForeground();
  }

  @NotNull
  public static Color getTreeForeground(boolean selected, boolean focused) {
    return !selected ? getTreeForeground() : getTreeSelectionForeground(focused);
  }

  @NotNull
  @Deprecated
  public static Color getTreeSelectionForeground() {
    return getTreeSelectionForeground(true);
  }


  // Table

  @NotNull
  public static Font getTableFont() {
    Font font = UIManager.getFont("Table.font");
    return font != null ? font : getLabelFont();
  }

  // background

  @NotNull
  public static Color getTableBackground() {
    return UIManager.getColor("Table.background");
  }

  @NotNull
  public static Color getTableSelectionBackground(boolean focused) {
    if (!focused) return UnfocusedSelection.TABLE_BACKGROUND;
    return UIManager.getColor("Table.selectionBackground");
  }

  @NotNull
  public static Color getTableBackground(boolean selected, boolean focused) {
    return !selected ? getTableBackground() : getTableSelectionBackground(focused);
  }

  @NotNull
  @Deprecated
  public static Color getTableBackground(boolean selected) {
    return getTableBackground(selected, true);
  }

  @NotNull
  @Deprecated
  public static Color getTableSelectionBackground() {
    return getTableSelectionBackground(true);
  }

  @NotNull
  @Deprecated
  public static Color getTableUnfocusedSelectionBackground() {
    return getTableSelectionBackground(false);
  }

  // foreground

  @NotNull
  public static Color getTableForeground() {
    return UIManager.getColor("Table.foreground");
  }

  @NotNull
  public static Color getTableSelectionForeground(boolean focused) {
    Color foreground = UIManager.getColor(focused ? "Table.selectionForeground" : "Table.selectionInactiveForeground");
    return foreground != null ? foreground : getTreeForeground();
  }

  @NotNull
  public static Color getTableForeground(boolean selected, boolean focused) {
    return !selected ? getTableForeground() : getTableSelectionForeground(focused);
  }

  @NotNull
  @Deprecated
  public static Color getTableForeground(boolean selected) {
    return getTableForeground(selected, true);
  }

  @NotNull
  @Deprecated
  public static Color getTableSelectionForeground() {
    return UIManager.getColor("Table.selectionForeground");
  }
}
