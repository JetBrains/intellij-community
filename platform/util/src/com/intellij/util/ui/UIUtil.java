/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.ProgressBarUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

/**
 * @author max
 */
@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public class UIUtil {

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
      c.putClientProperty("JComponent.sizeVariant",
                          componentStyle == ComponentStyle.REGULAR ? "regular" : componentStyle == ComponentStyle.SMALL ? "small" : "mini");
    }
    else {
      c.setFont(getFont(
        componentStyle == ComponentStyle.REGULAR
        ? FontSize.NORMAL
        : componentStyle == ComponentStyle.SMALL ? FontSize.SMALL : FontSize.MINI, c.getFont()));
    }
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

  private static final GrayFilter DEFAULT_GRAY_FILTER = new GrayFilter(true, 65);
  private static final GrayFilter DARCULA_GRAY_FILTER = new GrayFilter(true, 30);

  public static GrayFilter getGrayFilter() {
    return isUnderDarcula() ? DARCULA_GRAY_FILTER : DEFAULT_GRAY_FILTER;
  }

  public static boolean isAppleRetina() {
    return isRetina() && SystemInfo.isAppleJvm;
  }

  public enum FontSize {NORMAL, SMALL, MINI}

  public enum ComponentStyle {REGULAR, SMALL, MINI}

  public enum FontColor {NORMAL, BRIGHTER}

  public static final char MNEMONIC = BundleBase.MNEMONIC;
  @NonNls public static final String HTML_MIME = "text/html";
  @NonNls public static final String JSLIDER_ISFILLED = "JSlider.isFilled";
  @NonNls public static final String ARIAL_FONT_NAME = "Arial";
  @NonNls public static final String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";
  @NonNls public static final String CENTER_TOOLTIP_DEFAULT = "ToCenterTooltip";
  @NonNls public static final String CENTER_TOOLTIP_STRICT = "ToCenterTooltip.default";

  public static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("<\\s*([^<>/ ]+)([^<>]*)/\\s*>", Pattern.CASE_INSENSITIVE);

  @NonNls public static final String FOCUS_PROXY_KEY = "isFocusProxy";

  public static Key<Integer> KEEP_BORDER_SIDES = Key.create("keepBorderSides");

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.UIUtil");

  private static final Color UNFOCUSED_SELECTION_COLOR = Gray._212;
  private static final Color ACTIVE_HEADER_COLOR = new Color(160, 186, 213);
  private static final Color INACTIVE_HEADER_COLOR = Gray._128;
  private static final Color BORDER_COLOR = Color.LIGHT_GRAY;

  public static final Color AQUA_SEPARATOR_FOREGROUND_COLOR = Gray._190;
  public static final Color AQUA_SEPARATOR_BACKGROUND_COLOR = Gray._240;
  public static final Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);

  public static final int DEFAULT_HGAP = 10;
  public static final int DEFAULT_VGAP = 4;
  public static final int LARGE_VGAP = 12;

  public static final Insets PANEL_REGULAR_INSETS = new Insets(8, 12, 8, 12);
  public static final Insets PANEL_SMALL_INSETS = new Insets(5, 8, 5, 8);


  public static final Border DEBUG_MARKER_BORDER = new Border() {
    private final Insets empty = new Insets(0, 0, 0, 0);

    @Override
    public Insets getBorderInsets(Component c) {
      return empty;
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

  // accessed only from EDT
  private static final HashMap<Color, BufferedImage> ourAppleDotSamples = new HashMap<Color, BufferedImage>();

  private static volatile Pair<String, Integer> ourSystemFontData = null;

  @NonNls private static final String ROOT_PANE = "JRootPane.future";

  private static final Ref<Boolean> ourRetina = Ref.create(SystemInfo.isMac ? null : false);

  private UIUtil() {
  }

  public static boolean isRetina() {
    synchronized (ourRetina) {
      if (ourRetina.isNull()) {
        ourRetina.set(false); // in case HiDPIScaledImage.drawIntoImage is not called for some reason

        if (SystemInfo.isJavaVersionAtLeast("1.6.0_33") && SystemInfo.isAppleJvm) {
          if (!"false".equals(System.getProperty("ide.mac.retina"))) {
            ourRetina.set(IsRetina.isRetina());
            return ourRetina.get();
          }
        } else if (SystemInfo.isJavaVersionAtLeast("1.7.0_40") && SystemInfo.isOracleJvm) {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            final GraphicsDevice device = env.getDefaultScreenDevice();
            try {
              Field field = device.getClass().getDeclaredField("scale");
              if (field != null) {
                field.setAccessible(true);
                Object scale = field.get(device);
                if (scale instanceof Integer && ((Integer)scale).intValue() == 2) {
                  ourRetina.set(true);
                  return true;
                }
              }
            }
            catch (Exception ignore) {
            }
        }
        ourRetina.set(false);
      }

      return ourRetina.get();
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

  public static String getHtmlBody(String text) {
    return getHtmlBody(new Html(text));
  }

  public static String getHtmlBody(Html html) {
    String text = html.getText();
    String result;
    if (!text.startsWith("<html>")) {
      result = text.replaceAll("\n", "<br>");
    }
    else {
      final int bodyIdx = text.indexOf("<body>");
      final int closedBodyIdx = text.indexOf("</body>");
      if (bodyIdx != -1 && closedBodyIdx != -1) {
        result = text.substring(bodyIdx + "<body>".length(), closedBodyIdx);
      }
      else {
        text = StringUtil.trimStart(text, "<html>").trim();
        text = StringUtil.trimEnd(text, "</html>").trim();
        text = StringUtil.trimStart(text, "<body>").trim();
        text = StringUtil.trimEnd(text, "</body>").trim();
        result = text;
      }
    }

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

  public static void setEnabled(Component component, boolean enabled, boolean recursively) {
    component.setEnabled(enabled);
    if (component instanceof JLabel) {
      Color color = enabled ? getLabelForeground() : getLabelDisabledForeground();
      if (color != null) {
        component.setForeground(color);
      }
    }
    if (recursively && enabled == component.isEnabled()) {
      if (component instanceof Container) {
        final Container container = (Container)component;
        final int subComponentCount = container.getComponentCount();
        for (int i = 0; i < subComponentCount; i++) {
          setEnabled(container.getComponent(i), enabled, recursively);
        }
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

      String s = currentLine + currentAtom.toString();
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

    String s = currentLine + currentAtom.toString();
    if (!s.isEmpty()) {
      lines.add(s);
    }

    return ArrayUtil.toStringArray(lines);
  }

  public static void setActionNameAndMnemonic(String text, Action action) {
    int mnemoPos = text.indexOf('&');
    if (mnemoPos >= 0 && mnemoPos < text.length() - 2) {
      String mnemoChar = text.substring(mnemoPos + 1, mnemoPos + 2).trim();
      if (mnemoChar.length() == 1) {
        action.putValue(Action.MNEMONIC_KEY, Integer.valueOf((int)mnemoChar.charAt(0)));
      }
    }

    text = text.replaceAll("&", "");
    action.putValue(Action.NAME, text);
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
        return Math.max(defSize - 2f, 11f);
      case MINI:
        return Math.max(defSize - 4f, 9f);
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

  /** @deprecated to remove in IDEA 14 */
  @SuppressWarnings("UnusedDeclaration")
  public static Icon getOptionPanelWarningIcon() {
    return getWarningIcon();
  }

  /** @deprecated to remove in IDEA 14 */
  @SuppressWarnings("UnusedDeclaration")
  public static Icon getOptionPanelQuestionIcon() {
    return getQuestionIcon();
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
    return isUnderDarcula() ? Gray._52 : UNFOCUSED_SELECTION_COLOR;
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
    return isUnderGTKLookAndFeel() ? UIManager.getColor("EditorPane.background") : UIManager.getColor("TextField.background");
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

  public static Icon getErrorIcon() {
    return UIManager.getIcon("OptionPane.errorIcon");
  }

  public static Icon getInformationIcon() {
    return UIManager.getIcon("OptionPane.informationIcon");
  }

  public static Icon getQuestionIcon() {
    return UIManager.getIcon("OptionPane.questionIcon");
  }

  public static Icon getWarningIcon() {
    return UIManager.getIcon("OptionPane.warningIcon");
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
    boolean white = (selected && focused) || isUnderDarcula();

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
    return isUnderAquaBasedLookAndFeel() || isUnderNimbusLookAndFeel() || isUnderGTKLookAndFeel() || isUnderDarcula() || isUnderIntelliJLaF()
           ? AllIcons.Mac.Tree_white_right_arrow : getTreeCollapsedIcon();
  }

  public static Icon getTreeSelectedExpandedIcon() {
    return isUnderAquaBasedLookAndFeel() || isUnderNimbusLookAndFeel() || isUnderGTKLookAndFeel() || isUnderDarcula() || isUnderIntelliJLaF()
           ? AllIcons.Mac.Tree_white_down_arrow : getTreeExpandedIcon();
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderAlloyLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Alloy");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderAlloyIDEALookAndFeel() {
    return isUnderAlloyLookAndFeel() && UIManager.getLookAndFeel().getName().contains("IDEA");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderWindowsLookAndFeel() {
    return UIManager.getLookAndFeel().getName().equals("Windows");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderWindowsClassicLookAndFeel() {
    return UIManager.getLookAndFeel().getName().equals("Windows Classic");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderNimbusLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Nimbus");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderAquaLookAndFeel() {
    return SystemInfo.isMac && UIManager.getLookAndFeel().getName().contains("Mac OS X");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderJGoodiesLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("JGoodies");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderAquaBasedLookAndFeel() {
    return SystemInfo.isMac && (isUnderAquaLookAndFeel() || isUnderDarcula());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderDarcula() {
    return UIManager.getLookAndFeel().getName().contains("Darcula");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderIntelliJLaF() {
    return UIManager.getLookAndFeel().getName().contains("IntelliJ");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderGTKLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("GTK");
  }

  public static final Color GTK_AMBIANCE_TEXT_COLOR = new Color(223, 219, 210);
  public static final Color GTK_AMBIANCE_BACKGROUND_COLOR = new Color(67, 66, 63);

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isMurrineBasedTheme() {
    final String gtkTheme = getGtkThemeName();
    return "Ambiance".equalsIgnoreCase(gtkTheme) ||
           "Radiance".equalsIgnoreCase(gtkTheme) ||
           "Dust".equalsIgnoreCase(gtkTheme) ||
           "Dust Sand".equalsIgnoreCase(gtkTheme);
  }

  public static Color shade(final Color c, final double factor, final double alphaFactor) {
    assert factor >= 0 : factor;
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

    for (i1 = i1 != x1 + 1 ? y + 2 : y + 1; i1 <= y1; i1 += 2) {
      drawLine(g, x1, i1, x1, i1);
    }

    for (i1 = i1 != y1 + 1 ? x1 - 2 : x1 - 1; i1 >= x; i1 -= 2) {
      drawLine(g, i1, y1, i1, y1);
    }

    for (i1 = i1 != x - 1 ? y1 - 2 : y1 - 1; i1 >= y; i1 -= 2) {
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
    if ((SystemInfo.isMac && !isRetina()) || SystemInfo.isLinux) {
      drawAppleDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
    else {
      drawBoringDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
  }

  public static void drawSearchMatch(final Graphics2D g,
                                     final int startX,
                                     final int endX,
                                     final int height) {
    Color c1 = new Color(255, 234, 162);
    Color c2 = new Color(255, 208, 66);
    drawSearchMatch(g, startX, endX, height, c1, c2);
  }

  public static void drawSearchMatch(Graphics2D g, int startX, int endX, int height, Color c1, Color c2) {
    final boolean drawRound = endX - startX > 4;

    final Composite oldComposite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
    g.setPaint(getGradientPaint(startX, 2, c1, startX, height - 5, c2));

    if (isRetina()) {
      g.fillRoundRect(startX - 1, 2, endX - startX + 1, height - 4, 5, 5);
      g.setComposite(oldComposite);
      return;
    }

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

    g.setComposite(oldComposite);
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
    g.setColor(getPanelBackground());
    g.fillRect(x, 0, width, height);

    ((Graphics2D)g).setPaint(getGradientPaint(0, 0, new Color(0, 0, 0, 5), 0, height, new Color(0, 0, 0, 20)));
    g.fillRect(x, 0, width, height);

    g.setColor(new Color(0, 0, 0, toolWindow ? 90 : 50));
    if (drawTopLine) g.drawLine(x, 0, width, 0);
    if (drawBottomLine) g.drawLine(x, height - 1, width, height - 1);

    g.setColor(isUnderDarcula() ? Gray._255.withAlpha(30) : new Color(255, 255, 255, 100));
    g.drawLine(x, drawTopLine ? 1 : 0, width, drawTopLine ? 1 : 0);

    if (active) {
      g.setColor(new Color(100, 150, 230, toolWindow ? 50 : 30));
      g.fillRect(x, 0, width, height);
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

    // Draw apple like dotted line:
    //
    // CCC CCC CCC ...
    // CCC CCC CCC ...
    // CCC CCC CCC ...
    //
    // (where "C" - colored pixel, " " - white pixel)

    final int step = 4;
    final int startPosCorrection = startX % step < 3 ? 0 : 1;

    // Optimization - lets draw dotted line using dot sample image.

    // draw one dot by pixel:

    // save old settings
    final Composite oldComposite = g.getComposite();
    // draw image "over" on top of background
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

    // sample
    final BufferedImage image = getAppleDotStamp(fgColor, oldColor);

    // Now copy our dot several times
    final int dotX0 = (startX / step + startPosCorrection) * step;
    for (int dotXi = dotX0; dotXi < endX; dotXi += step) {
      g.drawImage(image, dotXi, lineY, null);
    }

    //restore previous settings
    g.setComposite(oldComposite);
  }

  private static BufferedImage getAppleDotStamp(final Color fgColor,
                                                final Color oldColor) {
    final Color color = fgColor != null ? fgColor : oldColor;

    // let's avoid of generating tons of GC and store samples for different colors
    BufferedImage sample = ourAppleDotSamples.get(color);
    if (sample == null) {
      sample = createAppleDotStamp(color);
      ourAppleDotSamples.put(color, sample);
    }
    return sample;
  }

  private static BufferedImage createAppleDotStamp(final Color color) {
    final BufferedImage image = createImage(3, 3, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = image.createGraphics();

    g.setColor(color);

    // Each dot:
    // | 20%  | 50%  | 20% |
    // | 80%  | 80%  | 80% |
    // | 50%  | 100% | 50% |

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .2f));
    g.drawLine(0, 0, 0, 0);
    g.drawLine(2, 0, 2, 0);

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 0.7f));
    g.drawLine(0, 1, 2, 1);

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 1.0f));
    g.drawLine(1, 2, 1, 2);

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .5f));
    g.drawLine(1, 0, 1, 0);
    g.drawLine(0, 2, 0, 2);
    g.drawLine(2, 2, 2, 2);

    // dispose graphics
    g.dispose();

    return image;
  }

  public static void applyRenderingHints(final Graphics g) {
    Toolkit tk = Toolkit.getDefaultToolkit();
    //noinspection HardCodedStringLiteral
    Map map = (Map)tk.getDesktopProperty("awt.font.desktophints");
    if (map != null) {
      ((Graphics2D)g).addRenderingHints(map);
    }
  }


  public static BufferedImage createImage(int width, int height, int type) {
    if (isRetina()) {
      return RetinaImage.create(width, height, type);
    }
    //noinspection UndesirableClassUsage
    return new BufferedImage(width, height, type);
  }

  public static void drawImage(Graphics g, Image image, int x, int y, ImageObserver observer) {
    if (image instanceof JBHiDPIScaledImage) {
      final Graphics2D newG = (Graphics2D)g.create(x, y, image.getWidth(observer), image.getHeight(observer));
      newG.scale(0.5, 0.5);
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img == null) {
        img = image;
      }
      newG.drawImage(img, 0, 0, observer);
      newG.scale(1, 1);
      newG.dispose();
    } else {
      g.drawImage(image, x, y, observer);
    }
  }

  public static void drawImage(Graphics g, BufferedImage image, BufferedImageOp op, int x, int y) {
    if (image instanceof JBHiDPIScaledImage) {
      final Graphics2D newG = (Graphics2D)g.create(x, y, image.getWidth(null), image.getHeight(null));
      newG.scale(0.5, 0.5);
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img == null) {
        img = image;
      }
      newG.drawImage((BufferedImage)img, op, 0, 0);
      newG.scale(1, 1);
      newG.dispose();
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
    if (!useRetinaCondition || !isRetina() || Registry.is("ide.mac.retina.disableDrawingFix", false)) {
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

  @TestOnly
  public static void dispatchAllInvocationEvents() {
    assert SwingUtilities.isEventDispatchThread() : Thread.currentThread();
    final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    while (true) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      try {
        AWTEvent event1 = eventQueue.getNextEvent();
        if (event1 instanceof InvocationEvent) {
          ((InvocationEvent)event1).dispatch();
        }
      }
      catch (Exception e) {
        LOG.error(e); //?
      }
    }
  }

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
    for (int i = x - 1; i <= x + 1; i++) {
      for (int j = y - 1; j <= y + 1; j++) {
        g.drawString(s, i, j);
      }
    }
    g.setColor(foreground);
    g.drawString(s, x, y);
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
  public static Color getBgFillColor(@NotNull JComponent c) {
    final Component parent = findNearestOpaque(c);
    return parent == null ? c.getBackground() : parent.getBackground();
  }

  @Nullable
  public static Component findNearestOpaque(JComponent c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent.isOpaque()) return eachParent;
      eachParent = eachParent.getParent();
    }

    return null;
  }

  @NonNls
  public static String getCssFontDeclaration(final Font font) {
    return getCssFontDeclaration(font, null, null, null);
  }

  @Language("HTML")
  @NonNls
  public static String getCssFontDeclaration(final Font font, @Nullable Color fgColor, @Nullable Color linkColor, @Nullable String liImg) {
    URL resource = liImg != null ? SystemInfo.class.getResource(liImg) : null;

    @NonNls String fontFamilyAndSize = "font-family:" + font.getFamily() + "; font-size:" + font.getSize() + ";";
    @NonNls @Language("HTML")
    String body = "body, div, td, p {" + fontFamilyAndSize + " " + (fgColor != null ? "color:" + ColorUtil.toHex(fgColor) : "") + "}";
    if (resource != null) {
      body += "ul {list-style-image: " + resource.toExternalForm() + "}";
    }
    @NonNls String link = linkColor != null ? "a {" + fontFamilyAndSize + " color:" + ColorUtil.toHex(linkColor) + "}" : "";
    return "<style> " + body + " " + link + "</style>";
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
  }

  public static void disposeProgress(final JProgressBar progress) {
    if (!isUnderNativeMacLookAndFeel()) return;

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (isToDispose(progress)) {
          progress.getUI().uninstallUI(progress);
          progress.putClientProperty("isDisposed", Boolean.TRUE);
        }
      }
    });
  }

  private static boolean isToDispose(final JProgressBar progress) {
    final ProgressBarUI ui = progress.getUI();

    if (ui == null) return false;
    if (Boolean.TYPE.equals(progress.getClientProperty("isDisposed"))) return false;

    try {
      final Field progressBarField = ReflectionUtil.findField(ui.getClass(), JProgressBar.class, "progressBar");
      progressBarField.setAccessible(true);
      return progressBarField.get(ui) != null;
    }
    catch (NoSuchFieldException e) {
      return true;
    }
    catch (IllegalAccessException e) {
      return true;
    }
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

  public static Color getBorderColor() {
    return isUnderDarcula() ? Gray._50 : BORDER_COLOR;
  }

  public static Font getTitledBorderFont() {
    Font defFont = getLabelFont();
    return defFont.deriveFont(Math.max(defFont.getSize() - 2f, 11f));
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

  public static HTMLEditorKit getHTMLEditorKit() {
    final HTMLEditorKit kit = new HTMLEditorKit();

    Font font = getLabelFont();
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : 11;

    final StyleSheet styleSheet = kit.getStyleSheet();
    styleSheet.addRule(String.format("body, div, p { font-family: %s; font-size: %s; } p { margin-top: 0; }", family, size));
    kit.setStyleSheet(styleSheet);

    return kit;
  }

  public static void removeScrollBorder(final Component c) {
    new AwtVisitor(c) {
      @Override
      public boolean visit(final Component component) {
        if (component instanceof JScrollPane) {
          if (!hasNonPrimitiveParents(c, component)) {
            final JScrollPane scrollPane = (JScrollPane)component;
            Integer keepBorderSides = (Integer)scrollPane.getClientProperty(KEEP_BORDER_SIDES);
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
        return false;
      }
    };
  }

  public static boolean hasNonPrimitiveParents(Component stopParent, Component c) {
    Component eachParent = c.getParent();
    while (true) {
      if (eachParent == null || eachParent == stopParent) return false;
      if (!isPrimitive(eachParent)) return true;
      eachParent = eachParent.getParent();
    }
  }

  public static boolean isPrimitive(Component c) {
    return c instanceof JPanel || c instanceof JLayeredPane;
  }

  public static Point getCenterPoint(Dimension container, Dimension child) {
    return getCenterPoint(new Rectangle(new Point(), container), child);
  }

  public static Point getCenterPoint(Rectangle container, Dimension child) {
    Point result = new Point();

    Point containerLocation = container.getLocation();
    Dimension containerSize = container.getSize();

    result.x = containerLocation.x + containerSize.width / 2 - child.width / 2;
    result.y = containerLocation.y + containerSize.height / 2 - child.height / 2;

    return result;
  }

  public static String toHtml(String html) {
    return toHtml(html, 0);
  }

  @NonNls
  public static String toHtml(String html, final int hPadding) {
    html = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
    Font font = getLabelFont();
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : 11;
    return "<html><style>body { font-family: "
           + family + "; font-size: "
           + size + ";} ul li {list-style-type:circle;}</style>"
           + addPadding(html, hPadding) + "</html>";
  }

  public static String addPadding(final String html, int hPadding) {
    return String.format("<p style=\"margin: 0 %dpx 0 %dpx;\">%s</p>", hPadding, hPadding, html);
  }

  public static String convertSpace2Nbsp(String html) {
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

  public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  /**
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   *
   * @param runnable a runnable to invoke
   * @see #invokeAndWaitIfNeeded(com.intellij.util.ThrowableRunnable)
   */
  public static void invokeAndWaitIfNeeded(@NotNull Runnable runnable) {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      try {
        SwingUtilities.invokeAndWait(runnable);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public static void invokeAndWaitIfNeeded(@NotNull final ThrowableRunnable runnable) throws Throwable {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      final Ref<Throwable> ref = new Ref<Throwable>();
      SwingUtilities.invokeAndWait(new Runnable() {
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

  public static void initDefaultLAF() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

      if (ourSystemFontData == null) {
        final Font font = getLabelFont();
        ourSystemFontData = Pair.create(font.getName(), font.getSize());
      }
    }
    catch (Exception ignored) { }
  }

  @Nullable
  public static Pair<String, Integer> getSystemFontData() {
    return ourSystemFontData;
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
    final Component editorComponent = comboBox.getEditor().getEditorComponent();
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
  public static ComboPopup getComboBoxPopup(JComboBox comboBox) {
    final ComboBoxUI ui = comboBox.getUI();
    if (ui instanceof BasicComboBoxUI) {
      try {
        final Field popup = BasicComboBoxUI.class.getDeclaredField("popup");
        popup.setAccessible(true);
        return (ComboPopup)popup.get(ui);
      }
      catch (NoSuchFieldException e) {
        return null;
      }
      catch (IllegalAccessException e) {
        return null;
      }
    }

    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  public static boolean isSelectionButtonDown(MouseEvent e) {
    return e.isShiftDown() || e.isControlDown() || e.isMetaDown();
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

  @Nullable
  public static <T> T getParentOfType(Class<? extends T> cls, Component c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (cls.isAssignableFrom(eachParent.getClass())) {
        @SuppressWarnings({"unchecked"}) final T t = (T)eachParent;
        return t;
      }

      eachParent = eachParent.getParent();
    }

    return null;
  }

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
      @SuppressWarnings({"unchecked"}) final T t = (T)parent;
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
      @SuppressWarnings({"unchecked"}) final T t = (T)parent;
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
    public void draw(@NotNull final Graphics g, final PairFunction<Integer, Integer, Pair<Integer, Integer>> _position) {
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

      final Pair<Integer, Integer> position = _position.fun(maxWidth[0] + 20, height[0]);
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
            if (isUnderDarcula()) {
              g.setColor(new Color(60, 118, 249));
            }
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
        @SuppressWarnings({"unchecked"}) WeakReference<JRootPane> pane =
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

  public static Timer createNamedTimer(@NonNls @NotNull final String name, int delay, @NotNull ActionListener listener) {
    return new Timer(delay, listener) {
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

    if (component.getBackground().equals(getPanelBackground()) || component instanceof JScrollPane || component instanceof JViewport) {
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

  public static void addInsets(@NotNull JComponent component, @NotNull Insets insets) {
    if (component.getBorder() != null) {
      component.setBorder(new CompoundBorder(new EmptyBorder(insets), component.getBorder()));
    }
    else {
      component.setBorder(new EmptyBorder(insets));
    }
  }

  public static Dimension addInsets(@NotNull Dimension dimension, @NotNull Insets insets) {
    Dimension ans = new Dimension(dimension);
    ans.width += insets.left;
    ans.width += insets.right;
    ans.height += insets.top;
    ans.height += insets.bottom;

    return ans;
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

  public static void addBorder(JComponent component, Border border) {
    if (component == null) return;

    if (component.getBorder() != null) {
      component.setBorder(new CompoundBorder(border, component.getBorder()));
    }
    else {
      component.setBorder(border);
    }
  }

  private static final Color DECORATED_ROW_BG_COLOR = new JBColor(new Color(242, 245, 249), new Color(79, 83, 84));

  public static Color getDecoratedRowColor() {
    return DECORATED_ROW_BG_COLOR;
  }

  @NotNull
  public static Paint getGradientPaint(float x1, float y1, @NotNull Color c1, float x2, float y2, @NotNull Color c2) {
    return (Registry.is("ui.no.bangs.and.whistles", false)) ? ColorUtil.mix(c1, c2, .5) : new GradientPaint(x1, y1, c1, x2, y2, c2);
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

  public static void setAutoRequestFocus (final Window onWindow, final boolean set){
    if (SystemInfo.isMac) return;
    if (SystemInfo.isJavaVersionAtLeast("1.7")) {
      try {
        Method setAutoRequestFocusMethod  = onWindow.getClass().getMethod("setAutoRequestFocus",new Class [] {boolean.class});
        setAutoRequestFocusMethod.invoke(onWindow, set);
      }
      catch (NoSuchMethodException e) { LOG.debug(e); }
      catch (InvocationTargetException e) { LOG.debug(e); }
      catch (IllegalAccessException e) { LOG.debug(e); }
    }
  }
}
