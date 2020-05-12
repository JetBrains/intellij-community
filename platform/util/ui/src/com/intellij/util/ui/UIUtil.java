// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.BundleBase;
import com.intellij.diagnostic.LoadingState;
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
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.*;
import sun.awt.HeadlessToolkit;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.html.StyleSheet;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RGBImageFilter;
import java.awt.print.PrinterGraphics;
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
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public final class UIUtil {
  static {
    LoadingState.LAF_INITIALIZED.checkOccurred();
  }

  public static final String BORDER_LINE = "<hr size=1 noshade>";
  @NonNls public static final String BR = "<br/>";

  public static final Key<Boolean> LAF_WITH_THEME_KEY = Key.create("Laf.with.ui.theme");
  public static final Key<String> PLUGGABLE_LAF_KEY = Key.create("Pluggable.laf.name");

  // cannot be static because logging maybe not configured yet
  private static @NotNull Logger getLogger() {
    return Logger.getInstance(UIUtil.class);
  }

  public static void decorateWindowHeader(JRootPane pane) {
    if (pane != null && SystemInfo.isMacOSMojave) {
      pane.putClientProperty("jetbrains.awt.windowDarkAppearance", StartupUiUtil.isUnderDarcula());
    }
  }

  public static void setCustomTitleBar(@NotNull Window window, @NotNull JRootPane rootPane, java.util.function.Consumer<Runnable> onDispose) {
    if (!SystemInfo.isMac || !Registry.is("ide.mac.transparentTitleBarAppearance", false)) {
      return;
    }

    JBInsets topWindowInset = JBUI.insetsTop(24);
    rootPane.putClientProperty("jetbrains.awt.transparentTitleBarAppearance", true);
    AbstractBorder customDecorationBorder = new AbstractBorder() {
      @Override
      public Insets getBorderInsets(Component c) {
        return topWindowInset;
      }

      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D graphics = (Graphics2D)g.create();
        try {
          Rectangle headerRectangle = new Rectangle(0, 0, c.getWidth(), topWindowInset.top);
          graphics.setColor(getPanelBackground());
          graphics.fill(headerRectangle);
          Color color = window.isActive()
                        ? JBColor.black
                        : JBColor.gray;
          graphics.setColor(color);
          int controlButtonsWidth = 70;
          String windowTitle = getWindowTitle(window);
          double widthToFit = controlButtonsWidth * 2 + GraphicsUtil.stringWidth(windowTitle, g.getFont()) - c.getWidth();
          if (widthToFit <= 0) {
            drawCenteredString(graphics, headerRectangle, windowTitle);
          } else {
            FontMetrics fm = graphics.getFontMetrics();
            Rectangle2D stringBounds = fm.getStringBounds(windowTitle, graphics);
            Rectangle bounds =
              AffineTransform.getTranslateInstance(controlButtonsWidth, fm.getAscent() + (headerRectangle.height - stringBounds.getHeight()) / 2).createTransformedShape(stringBounds).getBounds();
            drawCenteredString(graphics, bounds, windowTitle, false, true);
          }
        }
        finally {
          graphics.dispose();
        }
      }
    };
    rootPane.setBorder(customDecorationBorder);

    WindowAdapter windowAdapter = new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        rootPane.repaint();
      }

      @Override
      public void windowDeactivated(WindowEvent e) {
        rootPane.repaint();
      }
    };
    PropertyChangeListener propertyChangeListener = e -> rootPane.repaint();
    window.addPropertyChangeListener("title", propertyChangeListener);
    onDispose.accept(() -> {
      window.removeWindowListener(windowAdapter);
      window.removePropertyChangeListener("title", propertyChangeListener);
    });
  }

  private static String getWindowTitle(Window window) {
    return window instanceof JDialog ? ((JDialog)window).getTitle() : ((JFrame)window).getTitle() ;
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

  public static int getMultiClickInterval() {
    Object property = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
    if (property instanceof Integer) {
      return (Integer)property;
    }
    return 500;
  }

  private static final AtomicNotNullLazyValue<Boolean> X_RENDER_ACTIVE = new AtomicNotNullLazyValue<Boolean>() {
    @Override
    protected @NotNull Boolean compute() {
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

  public static @NotNull Cursor getTextCursor(@NotNull Color backgroundColor) {
    return SystemInfo.isMac && ColorUtil.isDark(backgroundColor) ?
           MacUIUtil.getInvertedTextCursor() : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
  }

  public static @Nullable Cursor cursorIfNotDefault(@Nullable Cursor cursorToSet) {
    return cursorToSet != null && cursorToSet.getType() != Cursor.DEFAULT_CURSOR ? cursorToSet : null;
  }

  public static @NotNull RGBImageFilter getGrayFilter() {
    return GrayFilter.namedFilter("grayFilter", new GrayFilter(33, -35, 100));
  }

  public static @NotNull RGBImageFilter getTextGrayFilter() {
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

    public @NotNull GrayFilterUIResource asUIResource() {
      return new GrayFilterUIResource(this);
    }

    public static class GrayFilterUIResource extends GrayFilter implements UIResource {
      public GrayFilterUIResource(@NotNull GrayFilter filter) {
        super(filter.origBrightness, filter.origContrast, filter.alpha);
      }
    }

    public static @NotNull GrayFilter namedFilter(@NotNull String resourceName, @NotNull GrayFilter defaultFilter) {
      return ObjectUtils.notNull((GrayFilter)UIManager.get(resourceName), defaultFilter);
    }
  }

  /** @deprecated use {@link JBUIScale} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static boolean isAppleRetina() {
    return false;
  }

  public static @NotNull Couple<Color> getCellColors(@NotNull JTable table, boolean isSel, int row, int column) {
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

  public enum FontSize {NORMAL, SMALL, MINI}

  public enum ComponentStyle {LARGE, REGULAR, SMALL, MINI}

  public enum FontColor {NORMAL, BRIGHTER}

  public static final char MNEMONIC = BundleBase.MNEMONIC;
  @NonNls public static final String HTML_MIME = "text/html";
  @NonNls public static final String JSLIDER_ISFILLED = "JSlider.isFilled";
  @NonNls public static final String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";
  /**
   * Prevent component DataContext from returning parent editor
   * Useful for components that are manually painted over the editor to prevent shortcuts from falling-through to editor
   *
   * Usage: {@code component.putClientProperty(HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, Boolean.TRUE)}
   */
  @NonNls public static final String HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY = "AuxEditorComponent";
  @NonNls public static final String CENTER_TOOLTIP_DEFAULT = "ToCenterTooltip";
  @NonNls public static final String CENTER_TOOLTIP_STRICT = "ToCenterTooltip.default";

  private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("<\\s*([^<>/ ]+)([^<>]*)/\\s*>", Pattern.CASE_INSENSITIVE);

  @NonNls private static final String FOCUS_PROXY_KEY = "isFocusProxy";

  public static final Key<Integer> KEEP_BORDER_SIDES = Key.create("keepBorderSides");
  private static final Key<UndoManager> UNDO_MANAGER = Key.create("undoManager");
  /**
   * Alt+click does copy text from tooltip or balloon to clipboard.
   * We collect this text from components recursively and this generic approach might 'grab' unexpected text fragments.
   * To provide more accurate text scope you should mark dedicated component with putClientProperty(TEXT_COPY_ROOT, Boolean.TRUE)
   * Note, main(root) components of BalloonImpl and AbstractPopup are already marked with this key
   */
  public static final Key<Boolean> TEXT_COPY_ROOT = Key.create("TEXT_COPY_ROOT");

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

  public static final Color CONTRAST_BORDER_COLOR = JBColor.namedColor("Borders.ContrastBorderColor", new JBColor(0xC9C9C9, 0x323232));

  public static final Color SIDE_PANEL_BACKGROUND = JBColor.namedColor("SidePanel.background", new JBColor(0xE6EBF0, 0x3E434C));

  public static final Color AQUA_SEPARATOR_BACKGROUND_COLOR = new JBColor(Gray._240, Gray.x51);
  public static final Color TRANSPARENT_COLOR = Gray.TRANSPARENT;

  public static final int DEFAULT_HGAP = 10;
  public static final int DEFAULT_VGAP = 4;
  public static final int LARGE_VGAP = 12;

  private static final int REGULAR_PANEL_TOP_BOTTOM_INSET = 8;
  private static final int REGULAR_PANEL_LEFT_RIGHT_INSET = 12;

  public static final Insets PANEL_REGULAR_INSETS = getRegularPanelInsets();

  public static final Insets PANEL_SMALL_INSETS = JBInsets.create(5, 8);

  @NonNls private static final String ROOT_PANE = "JRootPane.future";

  private static final Ref<Boolean> ourRetina = Ref.create(SystemInfo.isMac ? null : false);

  private UIUtil() {
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

    if (Registry.is("new.retina.detection", false)) {
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
    ComponentUtil.putClientProperty(component, key, value);
  }

  public static @NotNull String getHtmlBody(@NotNull String text) {
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

  public static @NotNull String getHtmlBody(@NotNull Html html) {
    String result = getHtmlBody(html.getText());
    return html.isKeepFont() ? result : result.replaceAll("<font(.*?)>", "").replaceAll("</font>", "");
  }

  public static void drawLinePickedOut(@NotNull Graphics graphics, int x, int y, int x1, int y1) {
    if (x == x1) {
      int minY = Math.min(y, y1);
      int maxY = Math.max(y, y1);
      LinePainter2D.paint((Graphics2D)graphics, x, minY + 1, x1, maxY - 1);
    }
    else if (y == y1) {
      int minX = Math.min(x, x1);
      int maxX = Math.max(x, x1);
      LinePainter2D.paint((Graphics2D)graphics, minX + 1, y, maxX - 1, y1);
    }
    else {
      LinePainter2D.paint((Graphics2D)graphics, x, y, x1, y1);
    }
  }

  public static boolean isReallyTypedEvent(@NotNull KeyEvent e) {
    char c = e.getKeyChar();
    if (c == KeyEvent.CHAR_UNDEFINED) return false; // ignore CHAR_UNDEFINED, like Swing text components do
    if (c < 0x20 || c == 0x7F) return false;

    // Allow input of special characters on Windows in Persian keyboard layout using Ctrl+Shift+1..4
    if (SystemInfo.isWindows && c >= 0x200C && c <= 0x200F) return true;

    if (SystemInfo.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }

    return !e.isAltDown() && !e.isControlDown();
  }

  public static int getStringY(final @NotNull String string, final @NotNull Rectangle bounds, final @NotNull Graphics2D g) {
    final int centerY = bounds.height / 2;
    final Font font = g.getFont();
    final FontRenderContext frc = g.getFontRenderContext();
    final Rectangle stringBounds = font.getStringBounds(string.isEmpty() ? " " : string, frc).getBounds();

    return (int)(centerY - stringBounds.height / 2.0 - stringBounds.y);
  }

  public static void drawLabelDottedRectangle(final @NotNull JLabel label, final @NotNull Graphics g) {
    drawLabelDottedRectangle(label, g, null);
  }

  public static void drawLabelDottedRectangle(final @NotNull JLabel label, final @NotNull Graphics g, @Nullable Rectangle bounds) {
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

  public static @NotNull Rectangle getLabelTextBounds(final @NotNull JLabel label) {
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

  public static String @NotNull [] splitText(@NotNull String text, @NotNull FontMetrics fontMetrics, int widthLimit, char separator) {
    List<String> lines = new ArrayList<>();
    StringBuilder currentLine = new StringBuilder();
    StringBuilder currentAtom = new StringBuilder();

    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      currentAtom.append(ch);

      if (ch == separator) {
        currentLine.append(currentAtom);
        currentAtom.setLength(0);
      }

      String s = currentLine.toString() + currentAtom;
      int width = fontMetrics.stringWidth(s);

      if (width >= widthLimit - fontMetrics.charWidth('w')) {
        if (currentLine.length() > 0) {
          lines.add(currentLine.toString());
          currentLine = new StringBuilder();
        }
        else {
          lines.add(currentAtom.toString());
          currentAtom.setLength(0);
        }
      }
    }

    String s = currentLine.toString() + currentAtom;
    if (!s.isEmpty()) {
      lines.add(s);
    }

    return ArrayUtilRt.toStringArray(lines);
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


  public static @NotNull Font getLabelFont(@NotNull FontSize size) {
    return getFont(size, null);
  }

  public static @NotNull Font getFont(@NotNull FontSize size, @Nullable Font base) {
    if (base == null) base = StartupUiUtil.getLabelFont();

    return base.deriveFont(getFontSize(size));
  }

  public static float getFontSize(@NotNull FontSize size) {
    int defSize = StartupUiUtil.getLabelFont().getSize();
    switch (size) {
      case SMALL:
        return Math.max(defSize - JBUIScale.scale(2f), JBUIScale.scale(11f));
      case MINI:
        return Math.max(defSize - JBUIScale.scale(4f), JBUIScale.scale(9f));
      default:
        return defSize;
    }
  }

  public static @NotNull Color getLabelFontColor(@NotNull FontColor fontColor) {
    Color defColor = getLabelForeground();
    if (fontColor == FontColor.BRIGHTER) {
      return new JBColor(new Color(Math.min(defColor.getRed() + 50, 255), Math.min(defColor.getGreen() + 50, 255), Math.min(
        defColor.getBlue() + 50, 255)), defColor.darker());
    }
    return defColor;
  }

  public static int getCheckBoxTextHorizontalOffset(@NotNull JCheckBox cb) {
    // logic copied from javax.swing.plaf.basic.BasicRadioButtonUI.paint
    ButtonUI ui = cb.getUI();
    String text = cb.getText();

    Icon buttonIcon = cb.getIcon();
    if (buttonIcon == null && ui != null) {
      if (ui instanceof BasicRadioButtonUI) {
        buttonIcon = ((BasicRadioButtonUI)ui).getDefaultIcon();
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

  public static Color getLabelBackground() {
    return UIManager.getColor("Label.background");
  }

  public static @NotNull Color getLabelForeground() {
    return JBColor.namedColor("Label.foreground", new JBColor(Gray._0, Gray.xBB));
  }

  public static Color getErrorForeground() {
    return JBColor.namedColor("Label.errorForeground", new JBColor(new Color(0xC7222D), JBColor.RED));
  }

  public static @NotNull Color getLabelDisabledForeground() {
    return JBColor.namedColor("Label.disabledForeground", JBColor.GRAY);
  }

  public static @NotNull Color getContextHelpForeground() {
    return JBColor.namedColor("Label.infoForeground", new JBColor(Gray.x78, Gray.x8C));
  }

  public static @NotNull String removeMnemonic(@NotNull String s) {
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

  /**
   * @deprecated use {@link #getTreeForeground()}
   */
  @Deprecated
  public static @NotNull Color getTreeTextForeground() {
    return getTreeForeground();
  }

  /**
   * @deprecated use {@link #getTreeBackground()}
   */
  @Deprecated
  public static @NotNull Color getTreeTextBackground() {
    return getTreeBackground();
  }

  public static Color getFieldForegroundColor() {
    return UIManager.getColor("field.foreground");
  }

  public static Color getActiveTextColor() {
    return UIManager.getColor("textActiveText");
  }

  public static @NotNull Color getInactiveTextColor() {
    return JBColor.namedColor("Component.infoForeground", new JBColor(Gray.x99, Gray.x78));
  }

  /**
   * @deprecated use {@link UIUtil#getTextFieldBackground()} instead
   */
  @Deprecated
  public static Color getActiveTextFieldBackgroundColor() {
    return getTextFieldBackground();
  }

  public static Color getInactiveTextFieldBackgroundColor() {
    return UIManager.getColor("TextField.inactiveBackground");
  }

  /**
   * @deprecated use {@link UIUtil#getInactiveTextColor()} instead
   */
  @Deprecated
  public static @NotNull Color getTextInactiveTextColor() {
    return getInactiveTextColor();
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

  public static @NotNull Color getToolTipBackground() {
    return JBColor.namedColor("ToolTip.background", new JBColor(Gray.xF2, new Color(0x3c3f41)));
  }

  public static @NotNull Color getToolTipActionBackground() {
    return JBColor.namedColor("ToolTip.Actions.background", new JBColor(Gray.xEB, new Color(0x43474a)));
  }

  public static @NotNull Color getToolTipForeground() {
    return JBColor.namedColor("ToolTip.foreground", new JBColor(Gray.x00, Gray.xBF));
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

  public static Object getTabbedPanePaintContentBorder(final @NotNull JComponent c) {
    return c.getClientProperty("TabbedPane.paintContentBorder");
  }

  public static Color getTableGridColor() {
    return UIManager.getColor("Table.gridColor");
  }

  public static @NotNull Color getPanelBackground() {
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

  public static void setSliderIsFilled(final @NotNull JSlider slider, final boolean value) {
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

  /**
   * @deprecated use {@link JBUI.CurrentTheme.CustomFrameDecorations#separatorForeground()}
   */
  @Deprecated
  public static @NotNull Color getSeparatorForeground() {
    return JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground();
  }

  public static Color getSeparatorShadow() {
    return UIManager.getColor("Separator.shadow");
  }

  @SuppressWarnings("MissingDeprecatedAnnotation")
  @Deprecated
  public static Color getSeparatorHighlight() {
    return UIManager.getColor("Separator.highlight");
  }

  /**
   * @deprecated use {@link JBUI.CurrentTheme.CustomFrameDecorations#separatorForeground()}
   */
  @Deprecated
  public static @NotNull Color getSeparatorColor() {
    return JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground();
  }

  public static Border getTableFocusCellHighlightBorder() {
    return UIManager.getBorder("Table.focusCellHighlightBorder");
  }

  /**
   * @deprecated unsupported UI feature
   */
  @Deprecated
  public static void setLineStyleAngled(@SuppressWarnings("unused") @NotNull JTree component) {
  }

  public static Color getTableFocusCellForeground() {
    return UIManager.getColor("Table.focusCellForeground");
  }

  public static Border getTextFieldBorder() {
    return UIManager.getBorder("TextField.border");
  }

  public static @NotNull Icon getErrorIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.errorIcon"), AllIcons.General.ErrorDialog);
  }

  public static @NotNull Icon getInformationIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.informationIcon"), AllIcons.General.InformationDialog);
  }

  public static @NotNull Icon getQuestionIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.questionIcon"), AllIcons.General.QuestionDialog);
  }

  public static @NotNull Icon getWarningIcon() {
    return ObjectUtils.notNull(UIManager.getIcon("OptionPane.warningIcon"), AllIcons.General.WarningDialog);
  }

  public static @NotNull Icon getBalloonInformationIcon() {
    return AllIcons.General.BalloonInformation;
  }

  public static @NotNull Icon getBalloonWarningIcon() {
    return AllIcons.General.BalloonWarning;
  }

  public static @NotNull Icon getBalloonErrorIcon() {
    return AllIcons.General.BalloonError;
  }

  @SuppressWarnings("MissingDeprecatedAnnotation")
  @Deprecated
  public static Icon getRadioButtonIcon() {
    return UIManager.getIcon("RadioButton.icon");
  }

  public static @NotNull Icon getTreeNodeIcon(boolean expanded, boolean selected, boolean focused) {
    boolean white = selected && focused || StartupUiUtil.isUnderDarcula();

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

  public static @NotNull Icon getTreeCollapsedIcon() {
    return UIManager.getIcon("Tree.collapsedIcon");
  }

  public static @NotNull Icon getTreeExpandedIcon() {
    return UIManager.getIcon("Tree.expandedIcon");
  }

  /**
   * @deprecated use {@link #getTreeExpandedIcon()} and {@link #getTreeCollapsedIcon()}
   */
  @Deprecated
  public static Icon getTreeIcon(boolean expanded) {
    return expanded ? getTreeExpandedIcon() : getTreeCollapsedIcon();
  }

  public static @NotNull Icon getTreeSelectedCollapsedIcon() {
    Icon icon = UIManager.getIcon("Tree.collapsedSelectedIcon");
    return icon != null ? icon : getTreeCollapsedIcon();
  }

  public static @NotNull Icon getTreeSelectedExpandedIcon() {
    Icon icon = UIManager.getIcon("Tree.expandedSelectedIcon");
    return icon != null ? icon : getTreeExpandedIcon();
  }

  @SuppressWarnings("MissingDeprecatedAnnotation")
  @Deprecated
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
   * @deprecated Aqua Look-n-Feel is not supported anymore
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static boolean isUnderAquaLookAndFeel() {
    return SystemInfo.isMac && UIManager.getLookAndFeel().getName().contains("Mac OS X");
  }

  /**
   * @deprecated Nimbus Look-n-Feel is deprecated and not supported anymore
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static boolean isUnderNimbusLookAndFeel() {
    return false;
  }

  public static boolean isUnderAquaBasedLookAndFeel() {
    return SystemInfo.isMac && (StartupUiUtil.isUnderDarcula() || isUnderIntelliJLaF());
  }

  public static boolean isUnderDefaultMacTheme() {
    LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
    if (SystemInfo.isMac && lookAndFeel instanceof UserDataHolder) {
      UserDataHolder dh = (UserDataHolder)lookAndFeel;

      return Boolean.TRUE != dh.getUserData(LAF_WITH_THEME_KEY) &&
             StringUtil.equals(dh.getUserData(PLUGGABLE_LAF_KEY), "macOS Light");
    }
    return false;
  }

  public static boolean isUnderWin10LookAndFeel() {
    LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
    if (SystemInfo.isWindows && lookAndFeel instanceof UserDataHolder) {
      UserDataHolder dh = (UserDataHolder)lookAndFeel;

      return Boolean.TRUE != dh.getUserData(LAF_WITH_THEME_KEY) &&
             StringUtil.equals(dh.getUserData(PLUGGABLE_LAF_KEY), "Windows 10 Light");
    }
    return false;
  }

  public static boolean isUnderDarcula() {
    return StartupUiUtil.isUnderDarcula();
  }

  public static boolean isUnderIntelliJLaF() {
    return UIManager.getLookAndFeel().getName().contains("IntelliJ") || isUnderDefaultMacTheme() || isUnderWin10LookAndFeel();
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
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

  public static @NotNull Font getToolbarFont() {
    return SystemInfo.isMac ? getLabelFont(UIUtil.FontSize.SMALL) : StartupUiUtil.getLabelFont();
  }

  public static @NotNull Color shade(@NotNull Color c, final double factor, final double alphaFactor) {
    assert factor >= 0 : factor;
    //noinspection UseJBColor
    return new Color(
      Math.min((int)Math.round(c.getRed() * factor), 255),
      Math.min((int)Math.round(c.getGreen() * factor), 255),
      Math.min((int)Math.round(c.getBlue() * factor), 255),
      Math.min((int)Math.round(c.getAlpha() * alphaFactor), 255)
    );
  }

  public static @NotNull Color mix(@NotNull Color c1, final Color c2, final double factor) {
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
    return StartupUiUtil.isUnderDarcula();
  }

  public static int getListCellHPadding() {
    return isUnderDefaultMacTheme() ? 8 :
           isUnderWin10LookAndFeel() ? 2 :
           7;
  }

  public static int getListCellVPadding() {
    return 1;
  }

  public static @NotNull JBInsets getRegularPanelInsets() {
    return JBInsets.create(REGULAR_PANEL_TOP_BOTTOM_INSET, REGULAR_PANEL_LEFT_RIGHT_INSET);
  }

  public static @NotNull Insets getListCellPadding() {
    return JBInsets.create(getListCellVPadding(), getListCellHPadding());
  }

  public static @NotNull Insets getListViewportPadding() {
    return isUnderNativeMacLookAndFeel() ? JBInsets.create(1, 0) : JBUI.emptyInsets();
  }

  public static boolean isToUseDottedCellBorder() {
    return !isUnderNativeMacLookAndFeel();
  }

  public static boolean isControlKeyDown(@NotNull MouseEvent mouseEvent) {
    return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  public static String @NotNull [] getValidFontNames(final boolean familyName) {
    Set<String> result = new TreeSet<>();

    // adds fonts that can display symbols at [A, Z] + [a, z] + [0, 9]
    for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
      try {
        if (FontUtil.isValidFont(font)) {
          result.add(familyName ? font.getFamily() : font.getName());
        }
      }
      catch (Exception ignore) {
        // JRE has problems working with the font. Just skip.
      }
    }

    // add label font (if isn't listed among above)
    Font labelFont = StartupUiUtil.getLabelFont();
    if (labelFont != null && FontUtil.isValidFont(labelFont)) {
      result.add(familyName ? labelFont.getFamily() : labelFont.getName());
    }

    return ArrayUtilRt.toStringArray(result);
  }

  public static String @NotNull [] getStandardFontSizes() {
    return STANDARD_FONT_SIZES;
  }

  public static void setupEnclosingDialogBounds(final @NotNull JComponent component) {
    component.revalidate();
    component.repaint();
    final Window window = SwingUtilities.windowForComponent(component);
    if (window != null &&
        (window.getSize().height < window.getMinimumSize().height || window.getSize().width < window.getMinimumSize().width)) {
      window.pack();
    }
  }

  public static @NotNull String displayPropertiesToCSS(Font font, Color fg) {
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

  public static void appendColor(final @NotNull Color color, @NotNull StringBuilder sb) {
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
      LinePainter2D.paint((Graphics2D)g, i1, y, i1, y);
    }

    for (i1 = y + (i1 != x1 + 1 ? 2 : 1); i1 <= y1; i1 += 2) {
      LinePainter2D.paint((Graphics2D)g, x1, i1, x1, i1);
    }

    for (i1 = x1 - (i1 != y1 + 1 ? 2 : 1); i1 >= x; i1 -= 2) {
      LinePainter2D.paint((Graphics2D)g, i1, y1, i1, y1);
    }

    for (i1 = y1 - (i1 != x - 1 ? 2 : 1); i1 >= y; i1 -= 2) {
      LinePainter2D.paint((Graphics2D)g, x, i1, x, i1);
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

    if (JreHiDpiUtil.isJreHiDPI(g)) {
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
      LinePainter2D.paint(g, startX - 1, 4, startX - 1, height - 4);
      LinePainter2D.paint(g, endX, 4, endX, height - 4);

      g.setColor(new Color(100, 100, 100, 50));
      LinePainter2D.paint(g, startX - 1, 4, startX - 1, height - 4);
      LinePainter2D.paint(g, endX, 4, endX, height - 4);

      LinePainter2D.paint(g, startX, 3, endX - 1, 3);
      LinePainter2D.paint(g, startX, height - 3, endX - 1, height - 3);
    }

    config.restore();
  }

  private static void drawBoringDottedLine(final @NotNull Graphics2D g,
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

      LinePainter2D.paint(g, startX, lineY, endX, lineY);
      LinePainter2D.paint(g, startX, lineY + 1, endX, lineY + 1);
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
      LinePainter2D.paint(g, dotXi, lineY, dotXi + 1, lineY);
      LinePainter2D.paint(g, dotXi, lineY + 1, dotXi + 1, lineY + 1);
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
      if (drawTopLine) LinePainter2D.paint((Graphics2D)g, x, 0, width, 0);
      if (drawBottomLine) LinePainter2D.paint((Graphics2D)g, x, height - 1, width, height - 1);

    }
    finally {
      config.restore();
    }
  }

  public static void drawDoubleSpaceDottedLine(final @NotNull Graphics2D g,
                                               final int start,
                                               final int end,
                                               final int xOrY,
                                               final Color fgColor,
                                               boolean horizontal) {

    g.setColor(fgColor);
    for (int dot = start; dot < end; dot += 3) {
      if (horizontal) {
        LinePainter2D.paint(g, dot, xOrY, dot, xOrY);
      }
      else {
        LinePainter2D.paint(g, xOrY, dot, xOrY, dot);
      }
    }
  }

  private static void drawAppleDottedLine(final @NotNull Graphics2D g,
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

      LinePainter2D.paint(g, startX, lineY, endX, lineY);
      LinePainter2D.paint(g, startX, lineY + 1, endX, lineY + 1);
      LinePainter2D.paint(g, startX, lineY + 2, endX, lineY + 2);
    }

    AppleBoldDottedPainter painter = AppleBoldDottedPainter.forColor(ObjectUtils.notNull(fgColor, oldColor));
    painter.paint(g, startX, endX, lineY);
  }

  @Deprecated
  public static void applyRenderingHints(@NotNull Graphics g) {
    GraphicsUtil.applyRenderingHints((Graphics2D)g);
  }

  /**
   * @deprecated Use {@link ImageUtil#createImage(int, int, int)}
   */
  @Deprecated
  public static @NotNull BufferedImage createImage(int width, int height, int type) {
    return ImageUtil.createImage(width, height, type);
  }

  /**
   * @deprecated Use {@link ImageUtil#createImage(GraphicsConfiguration, int, int, int)}
   */
  @Deprecated
  public static @NotNull BufferedImage createImage(@Nullable GraphicsConfiguration gc, int width, int height, int type) {
    return ImageUtil.createImage(gc, width, height, type);
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
  public static @NotNull BufferedImage createImage(GraphicsConfiguration gc, double width, double height, int type, @NotNull RoundingMode rm) {
    if (JreHiDpiUtil.isJreHiDPI(gc)) {
      return RetinaImage.create(gc, width, height, type, rm);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(rm.round(width), rm.round(height), type);
  }

  /**
   * @see #createImage(GraphicsConfiguration, double, double, int, RoundingMode)
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(ScaleContext ctx, double width, double height, int type, @NotNull RoundingMode rm) {
    if (StartupUiUtil.isJreHiDPI(ctx)) {
      return RetinaImage.create(ctx, width, height, type, rm);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(rm.round(width), rm.round(height), type);
  }

  /**
   * @deprecated Use {@link ImageUtil#createImage(Graphics, int, int, int)}
   */
  @Deprecated
  public static @NotNull BufferedImage createImage(Graphics g, int width, int height, int type) {
    return ImageUtil.createImage(g, width, height, type);
  }

  /**
   * @deprecated Use {@link ImageUtil#createImage(Graphics, double, double, int, RoundingMode)}
   */
  @Deprecated
  public static @NotNull BufferedImage createImage(Graphics g, double width, double height, int type, @NotNull RoundingMode rm) {
    return ImageUtil.createImage(g, width, height, type, rm);
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
  public static @NotNull BufferedImage createImage(Component comp, int width, int height, int type) {
    return comp != null ?
           ImageUtil.createImage(comp.getGraphicsConfiguration(), width, height, type) :
           ImageUtil.createImage(width, height, type);
  }

  /**
   * @deprecated use {@link #createImage(Graphics, int, int, int)}
   */
  @Deprecated
  public static @NotNull BufferedImage createImageForGraphics(Graphics2D g, int width, int height, int type) {
    return ImageUtil.createImage(g, width, height, type);
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
   * Dispatch all pending invocation events (if any) in the {@link com.intellij.ide.IdeEventQueue}, ignores and removes all other events from the queue.
   * In tests, consider using {@link com.intellij.testFramework.PlatformTestUtil#dispatchAllInvocationEventsInIdeEventQueue()}
   * @see #pump()
   */
  @TestOnly
  public static void dispatchAllInvocationEvents() {
    assert EdtInvocationManager.getInstance().isEventDispatchThread() : Thread.currentThread() + "; EDT: "+getEventQueueThread();
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    Method dispatchEventMethod =
      Objects.requireNonNull(ReflectionUtil.getDeclaredMethod(eventQueue.getClass(), "dispatchEvent", AWTEvent.class));
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
        ExceptionUtil.rethrow(e);
      }

      if (i % 10000 == 0) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Suspiciously many (" + i + ") AWT events, last dispatched " + event);
      }
    }
  }

  private static @NotNull Thread getEventQueueThread() {
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    try {
      Method method = ReflectionUtil.getDeclaredMethod(EventQueue.class, "getDispatchThread");
      //noinspection ConstantConditions
      return (Thread)method.invoke(eventQueue);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void addAwtListener(final @NotNull AWTEventListener listener, long mask, @NotNull Disposable parent) {
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask);
    Disposer.register(parent, () -> Toolkit.getDefaultToolkit().removeAWTEventListener(listener));
  }

  public static void addParentChangeListener(@NotNull Component component, @NotNull PropertyChangeListener listener) {
    component.addPropertyChangeListener("ancestor", listener);
  }

  public static void removeParentChangeListener(@NotNull Component component, @NotNull PropertyChangeListener listener) {
    component.removePropertyChangeListener("ancestor", listener);
  }

  public static void drawVDottedLine(@NotNull Graphics2D g, int lineX, int startY, int endY, final @Nullable Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      LinePainter2D.paint(g, lineX, startY, lineX, endY);
    }

    g.setColor(fgColor);
    for (int i = startY / 2 * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  public static void drawHDottedLine(@NotNull Graphics2D g, int startX, int endX, int lineY, final @Nullable Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      LinePainter2D.paint(g, startX, lineY, endX, lineY);
    }

    g.setColor(fgColor);

    for (int i = startX / 2 * 2; i < endX; i += 2) {
      g.drawRect(i, lineY, 0, 0);
    }
  }

  public static void drawDottedLine(@NotNull Graphics2D g, int x1, int y1, int x2, int y2, final @Nullable Color bgColor, final Color fgColor) {
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
    boolean isRetina = JreHiDpiUtil.isJreHiDPI((Graphics2D)g);
    float scale = 1 / JBUIScale.sysScale((Graphics2D)g);
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

  public static @NotNull Color getBgFillColor(@NotNull Component c) {
    final Component parent = findNearestOpaque(c);
    return parent == null ? c.getBackground() : parent.getBackground();
  }

  public static @Nullable Component findNearestOpaque(Component c) {
    return ComponentUtil.findParentByCondition(c, Component::isOpaque);
  }

  /**
   * @deprecated use {@link ComponentUtil#findParentByCondition(Component, java.util.function.Predicate)}
   */
  @Deprecated
  public static Component findParentByCondition(@Nullable Component c, @NotNull Condition<? super Component> condition) {
    return ComponentUtil.findParentByCondition(c, it -> condition.value(it));
  }

  //x and y should be from {0, 0} to {parent.getWidth(), parent.getHeight()}
  public static @Nullable Component getDeepestComponentAt(@NotNull Component parent, int x, int y) {
    Component component = SwingUtilities.getDeepestComponentAt(parent, x, y);
    if (component != null && component.getParent() instanceof JRootPane) { // GlassPane case
      JRootPane rootPane = (JRootPane)component.getParent();
      component = getDeepestComponentAtForComponent(parent, x, y, rootPane.getLayeredPane());
      if (component == null) {
        component = getDeepestComponentAtForComponent(parent, x, y, rootPane.getContentPane());
      }
    }
    if (component != null && component.getParent() instanceof JLayeredPane) { // Handle LoadingDecorator
      Component[] components = ((JLayeredPane)component.getParent()).getComponentsInLayer(JLayeredPane.DEFAULT_LAYER);
      if (components.length == 1 && ArrayUtil.indexOf(components, component) == -1) {
        component = getDeepestComponentAtForComponent(parent, x, y, components[0]);
      }
    }
    return component;
  }

  private static Component getDeepestComponentAtForComponent(@NotNull Component parent, int x, int y, @NotNull Component component) {
    Point point = SwingUtilities.convertPoint(parent, new Point(x, y), component);
    return SwingUtilities.getDeepestComponentAt(component, point.x, point.y);
  }

  public static void layoutRecursively(@NotNull Component component) {
    if (!(component instanceof JComponent)) {
      return;
    }
    forEachComponentInHierarchy(component, Component::doLayout);
  }

  @Language("HTML")
  public static @NotNull String getCssFontDeclaration(@NotNull Font font) {
    return getCssFontDeclaration(font, getLabelForeground(), JBUI.CurrentTheme.Link.linkColor(), null);
  }

  @Language("HTML")
  public static @NotNull String getCssFontDeclaration(@NotNull Font font, @Nullable Color fgColor, @Nullable Color linkColor, @Nullable String liImg) {
    @Language("HTML")
    String familyAndSize = "font-family:'" + font.getFamily() + "'; font-size:" + font.getSize() + "pt;";
    return "<style>\n"
    +"body, div, td, p {" + familyAndSize
    + (fgColor != null ? " color:#" + ColorUtil.toHex(fgColor)+';' : "")
    +"}\n"
    +"a {" + familyAndSize
    + (linkColor != null ? " color:#"+ColorUtil.toHex(linkColor)+';' : "")
    +"}\n"
    +"code {font-size:"+font.getSize()+"pt;}\n"
    +"ul {list-style:disc; margin-left:15px;}\n"
    +"</style>";
  }

  public static @NotNull Color getFocusedFillColor() {
    return toAlpha(getListSelectionBackground(true), 100);
  }

  public static @NotNull Color getFocusedBoundsColor() {
    return getBoundsColor();
  }

  public static @NotNull Color getBoundsColor() {
    return JBColor.border();
  }

  public static @NotNull Color getBoundsColor(boolean focused) {
    return focused ? getFocusedBoundsColor() : getBoundsColor();
  }

  public static @NotNull Color toAlpha(final Color color, final int alpha) {
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

  /**
   * @deprecated use {@link com.intellij.openapi.wm.IdeFocusManager}
   */
  @Deprecated
  public static void requestFocus(final @NotNull JComponent c) {
    if (c.isShowing()) {
      c.requestFocus();
    }
    else {
      SwingUtilities.invokeLater(c::requestFocus);
    }
  }

  //Whitelist for component types that provide obvious 'focused' view
  public static boolean canDisplayFocusedState(@NotNull Component component) {
    return component instanceof JTextComponent || component instanceof AbstractButton || component instanceof JComboBox;
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

  public static void disposeProgress(final @NotNull JProgressBar progress) {
    if (!isUnderNativeMacLookAndFeel()) return;

    SwingUtilities.invokeLater(() -> progress.setUI(null));
  }

  public static @Nullable Component findUltimateParent(@Nullable Component c) {
    return c == null ? null : ComponentUtil.findUltimateParent(c);
  }

  public static @NotNull Color getHeaderActiveColor() {
    return ACTIVE_HEADER_COLOR;
  }

  public static @NotNull Color getFocusedBorderColor() {
    return JBUI.CurrentTheme.Focus.focusColor();
  }

  public static @NotNull Color getHeaderInactiveColor() {
    return INACTIVE_HEADER_COLOR;
  }

  public static @NotNull Font getTitledBorderFont() {
    return StartupUiUtil.getLabelFont();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  @Deprecated
  public static @NotNull Color getBorderInactiveColor() {
    return JBColor.border();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  @Deprecated
  public static @NotNull Color getBorderActiveColor() {
    return JBColor.border();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  @Deprecated
  public static @NotNull Color getBorderSeparatorColor() {
    return JBColor.border();
  }

  public static @Nullable StyleSheet loadStyleSheet(@Nullable URL url) {
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

  public static @NotNull HTMLEditorKit getHTMLEditorKit() {
    return getHTMLEditorKit(true);
  }

  public static @NotNull HTMLEditorKit getHTMLEditorKit(boolean noGapsBetweenParagraphs) {
    return new JBHtmlEditorKit(noGapsBetweenParagraphs);
  }

  public static final class JBWordWrapHtmlEditorKit extends JBHtmlEditorKit {
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

  public static @NotNull FontUIResource getFontWithFallback(@NotNull Font font) {
    return getFontWithFallback(font.getFamily(), font.getStyle(), font.getSize());
  }

  public static @NotNull FontUIResource getFontWithFallback(@Nullable String familyName, @JdkConstants.FontStyle int style, int size) {
    // On macOS font fallback is implemented in JDK by default
    // (except for explicitly registered fonts, e.g. the fonts we bundle with IDE, for them we don't have a solution now)
    Font fontWithFallback = SystemInfo.isMac ? new Font(familyName, style, size) : new StyleContext().getFont(familyName, style, size);
    return fontWithFallback instanceof FontUIResource ? (FontUIResource)fontWithFallback : new FontUIResource(fontWithFallback);
  }

  //Escape error-prone HTML data (if any) when we use it in renderers, see IDEA-170768
  public static <T> T htmlInjectionGuard(T toRender) {
    if (toRender instanceof String && StringUtil.toLowerCase((String)toRender).startsWith("<html>")) {
      //noinspection unchecked
      return (T) ("<html>" + StringUtil.escapeXmlEntities((String)toRender));
    }
    return toRender;
  }

  /**
   * @deprecated This method is a hack. Please avoid it and create borderless {@code JScrollPane} manually using
   * {@link com.intellij.ui.ScrollPaneFactory#createScrollPane(Component, boolean)}.
   */
  @Deprecated
  public static void removeScrollBorder(final Component c) {
    JBIterable<JScrollPane> scrollPanes = uiTraverser(c)
      .expand(o -> o == c || o instanceof JPanel || o instanceof JLayeredPane)
      .filter(JScrollPane.class);
    for (JScrollPane scrollPane : scrollPanes) {
      Integer keepBorderSides = ComponentUtil.getClientProperty(scrollPane, KEEP_BORDER_SIDES);
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

  public static @NotNull String toHtml(@NotNull String html) {
    return toHtml(html, 0);
  }

  @NonNls
  public static @NotNull String toHtml(@NotNull String html, final int hPadding) {
    html = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
    Font font = StartupUiUtil.getLabelFont();
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : JBUIScale.scale(11);
    return "<html><style>body { font-family: "
           + family + "; font-size: "
           + size + ";} ul li {list-style-type:circle;}</style>"
           + addPadding(html, hPadding) + "</html>";
  }

  public static @NotNull String addPadding(@NotNull String html, int hPadding) {
    return String.format("<p style=\"margin: 0 %dpx 0 %dpx;\">%s</p>", hPadding, hPadding, html);
  }

  public static @NotNull String convertSpace2Nbsp(@NotNull String html) {
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
    EdtInvocationManager edtInvocationManager = EdtInvocationManager.getInstance();
    if (edtInvocationManager.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      edtInvocationManager.invokeLater(runnable);
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
    EdtInvocationManager.getInstance().invokeAndWaitIfNeeded(runnable);
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
  public static <T> T invokeAndWaitIfNeeded(final @NotNull Computable<T> computable) {
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
   */
  public static void invokeAndWaitIfNeeded(final @NotNull ThrowableRunnable<?> runnable) throws Throwable {
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
  public static void changeBackGround(final @NotNull Component component, final Color background) {
    final Color oldBackGround = component.getBackground();
    if (background == null || !background.equals(oldBackGround)) {
      component.setBackground(background);
    }
  }

  public static @Nullable ComboPopup getComboBoxPopup(@NotNull JComboBox<?> comboBox) {
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
    editor.reshape(x, y, width, height);
  }

  /**
   * @deprecated the method was used to fix Aqua Look-n-Feel problems. Now it does not make sense
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static int fixComboBoxHeight(final int height) {
    return height;
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
      child = child instanceof JPopupMenu ? ((JPopupMenu)child).getInvoker()
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
  @Contract(pure = true)
  public static @Nullable <T> T getParentOfType(@NotNull Class<? extends T> type, Component component) {
    return ComponentUtil.getParentOfType(type, component);
  }

  public static @NotNull JBIterable<Component> uiParents(@Nullable Component c, boolean strict) {
    return strict ? JBIterable.generate(c, c1 -> c1.getParent()).skip(1) : JBIterable.generate(c, c1 -> c1.getParent());
  }

  public static @NotNull JBIterable<Component> uiChildren(@Nullable Component component) {
    if (!(component instanceof Container)) return JBIterable.empty();
    Container container = (Container)component;
    return JBIterable.of(container.getComponents());
  }

  public static @NotNull JBTreeTraverser<Component> uiTraverser(@Nullable Component component) {
    return UI_TRAVERSER.withRoot(component).expandAndFilter(o -> !(o instanceof CellRendererPane));
  }

  public static final Key<Iterable<? extends Component>> NOT_IN_HIERARCHY_COMPONENTS = Key.create("NOT_IN_HIERARCHY_COMPONENTS");

  private static final JBTreeTraverser<Component> UI_TRAVERSER = JBTreeTraverser.from((Function<Component, JBIterable<Component>>)c -> {
    JBIterable<Component> result;
    if (c instanceof JMenu) {
      result = JBIterable.of(((JMenu)c).getMenuComponents());
    }
    else {
      result = uiChildren(c);
    }
    if (c instanceof JComponent) {
      JComponent jc = (JComponent)c;
      Iterable<? extends Component> orphans = ComponentUtil.getClientProperty(jc, NOT_IN_HIERARCHY_COMPONENTS);
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

  public static void scrollListToVisibleIfNeeded(final @NotNull JList<?> list) {
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

  public static @Nullable <T extends JComponent> T findComponentOfType(JComponent parent, Class<T> cls) {
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

  public static @NotNull <T extends JComponent> List<T> findComponentsOfType(JComponent parent, @NotNull Class<? extends T> cls) {
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
    private final List<String> myLines = new ArrayList<>();
    private boolean myDrawShadow;
    private Color myShadowColor;
    private float myLineSpacing;
    private Font myFont;
    private Color myColor;

    public TextPainter() {
      myDrawShadow = StartupUiUtil.isUnderDarcula();
      myShadowColor = StartupUiUtil.isUnderDarcula() ? Gray._0.withAlpha(100) : Gray._220;
      myLineSpacing = 1.0f;
    }

    public @NotNull TextPainter withShadow(boolean drawShadow, Color shadowColor) {
      myDrawShadow = drawShadow;
      myShadowColor = shadowColor;
      return this;
    }

    public @NotNull TextPainter withLineSpacing(float lineSpacing) {
      myLineSpacing = lineSpacing;
      return this;
    }

    public @NotNull TextPainter withColor(Color color) {
      myColor = color;
      return this;
    }

    public @NotNull TextPainter withFont(Font font) {
      myFont = font;
      return this;
    }

    public @NotNull TextPainter appendLine(String text) {
      if (text == null || text.isEmpty()) return this;
      myLines.add(text);
      return this;
    }

    /**
     * _position(block width, block height) => (x, y) of the block
     */
    public void draw(final @NotNull Graphics g, @NotNull PairFunction<? super Integer, ? super Integer, ? extends Couple<Integer>> _position) {
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
        ContainerUtil.process(myLines, text -> {
          FontMetrics fm = g.getFontMetrics();
          maxWidth[0] = Math.max(fm.stringWidth(text.replace("<shortcut>", "").replace("</shortcut>", "")), maxWidth[0]);
          height[0] += (fm.getHeight() + fm.getLeading()) * myLineSpacing;
          return true;
        });

        final Couple<Integer> position = _position.fun(maxWidth[0] + 20, height[0]);
        assert position != null;

        final int[] yOffset = {position.getSecond()};
        ContainerUtil.process(myLines, text -> {
          String shortcut = "";
          if (text.contains("<shortcut>")) {
            shortcut = text.substring(text.indexOf("<shortcut>") + "<shortcut>".length(), text.indexOf("</shortcut>"));
            text = text.substring(0, text.indexOf("<shortcut>"));
          }

          int x = position.getFirst() + 10;

          FontMetrics fm = g.getFontMetrics();

          if (myDrawShadow) {
            int xOff = StartupUiUtil.isUnderDarcula() ? 1 : 0;
            Color oldColor1 = g.getColor();
            g.setColor(myShadowColor);

            int yOff = 1;

            g.drawString(text, x + xOff, yOffset[0] + yOff);
            g.setColor(oldColor1);
          }

          g.drawString(text, x, yOffset[0]);
          if (!StringUtil.isEmpty(shortcut)) {
            Color oldColor1 = g.getColor();
            g.setColor(JBColor.namedColor("Editor.shortcutForeground", new JBColor(new Color(82, 99, 155), new Color(88, 157, 246))));
            g.drawString(shortcut, x + fm.stringWidth(text + (StartupUiUtil.isUnderDarcula() ? " " : "")), yOffset[0]);
            g.setColor(oldColor1);
          }

          yOffset[0] += (fm.getHeight() + fm.getLeading()) * myLineSpacing;

          return true;
        });
      }
      finally {
        if (oldFont != null) g.setFont(oldFont);
        if (oldColor != null) g.setColor(oldColor);
      }
    }
  }

  public static @Nullable JRootPane getRootPane(Component c) {
    JRootPane root = ComponentUtil.getParentOfType((Class<? extends JRootPane>)JRootPane.class, c);
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

  /**
   * @deprecated Use {@link TimerUtil#createNamedTimer(String, int, ActionListener)}
   */
  @Deprecated
  public static @NotNull Timer createNamedTimer(@NonNls @NotNull String name, int delay, @NotNull ActionListener listener) {
    return TimerUtil.createNamedTimer(name, delay, listener);
  }

  /**
   * @deprecated Use {@link TimerUtil#createNamedTimer(String, int)}
   */
  @Deprecated
  public static @NotNull Timer createNamedTimer(@NonNls @NotNull String name, int delay) {
    return TimerUtil.createNamedTimer(name, delay);
  }

  public static boolean isDialogRootPane(JRootPane rootPane) {
    if (rootPane != null) {
      final Object isDialog = rootPane.getClientProperty("DIALOG_ROOT_PANE");
      return isDialog instanceof Boolean && ((Boolean)isDialog).booleanValue();
    }
    return false;
  }

  public static @Nullable JComponent mergeComponentsWithAnchor(PanelWithAnchor @NotNull ... panels) {
    return mergeComponentsWithAnchor(Arrays.asList(panels));
  }

  public static @Nullable JComponent mergeComponentsWithAnchor(@NotNull Collection<? extends PanelWithAnchor> panels) {
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
    if (!(component instanceof JComponent)) {
      return;
    }
    forEachComponentInHierarchy(component, c -> {
      if (c instanceof JComponent) {
        ((JComponent)c).setOpaque(false);
      }
    });
  }

  public static void setBackgroundRecursively(@NotNull Component component, @NotNull Color bg) {
    forEachComponentInHierarchy(component, c -> c.setBackground(bg));
  }

  private static void forEachComponentInHierarchy(@NotNull Component component, @NotNull Consumer<? super Component> action) {
    action.consume(component);
    if (component instanceof Container) {
      for (Component c : ((Container)component).getComponents()) {
        forEachComponentInHierarchy(c, action);
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

  public static int getLcdContrastValue() {
    int lcdContrastValue  = Registry.intValue("lcd.contrast.value", 0);
    if (lcdContrastValue == 0) {
      return StartupUiUtil.doGetLcdContrastValueForSplash(StartupUiUtil.isUnderDarcula());
    }
    else {
      return StartupUiUtil.normalizeLcdContrastValue(lcdContrastValue);
    }
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

  public static @NotNull Color getDecoratedRowColor() {
    return JBColor.namedColor("Table.stripeColor", DECORATED_ROW_BG_COLOR);
  }

  public static @NotNull Paint getGradientPaint(float x1, float y1, @NotNull Color c1, float x2, float y2, @NotNull Color c2) {
    return Registry.is("ui.no.bangs.and.whistles", false) ? ColorUtil.mix(c1, c2, .5) : new GradientPaint(x1, y1, c1, x2, y2, c2);
  }

  public static @Nullable Point getLocationOnScreen(@NotNull JComponent component) {
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

  public static void setAutoRequestFocus(@NotNull Window window, boolean value) {
    if (!SystemInfo.isMac) {
      window.setAutoRequestFocus(value);
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

  public static void runWhenWindowClosed(@NotNull Window window, @NotNull Runnable runnable) {
    window.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        e.getWindow().removeWindowListener(this);
        runnable.run();
      }
    });
  }

  //May have no usages but it's useful in runtime (Debugger "watches", some logging etc.)
  public static @NotNull String getDebugText(@NotNull Component c) {
    StringBuilder builder  = new StringBuilder();
    getAllTextsRecursively(c, builder);
    return builder.toString();
  }

  private static void getAllTextsRecursively(@NotNull Component component, @NotNull StringBuilder builder) {
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
        getAllTextsRecursively(child, builder);
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
    UndoManager undoManager = ComponentUtil.getClientProperty(textComponent, UNDO_MANAGER);
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

  public static void addUndoRedoActions(final @NotNull JTextComponent textComponent) {
    if (textComponent.getClientProperty(UNDO_MANAGER) instanceof UndoManager) {
      return;
    }
    UndoManager undoManager = new UndoManager();
    textComponent.putClientProperty(UNDO_MANAGER, undoManager);
    textComponent.getDocument().addUndoableEditListener(undoManager);
    textComponent.getDocument().addDocumentListener(SET_TEXT_CHECKER);
    textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK), "undoKeystroke");
    textComponent.getActionMap().put("undoKeystroke", UNDO_ACTION);
    textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, (SystemInfo.isMac
                                                                           ? InputEvent.META_MASK : InputEvent.CTRL_MASK) | InputEvent.SHIFT_MASK), "redoKeystroke");
    textComponent.getActionMap().put("redoKeystroke", REDO_ACTION);
  }

  public static @Nullable UndoManager getUndoManager(Component component) {
    if (component instanceof JTextComponent) {
      Object o = ((JTextComponent)component).getClientProperty(UNDO_MANAGER);
      if (o instanceof UndoManager) return (UndoManager)o;
    }
    return null;
  }

  public static void playSoundFromResource(@NotNull String resourceName) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) {
      return;
    }
    playSoundFromStream(() -> callerClass.getResourceAsStream(resourceName));
  }

  public static void playSoundFromStream(final @NotNull Factory<? extends InputStream> streamProducer) {
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

  public static @NotNull String leftArrow() {
    return FontUtil.leftArrow(StartupUiUtil.getLabelFont());
  }

  public static @NotNull String rightArrow() {
    return FontUtil.rightArrow(StartupUiUtil.getLabelFont());
  }

  public static @NotNull String upArrow(@NotNull String defaultValue) {
    return FontUtil.upArrow(StartupUiUtil.getLabelFont(), defaultValue);
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
  public static @Nullable Window getWindow(@Nullable Component component) {
    return ComponentUtil.getWindow(component);
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
  public static boolean hasComponentOfType(@NotNull Component component, Class<?> @NotNull ... types) {
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
  public static @NotNull ComponentStyle getComponentStyle(Component component) {
    if (component instanceof JComponent) {
      Object property = ((JComponent)component).getClientProperty("JComponent.sizeVariant");
      if ("large".equals(property)) return ComponentStyle.LARGE;
      if ("small".equals(property)) return ComponentStyle.SMALL;
      if ("mini".equals(property)) return ComponentStyle.MINI;
    }
    return ComponentStyle.REGULAR;
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

  public static boolean isRetina(@NotNull GraphicsDevice device) {
    return DetectRetinaKit.isOracleMacRetinaDevice(device);
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


  private static final Color BACKGROUND = new JBColor(0xFFFFFF, 0x3C3F41);
  private static final Color LIST_BACKGROUND = JBColor.namedColor("List.background", BACKGROUND);
  private static final Color TREE_BACKGROUND = JBColor.namedColor("Tree.background", BACKGROUND);
  private static final Color TABLE_BACKGROUND = JBColor.namedColor("Table.background", BACKGROUND);

  private static final class FocusedSelection {
    private static final Color BACKGROUND = new JBColor(0x3875D6, 0x2F65CA);
    private static final Color TREE_BACKGROUND = JBColor.namedColor("Tree.selectionBackground", BACKGROUND);
    private static final Color TABLE_BACKGROUND = JBColor.namedColor("Table.selectionBackground", BACKGROUND);
  }

  private static final class UnfocusedSelection {
    private static final Color BACKGROUND = new JBColor(0xD4D4D4, 0x0D293E);
    private static final Color LIST_BACKGROUND = JBColor.namedColor("List.selectionInactiveBackground", BACKGROUND);
    private static final Color TREE_BACKGROUND = JBColor.namedColor("Tree.selectionInactiveBackground", BACKGROUND);
    private static final Color TABLE_BACKGROUND = JBColor.namedColor("Table.selectionInactiveBackground", BACKGROUND);
  }


  // List

  public static @NotNull Font getListFont() {
    Font font = UIManager.getFont("List.font");
    return font != null ? font : StartupUiUtil.getLabelFont();
  }

  // background

  public static @NotNull Color getListBackground() {
    return LIST_BACKGROUND;
  }

  private static final JBValue SELECTED_ITEM_ALPHA = new JBValue.UIInteger("List.selectedItemAlpha", 75);

  public static @NotNull Color getListSelectionBackground(boolean focused) {
    if (!focused) return UnfocusedSelection.LIST_BACKGROUND;
    Color color = UIManager.getColor("List.selectionBackground");
    double alpha = SELECTED_ITEM_ALPHA.getFloat() / 100.0;
    //noinspection UseJBColor
    return isUnderDefaultMacTheme() && alpha >= 0 && alpha <= 1.0 ? ColorUtil.mix(Color.WHITE, color, alpha) : color;
  }

  public static @NotNull Dimension updateListRowHeight(@NotNull Dimension size) {
    size.height = Math.max(size.height, UIManager.getInt("List.rowHeight"));
    return size;
  }

  public static @NotNull Color getListBackground(boolean selected, boolean focused) {
    return !selected ? getListBackground() : getListSelectionBackground(focused);
  }

  /**
   * @deprecated use {@link #getListBackground(boolean, boolean)}
   */
  @Deprecated
  public static @NotNull Color getListBackground(boolean selected) {
    return getListBackground(selected, true);
  }

  /**
   * @deprecated use {@link #getListSelectionBackground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getListSelectionBackground() {
    return getListSelectionBackground(true);
  }

  /**
   * @deprecated use {@link #getListSelectionBackground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getListUnfocusedSelectionBackground() {
    return getListSelectionBackground(false);
  }

  // foreground

  public static @NotNull Color getListForeground() {
    return UIManager.getColor("List.foreground");
  }

  public static @NotNull Color getListSelectionForeground(boolean focused) {
    Color foreground = UIManager.getColor(focused ? "List.selectionForeground" : "List.selectionInactiveForeground");
    if (focused && foreground == null) foreground = UIManager.getColor("List[Selected].textForeground");  // Nimbus
    return foreground != null ? foreground : getListForeground();
  }

  public static @NotNull Color getListForeground(boolean selected, boolean focused) {
    return !selected ? getListForeground() : getListSelectionForeground(focused);
  }

  /**
   * @deprecated use {@link #getListForeground(boolean, boolean)}
   */
  @Deprecated
  public static @NotNull Color getListForeground(boolean selected) {
    return getListForeground(selected, true);
  }

  /**
   * @deprecated use {@link #getListSelectionForeground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getListSelectionForeground() {
    return getListSelectionForeground(true);
  }


  // Tree

  public static @NotNull Font getTreeFont() {
    Font font = UIManager.getFont("Tree.font");
    return font != null ? font : StartupUiUtil.getLabelFont();
  }

  // background

  public static @NotNull Color getTreeBackground() {
    return TREE_BACKGROUND;
  }

  public static @NotNull Color getTreeSelectionBackground(boolean focused) {
    return focused ? FocusedSelection.TREE_BACKGROUND : UnfocusedSelection.TREE_BACKGROUND;
  }

  public static @NotNull Color getTreeBackground(boolean selected, boolean focused) {
    return !selected ? getTreeBackground() : getTreeSelectionBackground(focused);
  }

  /**
   * @deprecated use {@link #getTreeSelectionBackground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getTreeSelectionBackground() {
    return getTreeSelectionBackground(true);
  }

  /**
   * @deprecated use {@link #getTreeSelectionBackground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getTreeUnfocusedSelectionBackground() {
    return getTreeSelectionBackground(false);
  }

  // foreground

  public static @NotNull Color getTreeForeground() {
    return UIManager.getColor("Tree.foreground");
  }

  public static @NotNull Color getTreeSelectionForeground(boolean focused) {
    Color foreground = UIManager.getColor(focused ? "Tree.selectionForeground" : "Tree.selectionInactiveForeground");
    return foreground != null ? foreground : getTreeForeground();
  }

  public static @NotNull Color getTreeForeground(boolean selected, boolean focused) {
    return !selected ? getTreeForeground() : getTreeSelectionForeground(focused);
  }

  /**
   * @deprecated use {@link #getTreeSelectionForeground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getTreeSelectionForeground() {
    return getTreeSelectionForeground(true);
  }

  public static @NotNull Color getTableBackground() {
    return TABLE_BACKGROUND;
  }

  public static @NotNull Color getTableSelectionBackground(boolean focused) {
    return focused ? FocusedSelection.TABLE_BACKGROUND : UnfocusedSelection.TABLE_BACKGROUND;
  }

  public static @NotNull Color getTableBackground(boolean selected, boolean focused) {
    return !selected ? getTableBackground() : getTableSelectionBackground(focused);
  }

  /**
   * @deprecated use {@link #getTableBackground(boolean, boolean)}
   */
  @Deprecated
  public static @NotNull Color getTableBackground(boolean selected) {
    return getTableBackground(selected, true);
  }

  /**
   * @deprecated use {@link #getTableSelectionBackground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getTableSelectionBackground() {
    return getTableSelectionBackground(true);
  }

  /**
   * @deprecated use {@link #getTableSelectionBackground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getTableUnfocusedSelectionBackground() {
    return getTableSelectionBackground(false);
  }

  // foreground

  public static @NotNull Color getTableForeground() {
    return UIManager.getColor("Table.foreground");
  }

  public static @NotNull Color getTableSelectionForeground(boolean focused) {
    Color foreground = UIManager.getColor(focused ? "Table.selectionForeground" : "Table.selectionInactiveForeground");
    return foreground != null ? foreground : getTreeForeground();
  }

  public static @NotNull Color getTableForeground(boolean selected, boolean focused) {
    return !selected ? getTableForeground() : getTableSelectionForeground(focused);
  }

  /**
   * @deprecated use {@link #getTableForeground(boolean, boolean)}
   */
  @Deprecated
  public static @NotNull Color getTableForeground(boolean selected) {
    return getTableForeground(selected, true);
  }

  /**
   * @deprecated use {@link #getTableSelectionForeground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getTableSelectionForeground() {
    return UIManager.getColor("Table.selectionForeground");
  }

  /**
   * @deprecated use {@link JBUIScale#getSystemFontData()}
   */
  @Deprecated
  public static Pair<String, Integer> getSystemFontData() {
    return JBUIScale.getSystemFontData();
  }

  /**
   * @deprecated use {@link JreHiDpiUtil#isJreHiDPIEnabled()}
   */
  @Deprecated
  public static boolean isJreHiDPIEnabled() {
    return JreHiDpiUtil.isJreHiDPIEnabled();
  }

  /**
   * @deprecated use {@link JreHiDpiUtil#isJreHiDPI(Graphics2D)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isJreHiDPI(@Nullable Graphics2D g) {
    return JreHiDpiUtil.isJreHiDPI(g);
  }

  /**
   * @deprecated use {@link UIUtil#getPanelBackground()} instead
   */
  @SuppressWarnings("SpellCheckingInspection")
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull Color getPanelBackgound() {
    return getPanelBackground();
  }

  public static void doNotScrollToCaret(@NotNull JTextComponent textComponent) {
    textComponent.setCaret(new DefaultCaret() {
      @Override
      protected void adjustVisibility(Rectangle nloc) {}
    });
  }

  /**
   * By default soft wrapping in text components (for ASCII text) is only performed at spaces. This enables wrapping also at other places,
   * e.g. at dots.
   * <p>
   * NOTE: any operation which replaces document in the text component (e.g. {@link JTextComponent#setDocument(Document)},
   * {@link JEditorPane#setPage(URL)}, {@link JEditorPane#setEditorKit(EditorKit)}) will cancel the effect of this call.
   */
  public static void enableEagerSoftWrapping(@NotNull JTextComponent textComponent) {
    // see javax.swing.text.GlyphView.getBreaker()
    textComponent.getDocument().putProperty("multiByte", Boolean.TRUE);
  }

  public static @NotNull Color getTooltipSeparatorColor() {
    return JBColor.namedColor("Tooltip.separatorColor", 0xd1d1d1, 0x545658);
  }

  /**
   * This method (as opposed to {@link JEditorPane#scrollToReference}) supports also targets using {@code id} HTML attribute.
   */
  public static void scrollToReference(@NotNull JEditorPane editor, @NotNull String reference) {
    Document document = editor.getDocument();
    if (document instanceof HTMLDocument) {
      Element elementById = ((HTMLDocument) document).getElement(reference);
      if (elementById != null) {
        try {
          int pos = elementById.getStartOffset();
          Rectangle r = editor.modelToView(pos);
          if (r != null) {
            r.height = editor.getVisibleRect().height;
            editor.scrollRectToVisible(r);
            editor.setCaretPosition(pos);
          }
        } catch (BadLocationException e) {
          getLogger().error(e);
        }
        return;
      }
    }
    editor.scrollToReference(reference);
  }

  public static void runWhenFocused(@NotNull Component component, @NotNull Runnable runnable) {
    assert component.isShowing();
    if (component.isFocusOwner()) {
      runnable.run();
    }
    else {
      Disposable disposable = Disposer.newDisposable();
      FocusListener focusListener = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          Disposer.dispose(disposable);
          runnable.run();
        }
      };
      HierarchyListener hierarchyListener = e -> {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !component.isShowing()) {
          Disposer.dispose(disposable);
        }
      };
      component.addFocusListener(focusListener);
      component.addHierarchyListener(hierarchyListener);
      Disposer.register(disposable, () -> {
        component.removeFocusListener(focusListener);
        component.removeHierarchyListener(hierarchyListener);
      });
    }
  }

  public static Font getLabelFont() {
    return StartupUiUtil.getLabelFont();
  }

  public static void drawImage(@NotNull Graphics g, @NotNull Image image, int x, int y, @Nullable ImageObserver observer) {
    StartupUiUtil.drawImage(g, image, x, y, null);
  }

  public static @NotNull Point getCenterPoint(@NotNull Dimension container, @NotNull Dimension child) {
    return StartupUiUtil.getCenterPoint(container, child);
  }

  public static @NotNull Point getCenterPoint(@NotNull Rectangle container, @NotNull Dimension child) {
    return StartupUiUtil.getCenterPoint(container, child);
  }

  public static void drawImage(@NotNull Graphics g,
                               @NotNull Image image,
                               @Nullable Rectangle dstBounds,
                               @Nullable Rectangle srcBounds,
                               @Nullable ImageObserver observer) {
    StartupUiUtil.drawImage(g, image, dstBounds, srcBounds, null, observer);
  }

  public static void drawImage(@NotNull Graphics g, @NotNull BufferedImage image, @Nullable BufferedImageOp op, int x, int y) {
    StartupUiUtil.drawImage(g, image, x, y, -1, -1, op, null);
  }

  /** @see UIUtil#dispatchAllInvocationEvents() */
  @TestOnly
  public static void pump() {
    assert !SwingUtilities.isEventDispatchThread();
    Semaphore lock = new Semaphore(1);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> lock.up());
    lock.waitFor();
  }

  public static boolean isJreHiDPI() {
    return StartupUiUtil.isJreHiDPI();
  }

  public static Color makeTransparent(@NotNull Color color, @NotNull Color backgroundColor, double transparency) {
    int r = makeTransparent(transparency, color.getRed(), backgroundColor.getRed());
    int g = makeTransparent(transparency, color.getGreen(), backgroundColor.getGreen());
    int b = makeTransparent(transparency, color.getBlue(), backgroundColor.getBlue());

    //noinspection UseJBColor
    return new Color(r, g, b);
  }

  private static int makeTransparent(double transparency, int channel, int backgroundChannel) {
    final int result = (int)(backgroundChannel * (1 - transparency) + channel * transparency);
    if (result < 0) {
      return 0;
    }
    return Math.min(result, 255);
  }
}
