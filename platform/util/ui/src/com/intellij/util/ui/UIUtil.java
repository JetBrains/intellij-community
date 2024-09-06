// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.BundleBase;
import com.intellij.concurrency.ThreadContext;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.*;
import com.intellij.ui.icons.HiDPIImage;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.*;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;
import sun.font.FontUtilities;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.FocusManager;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.RGBImageFilter;
import java.awt.print.PrinterGraphics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public final class UIUtil {
  public static final @NlsSafe String BORDER_LINE = "<hr size=1 noshade>";
  public static final @NlsSafe String BR = "<br/>";
  public static final @NlsSafe String HR = "<hr/>";
  public static final @NlsSafe String LINE_SEPARATOR = "\n";
  private static final Key<WeakReference<Component>> FOSTER_PARENT = Key.create("Component.fosterParent");
  private static final Key<Boolean> HAS_FOCUS = Key.create("Component.hasFocus");

  /**
   * A key for hiding a line under the window title bar on macOS
   * It works if and only if transparent title bars are enabled and IDE runs on JetBrains Runtime
   */
  @ApiStatus.Internal
  public static final String NO_BORDER_UNDER_WINDOW_TITLE_KEY = "";

  // cannot be static because logging maybe not configured yet
  private static @NotNull Logger getLogger() {
    return Logger.getInstance(UIUtil.class);
  }

  public static int getTransparentTitleBarHeight(JRootPane rootPane) {
    Object property = rootPane.getClientProperty("Window.transparentTitleBarHeight");
    if (property instanceof Integer) {
      return (int)property;
    }

    if ("small".equals(rootPane.getClientProperty("Window.style"))) {
      return JBUI.getInt("macOSWindow.Title.heightSmall", 19);
    }
    else {
      return JBUI.getInt("macOSWindow.Title.height", SystemInfo.isMacOSBigSur ? 29 : 23);
    }
  }

  // Here we setup dialog to be suggested in OwnerOptional as owner even if the dialog is not modal
  public static void markAsPossibleOwner(Dialog dialog) {
    ClientProperty.put(dialog, "PossibleOwner", Boolean.TRUE);
  }

  public static boolean isPossibleOwner(@NotNull Dialog dialog) {
    return ClientProperty.isTrue(dialog, "PossibleOwner");
  }

  public static int getMultiClickInterval() {
    Object property = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
    if (property instanceof Integer) {
      return (Integer)property;
    }
    return 500;
  }

  private static final Supplier<Boolean> X_RENDER_ACTIVE = new SynchronizedClearableLazy<>(() -> {
    if (!StartupUiUtil.isXToolkit()) {
      return false;
    }

    try {
      Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass("sun.awt.X11GraphicsEnvironment");
      Method method = clazz.getMethod("isXRenderAvailable");
      return (Boolean)method.invoke(null);
    }
    catch (Throwable e) {
      return false;
    }
  });

  private static final String[] STANDARD_FONT_SIZES =
    {"8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};

  public static void applyStyle(@NotNull ComponentStyle componentStyle, @NotNull Component comp) {
    if (!(comp instanceof JComponent c)) {
      return;
    }

    if (isUnderAquaBasedLookAndFeel()) {
      c.putClientProperty("JComponent.sizeVariant", Strings.toLowerCase(componentStyle.name()));
    }
    FontSize fontSize;
    if (componentStyle == ComponentStyle.MINI) {
      fontSize = FontSize.MINI;
    }
    else if (componentStyle == ComponentStyle.SMALL) {
      fontSize = FontSize.SMALL;
    }
    else {
      fontSize = FontSize.NORMAL;
    }
    c.setFont(getFont(fontSize, c.getFont()));
    Container p = c.getParent();
    if (p != null) {
      SwingUtilities.updateComponentTreeUI(p);
    }
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

  public static @NotNull Couple<Color> getCellColors(@NotNull JTable table, boolean isSel, int row, int column) {
    return Couple.of(isSel ? table.getSelectionForeground() : table.getForeground(),
                     isSel ? table.getSelectionBackground() : table.getBackground());
  }

  public static void fixOSXEditorBackground(@NotNull JTable table) {
    if (!SystemInfoRt.isMac) {
      return;
    }

    if (table.isEditing()) {
      int column = table.getEditingColumn();
      int row = table.getEditingRow();
      Component renderer = column >= 0 && row >= 0 ? table.getCellRenderer(row, column)
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
  public static final @NlsSafe String HTML_MIME = "text/html";
  public static final @NonNls String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";
  /**
   * Prevent component DataContext from returning parent editor
   * Useful for components that are manually painted over the editor to prevent shortcuts from falling-through to editor
   * <p>
   * Usage: {@code component.putClientProperty(HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, Boolean.TRUE)}
   *
   * @deprecated Use {@link com.intellij.openapi.actionSystem.CustomizedDataContext#EXPLICIT_NULL} instead.
   */
  @Deprecated(forRemoval = true)
  public static final @NonNls String HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY = "AuxEditorComponent";
  public static final @NonNls String CENTER_TOOLTIP_DEFAULT = "ToCenterTooltip";
  public static final @NonNls String CENTER_TOOLTIP_STRICT = "ToCenterTooltip.default";

  public static final @NonNls String ENABLE_IME_FORWARDING_IN_POPUP = "EnableIMEForwardingInPopup";

  private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("<\\s*([^<>/ ]+)([^<>]*)/\\s*>", Pattern.CASE_INSENSITIVE);

  public static final Key<Integer> KEEP_BORDER_SIDES = Key.create("keepBorderSides");

  /**
   * Alt+click does copy text from tooltip or balloon to clipboard.
   * We collect this text from components recursively and this generic approach might 'grab' unexpected text fragments.
   * To provide more accurate text scope you should mark dedicated component with putClientProperty(TEXT_COPY_ROOT, Boolean.TRUE)
   * Note, main(root) components of BalloonImpl and AbstractPopup are already marked with this key
   */
  public static final Key<Boolean> TEXT_COPY_ROOT = Key.create("TEXT_COPY_ROOT");

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

  private static final @NonNls String ROOT_PANE = "JRootPane.future";

  private static final Ref<Boolean> ourRetina = Ref.create(SystemInfoRt.isMac ? null : false);

  private UIUtil() {
  }

  public static boolean isRetina(@NotNull Graphics2D graphics) {
    return SystemInfoRt.isMac ? DetectRetinaKit.isMacRetina(graphics) : isRetina();
  }

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
            if (scale != null && scale == 2) {
              ourRetina.set(true);
              return true;
            }
          }
          catch (AWTError | Exception ignore) {
          }
          ourRetina.set(false);
        }

        return ourRetina.get();
      }
    }
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       the client property key
   * @return {@code true} if the property of the specified component is set to {@code true}
   * @deprecated use {@link ClientProperty#isTrue(Component, Object)} instead
   */
  @Deprecated
  public static boolean isClientPropertyTrue(Object component, @NotNull Object key) {
    return component instanceof Component && ClientProperty.isTrue((Component)component, key);
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       the client property key that specifies a return type
   * @return the property value from the specified component or {@code null}
   * @deprecated use {@link ClientProperty#get(Component, Object)} instead
   */
  @Deprecated(forRemoval = true)
  public static Object getClientProperty(Object component, @NotNull @NonNls Object key) {
    return component instanceof Component ? ClientProperty.get((Component)component, key) : null;
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @return the property value from the specified component or {@code null}
   */
  public static <T> T getClientProperty(Component component, @NotNull Class<T> type) {
    Object obj = ClientProperty.get(component, type);
    return type.isInstance(obj) ? type.cast(obj) : null;
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       the client property key that specifies a return type
   * @return the property value from the specified component or {@code null}
   * @deprecated use {@link ClientProperty#get(Component, Key)} instead
   */
  @Deprecated
  public static <T> @Nullable T getClientProperty(Object component, @NotNull Key<T> key) {
    return component instanceof Component ? ClientProperty.get((Component)component, key) : null;
  }

  /**
   * @deprecated use {@link JComponent#putClientProperty(Object, Object)}
   * or {@link ClientProperty#put(JComponent, Key, Object)} instead
   */
  @Deprecated
  public static <T> void putClientProperty(@NotNull JComponent component, @NotNull Key<T> key, T value) {
    component.putClientProperty(key, value);
  }

  @Contract(pure = true)
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

  @SuppressWarnings("HardCodedStringLiteral")
  public static @NotNull @Nls String getHtmlBody(@NotNull Html html) {
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
    if (c == KeyEvent.CHAR_UNDEFINED) {
      // ignore CHAR_UNDEFINED, like Swing text components do
      return false;
    }
    if (c < 0x20 || c == 0x7F) {
      return false;
    }

    // allow input of special characters on Windows in Persian keyboard layout using Ctrl+Shift+1..4
    if (SystemInfoRt.isWindows && c >= 0x200C && c <= 0x200F) {
      return true;
    }
    else if (SystemInfoRt.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }
    else {
      return !e.isAltDown() && !e.isControlDown();
    }
  }

  public static int getStringY(final @NotNull String string, final @NotNull Rectangle bounds, final @NotNull Graphics2D g) {
    int centerY = bounds.height / 2;
    Font font = g.getFont();
    FontRenderContext frc = g.getFontRenderContext();
    Rectangle stringBounds = font.getStringBounds(string.isEmpty() ? " " : string, frc).getBounds();
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
      }
      else if (label.getHorizontalTextPosition() == SwingConstants.LEADING) {
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
   * @param string   {@code String} to examine
   * @param font     {@code Font} that is used to render the string
   * @param graphics {@link Graphics} that should be used to render the string
   * @return height of the tallest glyph in a string. If string is empty, returns 0
   */
  public static int getHighestGlyphHeight(@NotNull String string, @NotNull Font font, @NotNull Graphics graphics) {
    FontRenderContext frc = ((Graphics2D)graphics).getFontRenderContext();
    GlyphVector gv = font.createGlyphVector(frc, string);
    int maxHeight = 0;
    for (int i = 0; i < string.length(); i++) {
      maxHeight = Math.max(maxHeight, (int)gv.getGlyphMetrics(i).getBounds2D().getHeight());
    }
    return maxHeight;
  }

  public static void setEnabled(@NotNull Component component, boolean enabled, boolean recursively) {
    setEnabled(component, enabled, recursively, false);
  }

  public static void setEnabled(@NotNull Component component, boolean enabled, boolean recursively, final boolean visibleOnly) {
    JBIterable<Component> all = recursively ? uiTraverser(component).expandAndFilter(
      visibleOnly ? Component::isVisible : Conditions.alwaysTrue()).traverse() : JBIterable.of(component);
    Color fg = enabled ? getLabelForeground() : getLabelDisabledForeground();
    for (Component c : all) {
      c.setEnabled(enabled);
      if (c instanceof JLabel) {
        c.setForeground(fg);
      }
    }
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
    WavePainter.forColor(g.getColor()).paint(g, (int)rectangle.getMinX(), (int)rectangle.getMaxX(), (int)rectangle.getMaxY());
  }

  public static String @NotNull [] splitText(@NotNull String text, @NotNull FontMetrics fontMetrics, int widthLimit, char separator) {
    List<String> lines = new ArrayList<>();
    StringBuilder currentLine = new StringBuilder();
    StringBuilder currentAtom = new StringBuilder();

    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      currentAtom.append(ch);

      boolean lineBreak = ch == '\n';
      if (lineBreak || ch == separator) {
        currentLine.append(currentAtom);
        currentAtom.setLength(0);
      }

      String s = currentLine.toString() + currentAtom;
      int width = fontMetrics.stringWidth(s);

      if (lineBreak || width >= widthLimit - fontMetrics.charWidth('w')) {
        if (!currentLine.isEmpty()) {
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

  public static void setActionNameAndMnemonic(@NotNull @Nls String text, @NotNull Action action) {
    assignMnemonic(text, action);

    text = text.replaceAll("&", "");
    action.putValue(Action.NAME, text);
  }

  public static void assignMnemonic(@NotNull @Nls String text, @NotNull Action action) {
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

    return JBFont.create(base).deriveFont(getFontSize(size));
  }

  public static float getFontSize(@NotNull FontSize size) {
    int defSize = StartupUiUtil.getLabelFont().getSize();
    return switch (size) {
      case SMALL -> Math.max(defSize - JBUIScale.scale(2f), JBUIScale.scale(11f));
      case MINI -> Math.max(defSize - JBUIScale.scale(4f), JBUIScale.scale(9f));
      default -> defSize;
    };
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

    Icon buttonIcon = cb.getIcon();
    if (buttonIcon == null && ui != null) {
      if (ui instanceof BasicRadioButtonUI) {
        buttonIcon = ((BasicRadioButtonUI)ui).getDefaultIcon();
      }
    }

    return getButtonTextHorizontalOffset(cb, cb.getSize(new Dimension()), buttonIcon);
  }

  public static int getButtonTextHorizontalOffset(@NotNull AbstractButton button, @NotNull Dimension size, @Nullable Icon buttonIcon) {
    String text = button.getText();

    Rectangle viewRect = new Rectangle();
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Insets i = button.getInsets();

    viewRect.y = i.top;
    viewRect.width = size.width - (i.right + viewRect.x);
    viewRect.height = size.height - (i.bottom + viewRect.y);

    SwingUtilities.layoutCompoundLabel(
      button, button.getFontMetrics(button.getFont()), text, buttonIcon,
      button.getVerticalAlignment(), button.getHorizontalAlignment(),
      button.getVerticalTextPosition(), button.getHorizontalTextPosition(),
      viewRect, iconRect, textRect,
      text == null ? 0 : button.getIconTextGap());

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

  public static @NotNull Color getLabelSuccessForeground() {
    return JBColor.namedColor("Label.successForeground", 0x368746, 0x50A661);
  }

  public static @NotNull Color getErrorForeground() {
    return NamedColorUtil.getErrorForeground();
  }

  public static @NotNull Color getLabelDisabledForeground() {
    return JBColor.namedColor("Label.disabledForeground", JBColor.GRAY);
  }

  public static @NotNull Color getLabelInfoForeground() {
    return JBColor.namedColor("Label.infoForeground", new JBColor(Gray._120, Gray._135));
  }

  public static @NotNull Color getContextHelpForeground() {
    return JBUI.CurrentTheme.ContextHelp.FOREGROUND;
  }

  public static @Nls @NotNull String removeMnemonic(@Nls @NotNull String s) {
    return TextWithMnemonic.parse(s).getText();
  }

  public static int getDisplayMnemonicIndex(@NotNull String s) {
    int idx = s.indexOf('&');
    if (idx >= 0 && idx != s.length() - 1 && idx == s.lastIndexOf('&')) return idx;

    idx = s.indexOf(MNEMONIC);
    if (idx >= 0 && idx != s.length() - 1 && idx == s.lastIndexOf(MNEMONIC)) return idx;

    return -1;
  }

  public static @Nls String replaceMnemonicAmpersand(@Nls String value) {
    return BundleBase.replaceMnemonicAmpersand(value);
  }

  /**
   * @deprecated use {@link #getTreeForeground()}
   */
  @Deprecated(forRemoval = true)
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
    return NamedColorUtil.getInactiveTextColor();
  }

  public static Color getInactiveTextFieldBackgroundColor() {
    return UIManager.getColor("TextField.inactiveBackground");
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

  public static Color getTextFieldDisabledBackground() {
    return UIManager.getColor("TextField.disabledBackground");
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
    return UIManager.getColor("Label.foreground");
  }

  public static Color getControlColor() {
    return UIManager.getColor("control");
  }

  public static Font getOptionPaneMessageFont() {
    return UIManager.getFont("OptionPane.messageFont");
  }

  /**
   * @deprecated Use {@link FontUtil#getMenuFont()}
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public static Font getMenuFont() {
    return FontUtil.getMenuFont();
  }

  /**
   * @deprecated use {@link JBUI.CurrentTheme.CustomFrameDecorations#separatorForeground()}
   */
  @Deprecated(forRemoval = true)
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
    return Objects.requireNonNullElse(UIManager.getIcon("OptionPane.errorIcon"), AllIcons.General.ErrorDialog);
  }

  public static @NotNull Icon getInformationIcon() {
    return Objects.requireNonNullElse(UIManager.getIcon("OptionPane.informationIcon"), AllIcons.General.InformationDialog);
  }

  public static @NotNull Icon getQuestionIcon() {
    return Objects.requireNonNullElse(UIManager.getIcon("OptionPane.questionIcon"), AllIcons.General.QuestionDialog);
  }

  public static @NotNull Icon getWarningIcon() {
    return Objects.requireNonNullElse(UIManager.getIcon("OptionPane.warningIcon"), AllIcons.General.WarningDialog);
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

  public static @NotNull Icon getTreeSelectedCollapsedIcon() {
    Icon icon = UIManager.getIcon("Tree.collapsedSelectedIcon");
    return icon == null ? getTreeCollapsedIcon() : icon;
  }

  public static @NotNull Icon getTreeSelectedExpandedIcon() {
    Icon icon = UIManager.getIcon("Tree.expandedSelectedIcon");
    return icon == null ? getTreeExpandedIcon() : icon;
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
  @Deprecated(forRemoval = true)
  public static boolean isUnderAquaLookAndFeel() {
    return SystemInfoRt.isMac && UIManager.getLookAndFeel().getName().contains("Mac OS X");
  }

  /**
   * @deprecated Nimbus Look-n-Feel is deprecated and not supported anymore
   */
  @Deprecated(forRemoval = true)
  public static boolean isUnderNimbusLookAndFeel() {
    return false;
  }

  public static boolean isUnderAquaBasedLookAndFeel() {
    return SystemInfoRt.isMac && (StartupUiUtil.isUnderDarcula() || isUnderIntelliJLaF());
  }

  /**
   * Do not use it. Use theme properties instead of it.
   */
  @Deprecated(forRemoval = true)
  public static boolean isUnderDefaultMacTheme() {
    return false;
  }

  /**
   * Do not use it. Use theme properties instead of it.
   */
  @Deprecated(forRemoval = true)
  public static boolean isUnderWin10LookAndFeel() {
    return false;
  }

  /**
   * @deprecated Do not use it. Use {@link JBColor#isBright()} to detect if current LaF theme is dark or bright.
   * See also {@link com.intellij.openapi.editor.colors.EditorColorsManager#isDarkEditor()}
   */
  @Deprecated(forRemoval = true)
  public static boolean isUnderDarcula() {
    return StartupUiUtil.INSTANCE.isDarkTheme();
  }

  public static boolean isUnderIntelliJLaF() {
    return StartupUiUtil.isUnderIntelliJLaF();
  }

  @Deprecated(forRemoval = true)
  public static boolean isUnderGTKLookAndFeel() {
    return SystemInfoRt.isUnix && !SystemInfoRt.isMac && UIManager.getLookAndFeel().getName().contains("GTK");
  }

  public static boolean isGraphite() {
    if (!SystemInfoRt.isMac) {
      return false;
    }

    try {
      // https://developer.apple.com/library/mac/documentation/Cocoa/Reference/ApplicationKit/Classes/NSCell_Class/index.html#//apple_ref/doc/c_ref/NSGraphiteControlTint
      // NSGraphiteControlTint = 6
      return Foundation.invoke("NSColor", "currentControlTint").intValue() == 6;
    }
    catch (Exception e) {
      return false;
    }
  }

  public static @NotNull Font getToolbarFont() {
    return SystemInfoRt.isMac ? getLabelFont(UIUtil.FontSize.SMALL) : StartupUiUtil.getLabelFont();
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

  /**
   * @param c1     first color to mix
   * @param c2     second color to mix
   * @param factor impact of color specified in {@code c2}
   * @return Mixed color
   */
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
    return getListViewportPadding(false);
  }

  public static @NotNull Insets getListViewportPadding(boolean listWithAdvertiser) {
    return listWithAdvertiser ? new JBInsets(4, 0, 8, 0) : JBInsets.create(4, 0);
  }

  public static boolean isToUseDottedCellBorder() {
    return !isUnderNativeMacLookAndFeel();
  }

  public static boolean isControlKeyDown(@NotNull MouseEvent mouseEvent) {
    return SystemInfoRt.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  public static @NotNull String getControlKeyName() {
    return SystemInfoRt.isMac ? "Command" : "Ctrl";
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
    if (SystemInfoRt.isMac && !isRetina() || SystemInfoRt.isLinux) {
      drawAppleDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
    else {
      drawBoringDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
  }

  @SuppressWarnings({"UnregisteredNamedColor", "UseJBColor"})
  public static void drawSearchMatch(@NotNull Graphics2D g,
                                     final float startX,
                                     final float endX,
                                     final int height) {
    drawSearchMatch(g, startX, endX, height, getSearchMatchGradientStartColor(), getSearchMatchGradientEndColor());
  }

  public static @NotNull JBColor getSearchMatchGradientStartColor() {
    return JBColor.namedColor("SearchMatch.startBackground", JBColor.namedColor("SearchMatch.startColor", new Color(0xb3ffeaa2, true)));
  }

  public static @NotNull JBColor getSearchMatchGradientEndColor() {
    return JBColor.namedColor("SearchMatch.endBackground", JBColor.namedColor("SearchMatch.endColor", new Color(0xb3ffd042, true)));
  }

  public static void drawSearchMatch(@NotNull Graphics2D g, float startXf, float endXf, int height, Color c1, Color c2) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setPaint(getGradientPaint(startXf, 2, c1, startXf, height - 5, c2));

      if (JreHiDpiUtil.isJreHiDPI(g2)) {
        GraphicsUtil.setupRoundedBorderAntialiasing(g2);
        g2.fill(new RoundRectangle2D.Float(startXf, 2, endXf - startXf, height - 4, 5, 5));
      }
      else {
        int startX = (int)startXf;
        int endX = (int)endXf;

        g2.fillRect(startX, 3, endX - startX, height - 5);

        boolean drawRound = endXf - startXf > 4;
        if (drawRound) {
          LinePainter2D.paint(g2, startX - 1, 4, startX - 1, height - 4);
          LinePainter2D.paint(g2, endX, 4, endX, height - 4);

          g2.setColor(new Color(100, 100, 100, 50));
          LinePainter2D.paint(g2, startX - 1, 4, startX - 1, height - 4);
          LinePainter2D.paint(g2, endX, 4, endX, height - 4);

          LinePainter2D.paint(g2, startX, 3, endX - 1, 3);
          LinePainter2D.paint(g2, startX, height - 3, endX - 1, height - 3);
        }
      }
    }
    finally {
      g2.dispose();
    }
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

    AppleBoldDottedPainter painter = AppleBoldDottedPainter.forColor(Objects.requireNonNullElse(fgColor, oldColor));
    painter.paint(g, startX, endX, lineY);
  }

  @Deprecated(forRemoval = true)
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
   * Creates a HiDPI-aware BufferedImage in the graphics config scale.
   *
   * @param gc     the graphics config
   * @param width  the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type   the type of the image
   * @param rm     the rounding mode to apply to width/height (for a HiDPI-aware image, the rounding is applied in the device space)
   * @return a HiDPI-aware BufferedImage in the graphics scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(GraphicsConfiguration gc,
                                                   double width,
                                                   double height,
                                                   int type,
                                                   @NotNull RoundingMode rm) {
    if (JreHiDpiUtil.isJreHiDPI(gc)) {
      return new HiDPIImage(gc, width, height, type, rm);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(rm.round(width), rm.round(height), type);
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the component scale.
   *
   * @param component the component associated with the target graphics device
   * @param width     the width in user coordinate space
   * @param height    the height in user coordinate space
   * @param type      the type of the image
   * @return a HiDPI-aware BufferedImage in the component scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(@Nullable Component component, int width, int height, int type) {
    return ImageUtil.createImage(component == null ? null : component.getGraphicsConfiguration(), width, height, type);
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
    g.setComposite(X_RENDER_ACTIVE.get() ? AlphaComposite.SrcOver : AlphaComposite.Src);
  }

  /**
   * Dispatch all pending invocation events (if any) in the {@link com.intellij.ide.IdeEventQueue}, ignores and removes all other events from the queue.
   * In tests, consider using {@link com.intellij.testFramework.PlatformTestUtil#dispatchAllInvocationEventsInIdeEventQueue()} instead
   * Must be called from EDT.
   *
   * @see #pump()
   */
  @TestOnly
  public static void dispatchAllInvocationEvents() {
    try (AccessToken ignored = ThreadContext.resetThreadContext()) {
      EDT.dispatchAllInvocationEvents();
    }
  }

  public static void addAwtListener(@NotNull AWTEventListener listener, long mask, @NotNull Disposable parent) {
    StartupUiUtil.addAwtListener(mask, parent, listener);
  }

  public static void addParentChangeListener(@NotNull Component component, @NotNull PropertyChangeListener listener) {
    component.addPropertyChangeListener("ancestor", listener);
  }

  public static void drawVDottedLine(@NotNull Graphics2D g,
                                     int lineX,
                                     int startY,
                                     int endY,
                                     final @Nullable Color bgColor,
                                     final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      LinePainter2D.paint(g, lineX, startY, lineX, endY);
    }

    g.setColor(fgColor);
    for (int i = startY / 2 * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  public static void drawHDottedLine(@NotNull Graphics2D g,
                                     int startX,
                                     int endX,
                                     int lineY,
                                     final @Nullable Color bgColor,
                                     final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      LinePainter2D.paint(g, startX, lineY, endX, lineY);
    }

    g.setColor(fgColor);

    for (int i = startX / 2 * 2; i < endX; i += 2) {
      g.drawRect(i, lineY, 0, 0);
    }
  }

  public static void drawDottedLine(@NotNull Graphics2D g,
                                    int x1,
                                    int y1,
                                    int x2,
                                    int y2,
                                    final @Nullable Color bgColor,
                                    final Color fgColor) {
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

  public static void drawStringWithHighlighting(@NotNull Graphics g,
                                                @NotNull String s,
                                                int x,
                                                int y,
                                                Color foreground,
                                                Color highlighting) {
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
   *
   * @param g            the {@link Graphics} instance to draw to
   * @param rect         the {@link Rectangle} to use as bounding box
   * @param str          the string to draw
   * @param horzCentered if true, the string will be centered horizontally
   * @param vertCentered if true, the string will be centered vertically
   */
  public static void drawCenteredString(@NotNull Graphics2D g,
                                        @NotNull Rectangle rect,
                                        @NotNull String str,
                                        boolean horzCentered,
                                        boolean vertCentered) {
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
   *
   * @param g    the {@link Graphics} instance to draw to
   * @param rect the {@link Rectangle} to use as bounding box
   * @param str  the string to draw
   */
  public static void drawCenteredString(@NotNull Graphics2D g, @NotNull Rectangle rect, @NotNull String str) {
    drawCenteredString(g, rect, str, true, true);
  }

  /**
   * @param component to check whether it has focus within its component hierarchy
   * @return {@code true} if component or one of its parents has focus in a more general sense than UI focuses,
   * sometimes useful to limit various activities by checking the focus of the real UI,
   * but it could be unneeded in headless mode or in other scenarios.
   * @see Component#isFocusOwner()
   */
  public static boolean isFocusAncestor(@NotNull Component component) {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) {
      return false;
    }
    if (SwingUtilities.isDescendingFrom(owner, component) || GraphicsEnvironment.isHeadless()) {
      return true;
    }

    while (component != null) {
      if (kindaHasFocus(component)) {
        return true;
      }
      component = component.getParent();
    }
    return false;
  }

  /**
   * Get the parent of a component in a more general sense than UI parent, i.e.,
   * if it doesn't have a real UI parent, use a foster one instead.
   *
   * @see UIUtil#setFosterParent(JComponent, Component)
   */
  @ApiStatus.Experimental
  public static @Nullable Component getParent(@NotNull Component component) {
    WeakReference<Component> ref = ClientProperty.get(component, FOSTER_PARENT);
    if (ref != null) {
      Component fosterParent = ref.get();
      if (fosterParent != null) {
        return fosterParent;
      }
    }

    return component.getParent();
  }

  /**
   * Set a foster parent for a component, i.e., explicitly specify what should be treated
   * as the component's parent if it doesn't have a real UI one.
   *
   * @throws IllegalArgumentException if the establishment of requested link
   *                                  will form a cycle in a generalized hierarchy graph.
   * @see UIUtil#getParent(Component)
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void setFosterParent(@NotNull JComponent component, @Nullable Component parent) {
    WeakReference<Component> ref = validateFosterParent(component, parent);
    ClientProperty.put(component, FOSTER_PARENT, ref);
  }

  /**
   * An overload of {@link UIUtil#setFosterParent(JComponent, Component)} for windows.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void setFosterParent(@NotNull Window window, @Nullable Component parent) {
    WeakReference<Component> ref = validateFosterParent(window, parent);
    ClientProperty.put(window, FOSTER_PARENT, ref);
  }

  private static @Nullable WeakReference<Component> validateFosterParent(@NotNull Component component, @Nullable Component parent) {
    if (parent != null && isGeneralizedAncestor(component, parent)) {
      throw new IllegalArgumentException("Setting this component as a foster parent will form a cycle in a hierarchy graph");
    }
    return parent != null ? new WeakReference<>(parent) : null;
  }

  private static boolean isGeneralizedAncestor(@NotNull Component ancestor, @NotNull Component descendant) {
    do {
      if (descendant == ancestor) {
        return true;
      }
      descendant = getParent(descendant);
    }
    while (descendant != null);
    return false;
  }

  private static boolean kindaHasFocus(@NotNull Component component) {
    JComponent jComponent = component instanceof JComponent ? (JComponent)component : null;
    return jComponent != null && Boolean.TRUE.equals(jComponent.getClientProperty(HAS_FOCUS));
  }

  /**
   * Checks if a component is focused in a more general sense than UI focuses,
   * sometimes useful to limit various activities by checking the focus of real UI,
   * but it could be unneeded in headless mode or in other scenarios.
   *
   * @see UIUtil#isShowing(Component)
   */
  @ApiStatus.Experimental
  public static boolean hasFocus(@NotNull Component component) {
    return GraphicsEnvironment.isHeadless() || kindaHasFocus(component) || component.hasFocus();
  }

  /**
   * Marks a component as focused
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void markAsFocused(@NotNull JComponent component, boolean value) {
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }
    component.putClientProperty(HAS_FOCUS, value ? Boolean.TRUE : null);
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

  /**
   * Provides all input event modifiers including deprecated, since they are still used in IntelliJ platform
   */
  @MagicConstant(flags = {
    Event.SHIFT_MASK, Event.CTRL_MASK, Event.META_MASK, Event.ALT_MASK, InputEvent.SHIFT_DOWN_MASK,
    InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.BUTTON1_DOWN_MASK,
    InputEvent.BUTTON2_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK, InputEvent.ALT_GRAPH_DOWN_MASK,
  })
  public static int getAllModifiers(@NotNull InputEvent event) {
    return event.getModifiers() | event.getModifiersEx();
  }

  public static @NotNull Color getBgFillColor(@NotNull Component c) {
    Component parent = findNearestOpaque(c);
    return parent == null ? c.getBackground() : parent.getBackground();
  }

  public static @Nullable Component findNearestOpaque(Component c) {
    return ComponentUtil.findParentByCondition(c, Component::isOpaque);
  }

  //x and y should be from {0, 0} to {parent.getWidth(), parent.getHeight()}
  public static @Nullable Component getDeepestComponentAt(@NotNull Component parent, int x, int y) {
    Component component = SwingUtilities.getDeepestComponentAt(parent, x, y);
    if (component != null && component.getParent() instanceof JRootPane rootPane) { // GlassPane case
      component = getDeepestComponentAtForComponent(parent, x, y, rootPane.getLayeredPane());
      if (component == null) {
        component = getDeepestComponentAtForComponent(parent, x, y, rootPane.getContentPane());
      }
    }
    if (component != null && component.getParent() instanceof JLayeredPane) { // Handle LoadingDecorator
      Component[] components = ((JLayeredPane)component.getParent()).getComponentsInLayer(JLayeredPane.DEFAULT_LAYER);
      if (components.length == 1 && ArrayUtilRt.indexOf(components, component, 0, components.length) == -1) {
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
  public static @NlsSafe @NotNull String getCssFontDeclaration(@NotNull Font font) {
    return getCssFontDeclaration(font, getLabelForeground(), JBUI.CurrentTheme.Link.Foreground.ENABLED, null);
  }

  @Language("HTML")
  public static @NlsSafe @NotNull String getCssFontDeclaration(@NotNull Font font,
                                                               @Nullable Color fgColor,
                                                               @Nullable Color linkColor,
                                                               @Nullable String liImg) {
    @Language("HTML")
    String familyAndSize = "font-family:'" + font.getFamily() + "'; font-size:" + font.getSize() + "pt;";
    return "<style>\n"
           + "body, div, td, p {" + familyAndSize
           + (fgColor != null ? " color:#" + ColorUtil.toHex(fgColor) + ';' : "")
           + "}\n"
           + "a {" + familyAndSize
           + (linkColor != null ? " color:#" + ColorUtil.toHex(linkColor) + ';' : "")
           + "}\n"
           + "code {font-size:" + font.getSize() + "pt;}\n"
           + "ul {list-style:disc; margin-left:15px;}\n"
           + "</style>";
  }

  public static @NotNull Color getFocusedFillColor() {
    return toAlpha(getListSelectionBackground(true), 100);
  }

  public static @NotNull Color getFocusedBoundsColor() {
    return NamedColorUtil.getBoundsColor();
  }

  public static @NotNull Color getBoundsColor() {
    return NamedColorUtil.getBoundsColor();
  }

  public static @NotNull Color toAlpha(final Color color, final int alpha) {
    Color actual = color != null ? color : Color.black;
    return new Color(actual.getRed(), actual.getGreen(), actual.getBlue(), alpha);
  }

  /**
   * @param component to check whether it can be focused or not
   * @return {@code true} if component is not {@code null} and can be focused
   * @see Component#isRequestFocusAccepted(boolean, boolean, FocusEvent.Cause)
   */
  public static boolean isFocusable(@Nullable Component component) {
    return component != null && component.isFocusable() && component.isEnabled() && component.isShowing();
  }

  /**
   * @deprecated use {@link com.intellij.openapi.wm.IdeFocusManager}
   */
  @Deprecated
  public static void requestFocus(@NotNull JComponent c) {
    if (c.isShowing()) {
      c.requestFocus();
    }
    else {
      SwingUtilities.invokeLater(c::requestFocus);
    }
  }

  // whitelist for component types that provide obvious 'focused' view
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
   * @deprecated use {@link HTMLEditorKitBuilder}
   */
  @Deprecated
  public static @NotNull HTMLEditorKit getHTMLEditorKit() {
    return HTMLEditorKitBuilder.simple();
  }

  /**
   * @deprecated use {@link HTMLEditorKitBuilder}
   */
  @Deprecated
  public static @NotNull HTMLEditorKit getHTMLEditorKit(boolean noGapsBetweenParagraphs) {
    HTMLEditorKitBuilder builder = new HTMLEditorKitBuilder();
    if (!noGapsBetweenParagraphs) {
      builder.withGapsBetweenParagraphs();
    }
    return builder.build();
  }

  /**
   * @deprecated use {@link HTMLEditorKitBuilder#withWordWrapViewFactory()}
   */
  @Deprecated
  public static final class JBWordWrapHtmlEditorKit extends JBHtmlEditorKit {
    @Override
    public ViewFactory getViewFactory() {
      return ExtendableHTMLViewFactory.DEFAULT_WORD_WRAP;
    }
  }

  public static @NotNull Font getFontWithFallbackIfNeeded(@NotNull Font font, @NotNull String text) {
    if (!SystemInfoRt.isMac /* 'getFontWithFallback' does nothing on macOS */ && font.canDisplayUpTo(text) != -1) {
      return getFontWithFallback(font);
    }
    else {
      return font;
    }
  }

  public static @NotNull Font getFontWithFallbackIfNeeded(@NotNull Font font, @NotNull char[] text) {
    if (!SystemInfoRt.isMac /* 'getFontWithFallback' does nothing on macOS */ && font.canDisplayUpTo(text, 0, text.length) != -1) {
      return getFontWithFallback(font);
    }
    else {
      return font;
    }
  }

  public static @NotNull FontUIResource getFontWithFallback(@NotNull Font font) {
    // On macOS font fallback is implemented in JDK by default
    // (except for explicitly registered fonts, e.g. the fonts we bundle with IDE, for them we don't have a solution now)
    if (!SystemInfoRt.isMac) {
      try {
        if (!FontUtilities.fontSupportsDefaultEncoding(font)) {
          font = FontUtilities.getCompositeFontUIResource(font);
        }
      }
      catch (Throwable e) {
        // this might happen e.g., if we're running under newer runtime, forbidding access to sun.font package
        getLogger().warn(e);
        // this might not give the same result, but we have no choice here
        return StartupUiUtilKt.getFontWithFallback(font.getFamily(), font.getStyle(), font.getSize());
      }
    }
    return font instanceof FontUIResource ? (FontUIResource)font : new FontUIResource(font);
  }

  public static @NotNull FontUIResource getFontWithFallback(@Nullable String familyName, @JdkConstants.FontStyle int style, int size) {
    return StartupUiUtilKt.getFontWithFallback(familyName, style, size);
  }

  //Escape error-prone HTML data (if any) when we use it in renderers, see IDEA-170768
  public static <T> T htmlInjectionGuard(T toRender) {
    if (toRender instanceof String && Strings.toLowerCase((String)toRender).startsWith("<html>")) {
      //noinspection unchecked
      return (T)("<html>" + Strings.escapeXmlEntities((String)toRender));
    }
    return toRender;
  }

  /**
   * @deprecated This method is a hack. Please avoid it and create borderless {@code JScrollPane} manually using
   * {@link com.intellij.ui.ScrollPaneFactory#createScrollPane(Component, boolean)}.
   */
  @Deprecated
  public static void removeScrollBorder(@NotNull Component c) {
    JBIterable<JScrollPane> scrollPanes = uiTraverser(c)
      .expand(o -> o == c || o instanceof JPanel || o instanceof JLayeredPane)
      .filter(JScrollPane.class);
    for (JScrollPane scrollPane : scrollPanes) {
      Integer keepBorderSides = ClientProperty.get(scrollPane, KEEP_BORDER_SIDES);
      if (keepBorderSides == null) {
        scrollPane.setBorder(new SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.NONE));
      }
      else if (scrollPane.getBorder() instanceof LineBorder lineBorder) {
        Color color = lineBorder.getLineColor();
        //noinspection MagicConstant
        scrollPane.setBorder(new SideBorder(color, keepBorderSides.intValue()));
      }
      else {
        //noinspection MagicConstant
        scrollPane.setBorder(new SideBorder(NamedColorUtil.getBoundsColor(), keepBorderSides.intValue()));
      }
    }
  }

  public static @NotNull @NlsSafe String toHtml(@NotNull @Nls String html) {
    return toHtml(html, 0);
  }

  public static @NotNull @NlsSafe String toHtml(@NotNull @Nls String html, final int hPadding) {
    final @NlsSafe String withClosedTag = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
    Font font = StartupUiUtil.getLabelFont();
    @NonNls String family = font.getFamily();
    int size = font.getSize();
    return "<html><style>body { font-family: "
           + family + "; font-size: "
           + size + ";} ul li {list-style-type:circle;}</style>"
           + addPadding(withClosedTag, hPadding) + "</html>";
  }

  public static @NotNull String addPadding(@NotNull String html, int hPadding) {
    return String.format("<p style=\"margin: 0 %dpx 0 %dpx;\">%s</p>", hPadding, hPadding, html);
  }

  /**
   * Please use Application.invokeLater() with a modality state (or ModalityUiUtil, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings. For those, use ModalityUiUtil, application.invoke* or TransactionGuard methods.<p/>
   * <p>
   * On AWT thread, invoked runnable immediately, otherwise do {@link SwingUtilities#invokeLater(Runnable)} on it.
   */
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    EdtInvocationManager.invokeLaterIfNeeded(runnable);
  }

  /**
   * Please use Application.invokeAndWait() with a modality state (or ModalityUiUtil, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings.<p/>
   * <p>
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   * DO NOT INVOKE THIS METHOD FROM UNDER READ ACTION.
   *
   * @param runnable a runnable to invoke
   */
  @ApiStatus.Obsolete
  public static void invokeAndWaitIfNeeded(@NotNull Runnable runnable) {
    EdtInvocationManager.invokeAndWaitIfNeeded(runnable);
  }

  /**
   * Please use Application.invokeAndWait() with a modality state (or ModalityUiUtil, or TransactionGuard methods), unless you work with Swings internals
   * and 'runnable' deals with Swings components only and doesn't access any PSI, VirtualFiles, project/module model or other project settings.<p/>
   * <p>
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   * DO NOT INVOKE THIS METHOD FROM UNDER READ ACTION.
   *
   * @param computable a runnable to invoke
   */
  @ApiStatus.Obsolete
  public static <T> T invokeAndWaitIfNeeded(@NotNull Computable<T> computable) {
    final Ref<T> result = Ref.create();
    EdtInvocationManager.invokeAndWaitIfNeeded(() -> result.set(computable.compute()));
    return result.get();
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
    if (SystemInfoRt.isMac) {
      final int commandKeyMask;
      try {
        commandKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
      }
      catch (HeadlessException e) {
        return;
      }
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

  public static boolean isSelectionButtonDown(@NotNull MouseEvent e) {
    return e.isShiftDown() || e.isControlDown() || e.isMetaDown();
  }

  public static boolean isToggleListSelectionEvent(@NotNull MouseEvent e) {
    return SwingUtilities.isLeftMouseButton(e) && (SystemInfoRt.isMac ? e.isMetaDown() : e.isControlDown()) && !e.isPopupTrigger();
  }

  /**
   * @see JBUI.CurrentTheme.List#rowHeight()
   */
  public static final int LIST_FIXED_CELL_HEIGHT = 20;

  /**
   * The main difference from javax.swing.SwingUtilities#isDescendingFrom(Component, Component) is that this method
   * uses getInvoker() instead of getParent() when it meets JPopupMenu
   *
   * @param child  child component
   * @param parent parent component
   * @return true if parent if a top parent of child, false otherwise
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
    if (!(component instanceof Container container)) return JBIterable.empty();
    return JBIterable.of(container.getComponents());
  }

  public static @NotNull JBTreeTraverser<Component> uiTraverser(@Nullable Component component) {
    return UI_TRAVERSER.withRoot(component).expandAndFilter(o -> !(o instanceof CellRendererPane));
  }

  public static final Key<Iterable<? extends Component>> NOT_IN_HIERARCHY_COMPONENTS = ComponentUtil.NOT_IN_HIERARCHY_COMPONENTS;

  private static final JBTreeTraverser<Component> UI_TRAVERSER = JBTreeTraverser.from((Function<Component, JBIterable<Component>>)c -> {
    if (c instanceof Window window && isDisposed(window)) {
      return JBIterable.empty();
    }
    JBIterable<Component> result;
    if (c instanceof JMenu) {
      result = JBIterable.of(((JMenu)c).getMenuComponents());
    }
    else {
      result = uiChildren(c);
    }
    if (c instanceof JComponent jc) {
      Iterable<? extends Component> orphans = ClientProperty.get(jc, ComponentUtil.NOT_IN_HIERARCHY_COMPONENTS);
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

  private static boolean isDisposed(Window window) {
    Window w = window;
    while (w != null) {
      if (w instanceof DisposableWindow dw && dw.isWindowDisposed()) {
        return true;
      }
      w = w.getOwner();
    }
    return false;
  }

  @ApiStatus.Internal
  public static void addNotInHierarchyComponents(@NotNull JComponent container, @NotNull Iterable<Component> components) {
    Iterable<? extends Component> oldValue = ClientProperty.get(container, NOT_IN_HIERARCHY_COMPONENTS);
    if (oldValue == null) {
      ClientProperty.put(container, NOT_IN_HIERARCHY_COMPONENTS, components);
      return;
    }

    ClientProperty.put(container, NOT_IN_HIERARCHY_COMPONENTS, IterablesConcat.concat(oldValue, components));
  }

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

  public static @NotNull <T extends JComponent> List<T> findComponentsOfType(@Nullable JComponent parent, @NotNull Class<? extends T> cls) {
    return ComponentUtil.findComponentsOfType(parent, cls);
  }

  public static final class TextPainter {
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
    public void draw(final @NotNull Graphics g,
                     @NotNull PairFunction<? super Integer, ? super Integer, ? extends Couple<Integer>> _position) {
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
          if (!Strings.isEmpty(shortcut)) {
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

  public static boolean isDialogRootPane(JRootPane rootPane) {
    if (rootPane != null) {
      final Object isDialog = rootPane.getClientProperty("DIALOG_ROOT_PANE");
      return isDialog instanceof Boolean && ((Boolean)isDialog).booleanValue();
    }
    return false;
  }

  public static void runWhenVisibilityChanged(@NotNull Component component, Runnable runnable) {
    component.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        runnable.run();
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        runnable.run();
      }
    });
  }

  public static @Nullable JComponent mergeComponentsWithAnchor(PanelWithAnchor @NotNull ... panels) {
    return mergeComponentsWithAnchor(Arrays.asList(panels));
  }

  public static @Nullable JComponent mergeComponentsWithAnchor(@NotNull Collection<? extends PanelWithAnchor> panels) {
    return mergeComponentsWithAnchor(panels, false);
  }

  public static @Nullable JComponent mergeComponentsWithAnchor(@NotNull Collection<? extends PanelWithAnchor> panels, boolean visibleOnly) {
    JComponent maxWidthAnchor = null;
    int maxWidth = 0;
    for (PanelWithAnchor panel : panels) {
      if (visibleOnly && (panel instanceof JComponent) && !((JComponent)panel).isVisible()) {
        continue;
      }
      JComponent anchor = panel != null ? panel.getOwnAnchor() : null;
      if (anchor != null) {
        panel.setAnchor(null); // to get own preferred size
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
        if (panel instanceof JComponent) {
          ((JComponent)panel).revalidate();
          ((JComponent)panel).repaint();
        }
      }
    }
    return maxWidthAnchor;
  }

  public static void setNotOpaqueRecursively(@NotNull Component component) {
    setOpaqueRecursively(component, false);
  }

  public static void setOpaqueRecursively(@NotNull Component component, boolean opaque) {
    if (!(component instanceof JComponent)) {
      return;
    }
    forEachComponentInHierarchy(component, c -> {
      if (c instanceof JComponent) {
        ((JComponent)c).setOpaque(opaque);
      }
    });
  }

  public static void setEnabledRecursively(@NotNull Component component, boolean enabled) {
    forEachComponentInHierarchy(component, c -> {
      c.setEnabled(enabled);
    });
  }

  public static void setBackgroundRecursively(@NotNull Component component, @NotNull Color bg) {
    forEachComponentInHierarchy(component, c -> c.setBackground(bg));
  }

  public static void setForegroundRecursively(@NotNull Component component, @NotNull Color bg) {
    forEachComponentInHierarchy(component, c -> c.setForeground(bg));
  }

  public static void setTooltipRecursively(@NotNull Component component, @Nls String text) {
    forEachComponentInHierarchy(component, c -> ((JComponent)c).setToolTipText(text));
  }

  public static void forEachComponentInHierarchy(@NotNull Component component, @NotNull Consumer<? super Component> action) {
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

  public static int getLcdContrastValue() {
    return StartupUiUtil.getLcdContrastValue();
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
    if (!SystemInfoRt.isMac) {
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
    StringBuilder builder = new StringBuilder();
    getAllTextsRecursively(c, builder);
    return builder.toString();
  }

  private static void getAllTextsRecursively(@NotNull Component component, @NotNull StringBuilder builder) {
    String candidate = "";
    if (component instanceof JLabel) candidate = ((JLabel)component).getText();
    if (component instanceof JTextComponent) candidate = ((JTextComponent)component).getText();
    if (component instanceof AbstractButton) candidate = ((AbstractButton)component).getText();
    if (Strings.isNotEmpty(candidate)) {
      candidate = candidate.replaceAll("<a href=\"#inspection/[^)]+\\)", "");
      if (!builder.isEmpty()) {
        builder.append(' ');
      }
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

  /**
   * Use {@link SwingUndoUtil#addUndoRedoActions(JTextComponent)}
   *
   * @param textComponent
   */
  @Deprecated
  public static void addUndoRedoActions(@NotNull JTextComponent textComponent) {
    SwingUndoUtil.addUndoRedoActions(textComponent);
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

  public static @NlsSafe @NotNull String leftArrow() {
    return FontUtil.leftArrow(StartupUiUtil.getLabelFont());
  }

  public static @NlsSafe @NotNull String rightArrow() {
    return FontUtil.rightArrow(StartupUiUtil.getLabelFont());
  }

  public static @NlsSafe @NotNull String upArrow(@NotNull String defaultValue) {
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
    if (component instanceof Container container) {
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
    return getLineHeight((JComponent)textComponent);
  }

  public static int getLineHeight(@NotNull JComponent component) {
    return component.getFontMetrics(component.getFont()).getHeight();
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
    if (!component.isCursorSet() || component.getCursor() != cursor) {
      component.setCursor(cursor);
    }
  }

  public static boolean haveCommonOwner(Component c1, Component c2) {
    if (c1 == null || c2 == null) return false;
    Window c1Ancestor = findWindowAncestor(c1);
    Window c2Ancestor = findWindowAncestor(c2);

    Set<Window> ownerSet = new HashSet<>();

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

  public static boolean isSimpleWindow(Window window) {
    return window != null && !(window instanceof Frame || window instanceof Dialog);
  }

  public static boolean isHelpButton(Component button) {
    return button instanceof JButton && "help".equals(((JComponent)button).getClientProperty("JButton.buttonType"));
  }

  public static boolean isRetina(@NotNull GraphicsDevice device) {
    return DetectRetinaKit.isOracleMacRetinaDevice(device);
  }

  /**
   * Employs a common pattern to use {@code Graphics}. This is a non-distractive approach
   * all modifications on {@code Graphics} are metter only inside the {@code Consumer} block
   *
   * @param originGraphics  graphics to work with
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


  // List

  public static @NotNull Font getListFont() {
    Font font = UIManager.getFont("List.font");
    return font != null ? font : StartupUiUtil.getLabelFont();
  }

  // background

  /**
   * @see RenderingUtil#getBackground(JList)
   */
  public static @NotNull Color getListBackground() {
    return JBUI.CurrentTheme.List.BACKGROUND;
  }

  /**
   * @see RenderingUtil#getSelectionBackground(JList)
   */
  public static @NotNull Color getListSelectionBackground(boolean focused) {
    return JBUI.CurrentTheme.List.Selection.background(focused);
  }

  public static @NotNull Dimension updateListRowHeight(@NotNull Dimension size) {
    size.height = Math.max(size.height, UIManager.getInt("List.rowHeight"));
    return size;
  }

  /**
   * @see RenderingUtil#getBackground(JList, boolean)
   */
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
  @Deprecated(forRemoval = true)
  public static @NotNull Color getListUnfocusedSelectionBackground() {
    return getListSelectionBackground(false);
  }

  // foreground

  /**
   * @see RenderingUtil#getForeground(JList)
   */
  public static @NotNull Color getListForeground() {
    return JBUI.CurrentTheme.List.FOREGROUND;
  }

  /**
   * @see RenderingUtil#getSelectionForeground(JList)
   */
  public static @NotNull Color getListSelectionForeground(boolean focused) {
    return NamedColorUtil.getListSelectionForeground(focused);
  }

  /**
   * @see RenderingUtil#getForeground(JList, boolean)
   */
  public static @NotNull Color getListForeground(boolean selected, boolean focused) {
    return !selected ? getListForeground() : NamedColorUtil.getListSelectionForeground(focused);
  }

  /**
   * @deprecated use {@link #getListForeground(boolean, boolean)}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull Color getListForeground(boolean selected) {
    return getListForeground(selected, true);
  }

  /**
   * @deprecated use {@link #getListSelectionForeground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getListSelectionForeground() {
    return NamedColorUtil.getListSelectionForeground(true);
  }

  // Tree

  public static @NotNull Font getTreeFont() {
    Font font = UIManager.getFont("Tree.font");
    return font != null ? font : StartupUiUtil.getLabelFont();
  }

  // background

  /**
   * @see RenderingUtil#getBackground(JTree)
   */
  public static @NotNull Color getTreeBackground() {
    return JBUI.CurrentTheme.Tree.BACKGROUND;
  }

  /**
   * @see RenderingUtil#getSelectionBackground(JTree)
   */
  public static @NotNull Color getTreeSelectionBackground(boolean focused) {
    return JBUI.CurrentTheme.Tree.Selection.background(focused);
  }

  /**
   * @see RenderingUtil#getBackground(JTree, boolean)
   */
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

  /**
   * @see RenderingUtil#getForeground(JTree)
   */
  public static @NotNull Color getTreeForeground() {
    return JBUI.CurrentTheme.Tree.FOREGROUND;
  }

  /**
   * @see RenderingUtil#getSelectionForeground(JTree)
   */
  public static @NotNull Color getTreeSelectionForeground(boolean focused) {
    return JBUI.CurrentTheme.Tree.Selection.foreground(focused);
  }

  /**
   * @see RenderingUtil#getForeground(JTree, boolean)
   */
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

  /**
   * @see RenderingUtil#getBackground(JTable)
   */
  public static @NotNull Color getTableBackground() {
    return JBUI.CurrentTheme.Table.BACKGROUND;
  }

  /**
   * @see RenderingUtil#getSelectionBackground(JTable)
   */
  public static @NotNull Color getTableSelectionBackground(boolean focused) {
    return JBUI.CurrentTheme.Table.Selection.background(focused);
  }

  /**
   * @see RenderingUtil#getBackground(JTable, boolean)
   */
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

  // foreground

  /**
   * @see RenderingUtil#getForeground(JTable)
   */
  public static @NotNull Color getTableForeground() {
    return JBUI.CurrentTheme.Table.FOREGROUND;
  }

  /**
   * @see RenderingUtil#getSelectionForeground(JTable)
   */
  public static @NotNull Color getTableSelectionForeground(boolean focused) {
    return JBUI.CurrentTheme.Table.Selection.foreground(focused);
  }

  /**
   * @see RenderingUtil#getForeground(JTable, boolean)
   */
  public static @NotNull Color getTableForeground(boolean selected, boolean focused) {
    return !selected ? getTableForeground() : getTableSelectionForeground(focused);
  }

  /**
   * @deprecated use {@link #getTableForeground(boolean, boolean)}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull Color getTableForeground(boolean selected) {
    return getTableForeground(selected, true);
  }

  /**
   * @deprecated use {@link #getTableSelectionForeground(boolean)}
   */
  @Deprecated
  public static @NotNull Color getTableSelectionForeground() {
    return getTableSelectionForeground(true);
  }

  public static void doNotScrollToCaret(@NotNull JTextComponent textComponent) {
    textComponent.setCaret(new DefaultCaret() {
      @Override
      protected void adjustVisibility(Rectangle nloc) { }
    });
  }

  public static void convertToLabel(@NotNull JEditorPane editorPane) {
    editorPane.setEditable(false);
    editorPane.setFocusable(false);
    editorPane.setOpaque(false);
    editorPane.setBorder(null);
    editorPane.setContentType("text/html");
    editorPane.setEditorKit(HTMLEditorKitBuilder.simple());
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
  public static void scrollToReference(@NotNull JEditorPane editor, @NotNull @NonNls String reference) {
    Document document = editor.getDocument();
    if (document instanceof HTMLDocument) {
      Element elementById = ((HTMLDocument)document).getElement(reference);
      if (elementById != null) {
        try {
          int pos = elementById.getStartOffset();
          Rectangle r = editor.modelToView(pos);
          if (r != null) {
            r.height = editor.getVisibleRect().height;
            editor.scrollRectToVisible(r);
            editor.setCaretPosition(pos);
          }
        }
        catch (BadLocationException e) {
          getLogger().error(e);
        }
        return;
      }
    }
    editor.scrollToReference(reference);
  }

  /**
   * Checks if a component is showing in a more general sense than UI visibility,
   * sometimes it's useful to limit various activities by checking the visibility of the real UI,
   * but it could be unneeded in headless mode or in other scenarios.
   *
   * @see UIUtil#hasFocus(Component)
   */
  @ApiStatus.Experimental
  public static boolean isShowing(@NotNull Component component) {
    return isShowing(component, true);
  }

  /**
   * An overload of {@link UIUtil#isShowing(Component)} allowing to ignore headless mode.
   *
   * @param checkHeadless when {@code true}, the {@code component} will always be considered visible in headless mode.
   */
  @ApiStatus.Experimental
  public static boolean isShowing(@NotNull Component component, boolean checkHeadless) {
    return ComponentUtil.isShowing(component, checkHeadless);
  }

  /**
   * Marks a component as showing
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void markAsShowing(@NotNull JComponent component, boolean value) {
    ComponentUtil.markAsShowing(component, value);
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

  public static void runWhenHidden(@NotNull Component component, @NotNull Runnable runnable) {
    component.addHierarchyListener(runWhenHidden(runnable));
  }

  private static @NotNull HierarchyListener runWhenHidden(@NotNull Runnable runnable) {
    return new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if (!BitUtil.isSet(e.getChangeFlags(), HierarchyEvent.DISPLAYABILITY_CHANGED)) {
          return;
        }
        Component component = e.getComponent();
        if (component.isDisplayable()) {
          return;
        }
        component.removeHierarchyListener(this);
        runnable.run();
      }
    };
  }

  public static void runWhenChanged(@NotNull Component component, @NotNull String property, @NotNull Runnable runnable) {
    component.addPropertyChangeListener(property, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        component.removePropertyChangeListener(property, this);
        runnable.run();
      }
    });
  }

  public static Future<?> runOnceWhenResized(@NotNull Component component, @NotNull Runnable runnable) {
    var future = new CompletableFuture<>();
    component.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        component.removeComponentListener(this);
        if (!future.isCancelled()) {
          try {
            runnable.run();
            future.complete(null);
          }
          catch (Throwable ex) {
            future.completeExceptionally(ex);
          }
        }
      }
    });
    return future;
  }

  public static Font getLabelFont() {
    return StartupUiUtil.getLabelFont();
  }

  public static void drawImage(@NotNull Graphics g, @NotNull Image image, int x, int y, @Nullable ImageObserver observer) {
    StartupUiUtil.drawImage(g, image, x, y, observer);
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
    StartupUiUtilKt.drawImage(g, image, dstBounds, srcBounds, null, observer);
  }

  @TestOnly
  public static void pump() {
    StartupUiUtil.INSTANCE.pump();
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

  public static void stopFocusedEditing(@NotNull Window window) {
    Component focusOwner = FocusManager.getCurrentManager().getFocusOwner();
    if (focusOwner == null || !SwingUtilities.isDescendingFrom(focusOwner, window)) {
      return;
    }
    if (focusOwner instanceof JFormattedTextField) {
      try {
        ((JFormattedTextField)focusOwner).commitEdit();
      }
      catch (ParseException ignored) {
      }
    }
    Object obj = focusOwner.getParent();
    if (obj instanceof JTable) {
      TableUtil.stopEditing((JTable)obj);
    }
    Object obj1 = focusOwner.getParent();
    if (obj1 instanceof JTree) {
      ((JTree)obj1).stopEditing();
    }
  }

  public static String colorToHex(final Color color) {
    return to2DigitsHex(color.getRed()) + to2DigitsHex(color.getGreen()) + to2DigitsHex(color.getBlue());
  }

  private static String to2DigitsHex(int i) {
    String s = Integer.toHexString(i);
    if (s.length() < 2) s = "0" + s;
    return s;
  }

  public static boolean isXServerOnWindows() {
    // This is heuristics to detect using Cygwin/X or other build of X.Org server on Windows in a WSL 2 environment
    return SystemInfoRt.isUnix && !SystemInfoRt.isMac && !SystemInfo.isWayland && System.getenv("WSLENV") != null;
  }

  public static void applyDeprecatedBackground(@Nullable JComponent component) {
    Color color = getDeprecatedBackground();
    if (component != null && color != null) {
      component.setBackground(color);
      component.setOpaque(true);
    }
  }

  @ApiStatus.Internal
  public static @Nullable Color getDeprecatedBackground() {
    return Registry.getColor("ui.deprecated.components.color", null);
  }

  public static boolean isMetalRendering() {
    return SystemInfo.isMac && Boolean.getBoolean("sun.java2d.metal");
  }

  public static boolean isFullScreenSupportedByDefaultGD() {
    GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    return gd.isFullScreenSupported();
  }

  private static boolean DISABLE_LAYOUT_IN_TEXT_COMPONENTS = false;

  @ApiStatus.Internal
  public static void disableLayoutInTextComponents() {
    DISABLE_LAYOUT_IN_TEXT_COMPONENTS = true;
  }

  /**
   * Disables performing text layout for 'complex' text in the document, if configured globally.
   * Should be called before the document is used for anything, i.e., right after construction.
   */
  @ApiStatus.Internal
  public static void disableTextLayoutIfNeeded(@NotNull Document document) {
    if (DISABLE_LAYOUT_IN_TEXT_COMPONENTS && document instanceof AbstractDocument ad) {
      ad.setDocumentProperties(new Hashtable<>(2) {
        @Override
        public synchronized Object get(Object key) {
          return "i18n".equals(key) ? Boolean.FALSE : super.get(key);
        }
      });
    }
  }
}
