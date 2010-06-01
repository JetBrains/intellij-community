/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.*;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.*;
import com.intellij.ui.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.text.html.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * @author max
 */
public class UIUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.UIUtil");
  @NonNls public static final String HTML_MIME = "text/html";
  public static final char MNEMONIC = 0x1B;
  @NonNls public static final String JSLIDER_ISFILLED = "JSlider.isFilled";
  @NonNls public static final String ARIAL_FONT_NAME = "Arial";
  @NonNls public static final String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";


  private static final Color ACTIVE_COLOR = new Color(160, 186, 213);
  private static final Color INACTIVE_COLOR = new Color(128, 128, 128);
  private static final Color SEPARATOR_COLOR = INACTIVE_COLOR.brighter();
  public static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("<\\s*([^<>/ ]+)([^<>]*)/\\s*>", Pattern.CASE_INSENSITIVE);

  @NonNls public static final String FOCUS_PROXY_KEY = "isFocusProxy";

  public static Key<Integer> KEEP_BORDER_SIDES = Key.create("keepBorderSides");

  private UIUtil() {
  }

  public static boolean isReallyTypedEvent(KeyEvent e) {
    char c = e.getKeyChar();
    if (!(c >= 0x20 && c != 0x7F)) return false;

    int modifiers = e.getModifiers();
    if (SystemInfo.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }

    return (modifiers & ActionEvent.ALT_MASK) == (modifiers & ActionEvent.CTRL_MASK);
  }

  public static int getStringY(@NotNull final String string, @NotNull final Rectangle bounds, @NotNull final Graphics2D g) {
    final int centerY = bounds.height / 2;
    final FontMetrics fm = g.getFontMetrics();
    final Font font = g.getFont();
    final FontRenderContext frc = g.getFontRenderContext();
    final Rectangle stringBounds = font.getStringBounds(string, frc).getBounds();

    return (int) (centerY - stringBounds.height / 2.0 - stringBounds.y);
  }

  public static void setEnabled(Component component, boolean enabled, boolean recursively) {
    component.setEnabled(enabled);
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

  public static String[] splitText(String text, FontMetrics fontMetrics, int widthLimit, char separator){
    ArrayList<String> lines = new ArrayList<String>();
    String currentLine = "";
    StringBuffer currentAtom = new StringBuffer();

    for (int i=0; i < text.length(); i++) {
      char ch = text.charAt(i);
      currentAtom.append(ch);

      if (ch == separator) {
        currentLine += currentAtom.toString();
        currentAtom.setLength(0);
      }

      String s = currentLine + currentAtom.toString();
      int width = fontMetrics.stringWidth(s);

      if (width >= widthLimit - fontMetrics.charWidth('w')) {
        if (currentLine.length() > 0) {
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
    if (s.length() > 0) {
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

  public static Font getLabelFont() {
    return UIManager.getFont("Label.font");
  }

  public static Color getLabelBackground() {
    return UIManager.getColor("Label.background");
  }

  public static Color getLabelForeground() {
    return UIManager.getColor("Label.foreground");
  }

  public static Icon getOptionPanelWarningIcon() {
    return UIManager.getIcon("OptionPane.warningIcon");
  }

  public static Icon getOptionPanelQuestionIcon() {
    return UIManager.getIcon("OptionPane.questionIcon");
  }

  @NotNull
  public static String removeMnemonic(@NotNull String s) {
    if (s.indexOf('&') != -1) {
      s = StringUtil.replace(s, "&", "");
    }
    if (s.indexOf(MNEMONIC) != -1) {
      s = StringUtil.replace(s, String.valueOf(MNEMONIC), "");
    }
    return s;
  }

  public static String replaceMnemonicAmpersand(final String value) {
    if (value.indexOf('&') >= 0) {
      boolean useMacMnemonic = value.contains("&&");
      StringBuilder realValue = new StringBuilder();
      int i = 0;
      while (i < value.length()) {
        char c = value.charAt(i);
        if (c == '\\') {
          if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
            realValue.append('&');
            i++;
          }
          else {
            realValue.append(c);
          }
        }
        else if (c == '&') {
          if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
            if (SystemInfo.isMac) {
              realValue.append(MNEMONIC);
            }
            i++;
          }
          else {
            if (!SystemInfo.isMac || !useMacMnemonic) {
              realValue.append(MNEMONIC);
            }
          }
        }
        else {
          realValue.append(c);
        }
        i++;
      }

      return realValue.toString();
    }
    return value;
  }

  public static Color getTableHeaderBackground() {
    return UIManager.getColor("TableHeader.background");
  }

  public static Color getTreeTextForeground() {
    return UIManager.getColor("Tree.textForeground");
  }

  public static Color getTreeSelectionBackground() {
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
    return UIManager.getColor("Table.selectionBackground");
  }

  public static Color getActiveTextColor() {
    return UIManager.getColor("textActiveText");
  }

  public static Color getInactiveTextColor() {
    return UIManager.getColor("textInactiveText");
  }

  public static Color getActiveTextFieldBackgroundColor() {
    return UIManager.getColor("TextField.background");
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

  public static Color getTextInactiveTextColor() {
    return UIManager.getColor("textInactiveText");
  }

  public static void installPopupMenuColorAndFonts(final JComponent contentPane) {
    LookAndFeel.installColorsAndFont(contentPane, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
  }

  public static void installPopupMenuBorder(final JComponent contentPane) {
    LookAndFeel.installBorder(contentPane, "PopupMenu.border");
  }

  public static boolean isMotifLookAndFeel() {
    return "Motif".equals(UIManager.getLookAndFeel().getID());
  }

  public static Color getTreeSelectionBorderColor() {
    return UIManager.getColor("Tree.selectionBorderColor");
  }

  public static Object getTreeRightChildIndent() {
    return UIManager.get("Tree.rightChildIndent");
  }

  public static Object getTreeLeftChildIndent() {
    return UIManager.get("Tree.leftChildIndent");
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
    return UIManager.getColor("Table.background");
  }

  public static Color getTableSelectionForeground() {
    return UIManager.getColor("Table.selectionForeground");
  }

  public static Color getTableForeground() {
    return UIManager.getColor("Table.foreground");
  }

  public static Color getListBackground() {
    return UIManager.getColor("List.background");
  }

  public static Color getListForeground() {
    return UIManager.getColor("List.foreground");
  }

  public static Color getPanelBackground() {
    return UIManager.getColor("Panel.background");
  }

  public static Color getTreeForeground() {
    return UIManager.getColor("Tree.foreground");
  }

  public static Color getTableFocusCellBackground() {
    return UIManager.getColor("Table.focusCellBackground");
  }

  public static Color getListSelectionBackground() {
    final Color color = UIManager.getColor("List.selectionBackground");
    if (color == null) {
      return UIManager.getColor("List[Selected].textBackground");  // Nimbus
    }
    return color;
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

  public static Color getSeparatorShadow() {
    return UIManager.getColor("Separator.shadow");
  }

  public static Font getMenuFont() {
    return UIManager.getFont("Menu.font");
  }

  public static Color getSeparatorHighlight() {
    return UIManager.getColor("Separator.highlight");
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

  public static Color getPanelBackgound() {
    return UIManager.getColor("Panel.background");
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
    return IconLoader.getIcon("/general/balloonInformation.png");
  }

  public static Icon getBalloonWarningIcon() {
    return IconLoader.getIcon("/general/balloonWarning.png");
  }

  public static Icon getBalloonErrorIcon() {
    return IconLoader.getIcon("/general/balloonError.png");
  }

  public static Icon getRadioButtonIcon() {
    return UIManager.getIcon("RadioButton.icon");
  }

  public static Icon getTreeCollapsedIcon() {
    return UIManager.getIcon("Tree.collapsedIcon");
  }

  public static Icon getTreeExpandedIcon() {
    return UIManager.getIcon("Tree.expandedIcon");
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
  public static boolean isUnderQuaquaLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Quaqua");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderNimbusLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Nimbus");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderAquaLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Mac OS X");
  }

  public static boolean isFullRowSelectionLAF() {
    return isUnderNimbusLookAndFeel() || isUnderQuaquaLookAndFeel();
  }

  public static boolean isUnderNativeMacLookAndFeel() {
    return isUnderAquaLookAndFeel() || isUnderQuaquaLookAndFeel();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void removeQuaquaVisualMarginsIn(Component component) {
    if (component instanceof JComponent) {
      final JComponent jComponent = (JComponent)component;
      final Component[] children = jComponent.getComponents();
      for (Component child : children) {
        removeQuaquaVisualMarginsIn(child);
      }

      jComponent.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));
    }
  }

  public static boolean isControlKeyDown(MouseEvent mouseEvent) {
    return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  public static String[] getValidFontNames(final boolean familyName) {
    Set<String> result = new TreeSet<String>();
    Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    for (Font font : fonts) {
      // Adds fonts that can display symbols at [A, Z] + [a, z] + [0, 9]
      try {
        if (font.canDisplay('a') && font.canDisplay('z') && font.canDisplay('A') && font.canDisplay('Z') && font.canDisplay('0') &&
            font.canDisplay('1')) {
          result.add(familyName ? font.getFamily() : font.getName());
        }
      }
      catch (Exception e) {
        // JRE has problems working with the font. Just skip.
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  public static String[] getStandardFontSizes() {
    return new String[]{"8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};
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
      if (fg.getRed() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getRed()));
      if (fg.getGreen() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getGreen()));
      if (fg.getBlue() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getBlue()));
      rule.append(" ; ");
    }
    rule.append(" }");
    return rule.toString();
  }

  /**
   * @param g
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

  public static void applyRenderingHints(final Graphics g) {
    Toolkit tk = Toolkit.getDefaultToolkit();
    //noinspection HardCodedStringLiteral
    Map map = (Map)tk.getDesktopProperty("awt.font.desktophints");
    if (map != null) {
      ((Graphics2D)g).addRenderingHints(map);
    }
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
      public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
      }
    });
  }

  public static void drawVDottedLine(Graphics2D g, int lineX, int startY, int endY, final Color bgColor, final Color fgColor) {
    g.setColor(bgColor);
    drawLine(g, lineX, startY, lineX, endY);

    g.setColor(fgColor);

    for (int i = (startY / 2) * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  public static void drawHDottedLine(Graphics2D g, int startX, int endX, int lineY, final Color bgColor, final Color fgColor) {
    g.setColor(bgColor);
    drawLine(g, startX, lineY, endX, lineY);

    g.setColor(fgColor);

    for (int i = (startX / 2) * 2; i < endX; i += 2) {
      g.drawRect(i, lineY, 0, 0);
    }
  }

  public static void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2, final Color bgColor, final Color fgColor) {
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
    if (isCloseClick(e) || e.isPopupTrigger() || e.getID() != effectiveType) return false;
    return e.getButton() == MouseEvent.BUTTON1;
  }

  @NotNull
  public static
  Color getBgFillColor(@NotNull JComponent c) {
    final Component parent = findNearestOpaque(c);
    return parent == null ? c.getBackground() : parent.getBackground();
  }

  @Nullable
  public static
  Component findNearestOpaque(JComponent c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent.isOpaque()) return eachParent;
      eachParent = eachParent.getParent();
    }

    return eachParent;
  }

  @NonNls
  public static String getCssFontDeclaration(final Font font) {
    return "<style> body, div, td { font-family: " + font.getFamily() + "; font-size: " + font.getSize() + "; } </style>";
  }

  public static boolean isWinLafOnVista() {
    return (SystemInfo.isWindowsVista || SystemInfo.isWindows7) && "Windows".equals(UIManager.getLookAndFeel().getName());
  }

  public static boolean isStandardMenuLAF() {
    return isWinLafOnVista() || "Nimbus".equals(UIManager.getLookAndFeel().getName());
  }

  public static Color getFocusedFillColor() {
    return toAlpha(getListSelectionBackground(), 100);
  }

  public static Color getFocusedBoundsColor() {
    return getBoundsColor();
  }

  public static Color getBoundsColor() {
    return new Color(128, 128, 128);
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
      public void run() {
        if (isToDispose(progress)) {
          progress.getUI().uninstallUI(progress);
          progress.putClientProperty("isDisposed", Boolean.TRUE);
        }
      }
    });
  }

  private static boolean isToDispose(final JProgressBar progress)  {
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

  public static void setToolkitModal(final JDialog dialog) {
    try {
      final Class<?> modalityType = dialog.getClass().getClassLoader().loadClass("java.awt.Dialog$ModalityType");
      final Field field = modalityType.getField("TOOLKIT_MODAL");
      final Object value = field.get(null);

      final Method method = dialog.getClass().getMethod("setModalityType", modalityType);
      method.invoke(dialog, value);
    }
    catch (Exception e) {
      // ignore - no JDK 6
    }
  }

  public static void updateDialogIcon(final JDialog dialog, final List<Image> images) {
    try {
      final Method method = dialog.getClass().getMethod("setIconImages", List.class);
      method.invoke(dialog, images);
    }
    catch (Exception e) {
      // ignore - no JDK 6
    }
  }

  public static boolean hasJdk6Dialogs() {
    try {
      UIUtil.class.getClassLoader().loadClass("java.awt.Dialog$ModalityType");
    }
    catch (Throwable e) {
      return false;
    }
    return true;
  }

  public static Color getBorderActiveColor() {
    return ACTIVE_COLOR;
  }

  public static Color getBorderInactiveColor() {
    return INACTIVE_COLOR;
  }

  public static Color getBorderSeparatorColor() {
    return SEPARATOR_COLOR;
  }

  public static HTMLEditorKit getHTMLEditorKit() {
    final HTMLEditorKit kit = new HTMLEditorKit();

    Font font = UIManager.getFont("Label.font");
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : 11;

    final StyleSheet styleSheet = kit.getStyleSheet();
    styleSheet.addRule(String.format("body, div, p { font-family: %s; font-size: %s; } p { margin-top: 0; }", family, size));
    kit.setStyleSheet(styleSheet);

    return kit;
  }

  public static void removeScrollBorder(final Component c) {
    new AwtVisitor(c) {
      public boolean visit(final Component component) {
        if (component instanceof JScrollPane) {
          if (!hasNonPrimitiveParents(c, component)) {
            final JScrollPane scrollPane = (JScrollPane)component;
            Integer keepBorderSides = (Integer) scrollPane.getClientProperty(KEEP_BORDER_SIDES);
            if (keepBorderSides != null) {
              if (scrollPane.getBorder() instanceof LineBorder) {
                Color color = ((LineBorder) scrollPane.getBorder()).getLineColor();
                scrollPane.setBorder(new SideBorder(color, keepBorderSides.intValue()));
              }
              else {
                scrollPane.setBorder(new SideBorder(getBoundsColor(), keepBorderSides.intValue()));
              }
            }
            else {
              scrollPane.setBorder(null);
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

    result.x = containerLocation.x + (containerSize.width / 2 - child.width / 2);
    result.y = containerLocation.y + (containerSize.height / 2 - child.height / 2);

    return result;
  }

  public static String toHtml(String html) {
    return toHtml(html, 0);
  }

  @NonNls
  public static String toHtml(String html, final int hPadding) {
    html = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
    Font font = UIManager.getFont("Label.font");
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

  public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    } else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  /**
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   *
   * @param runnable a runnable to invoke
   */
  public static void invokeAndWaitIfNeeded(@NotNull Runnable runnable) {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(runnable);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public static boolean isFocusProxy(@Nullable Component c) {
    return c instanceof JComponent && Boolean.TRUE.equals(((JComponent)c).getClientProperty(FOCUS_PROXY_KEY));
  }

  public static void setFocusProxy(JComponent c, boolean isProxy) {
    c.putClientProperty(FOCUS_PROXY_KEY, isProxy ? Boolean.TRUE : null);
  }

  public static class MacTreeUI extends BasicTreeUI {
    public static final String SOURCE_LIST_CLIENT_PROPERTY = "mac.ui.source.list";

    public static final Color UNFOCUSED_SELECTION_COLOR = new Color(212, 212, 212);

    private static final Icon TREE_COLLAPSED_ICON = (Icon) UIManager.get("Tree.collapsedIcon");
    private static final Icon TREE_EXPANDED_ICON = (Icon) UIManager.get("Tree.expandedIcon");
    private static final Icon TREE_SELECTED_COLLAPSED_ICON = IconLoader.getIcon("/mac/tree_white_right_arrow.png");
    private static final Icon TREE_SELECTED_EXPANDED_ICON = IconLoader.getIcon("/mac/tree_white_down_arrow.png");

    private static final Border LIST_BACKGROUND_PAINTER = (Border) UIManager.get("List.sourceListBackgroundPainter");
    private static final Border LIST_SELECTION_BACKGROUND_PAINTER = (Border) UIManager.get("List.sourceListSelectionBackgroundPainter");
    private static final Border LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER = (Border) UIManager.get("List.sourceListFocusedSelectionBackgroundPainter");

    private MouseListener mySelectionListener = new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull final MouseEvent e) {
        final JTree tree = (JTree) e.getSource();
        if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
          // if we can't stop any ongoing editing, do nothing
          if (isEditing(tree) && tree.getInvokesStopCellEditing()
                              && !stopEditing(tree)) {
              return;
          }
          
          final TreePath pressedPath = getClosestPathForLocation(tree, e.getX(), e.getY());
          if (tree.isPathSelected(pressedPath)) return;

          if (pressedPath != null) {
            Rectangle bounds = getPathBounds(tree, pressedPath);

            if(e.getY() >= (bounds.y + bounds.height)) {
                return;
            }

            if (isLocationInExpandControl(pressedPath, e.getX(), e.getY())) {
              return;
            }

            if (tree.getDragEnabled() || !startEditing(pressedPath, e)) {
               selectPathForEvent(pressedPath, e);
            }
          }

        }
      }
    };

    @Override
    protected void completeUIInstall() {
      super.completeUIInstall();

      tree.setOpaque(false);
      tree.setLargeModel(true);
      tree.setShowsRootHandles(true);

      tree.addMouseListener(mySelectionListener);
    }

    @Override
    protected void installKeyboardActions() {
      super.installKeyboardActions();

      tree.getInputMap().put(KeyStroke.getKeyStroke("pressed LEFT"), "collapse_or_move_up");
      tree.getInputMap().put(KeyStroke.getKeyStroke("pressed RIGHT"), "expand");

      tree.getActionMap().put("collapse_or_move_up", new AbstractAction() {
        public void actionPerformed(final ActionEvent e) {
          final Object source = e.getSource();
          if (source instanceof JTree) {
            JTree tree = (JTree)source;
            int selectionRow = tree.getLeadSelectionRow();
            if (selectionRow == -1) return;

            if (isLeaf(selectionRow) || tree.isCollapsed(selectionRow)) {
              final TreePath parentPath = tree.getPathForRow(selectionRow).getParentPath();
              if (parentPath != null) {
                if (parentPath.getParentPath() != null || tree.isRootVisible()) {
                  final int parentRow = tree.getRowForPath(parentPath);
                  tree.scrollRowToVisible(parentRow);
                  tree.setSelectionInterval(parentRow, parentRow);
                }
              }
            }
            else {
              tree.collapseRow(selectionRow);
            }
          }
        }
      });
    }

    @Override
    protected void paintHorizontalPartOfLeg(final Graphics g,
                                            final Rectangle clipBounds,
                                            final Insets insets,
                                            final Rectangle bounds,
                                            final TreePath path,
                                            final int row,
                                            final boolean isExpanded,
                                            final boolean hasBeenExpanded,
                                            final boolean isLeaf) {

    }

    @Override
    protected boolean isToggleSelectionEvent(MouseEvent e) {
      return SwingUtilities.isLeftMouseButton(e) && (SystemInfo.isMac ? e.isMetaDown() : e.isControlDown()) && !e.isPopupTrigger();
    }

    @Override
    protected void paintVerticalPartOfLeg(final Graphics g, final Rectangle clipBounds, final Insets insets, final TreePath path) {
    }

    @Override
    protected void paintHorizontalLine(Graphics g, JComponent c, int y, int left, int right) {
    }

    @Override
    protected void paintRow(final Graphics g,
                            final Rectangle clipBounds,
                            final Insets insets,
                            final Rectangle bounds,
                            final TreePath path,
                            final int row,
                            final boolean isExpanded,
                            final boolean hasBeenExpanded,
                            final boolean isLeaf) {
      final int containerWidth = tree.getParent() instanceof JViewport ? tree.getParent().getWidth() : tree.getWidth();
      final int xOffset = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewPosition().x : 0;

      boolean selected = false;
      if (path != null) {
        selected = tree.isPathSelected(path);
        Graphics2D rowGraphics = (Graphics2D)g.create();
        if (clipBounds.height >= bounds.height) {
          // fill the row only if clip bounds intersects actual node bounds
          rowGraphics.setClip(xOffset, bounds.y, containerWidth, bounds.height);
        }
        else {
          // just paint inside clip bounds otherwise
          rowGraphics.setClip(clipBounds);
        }

        final Object sourceList = tree.getClientProperty(SOURCE_LIST_CLIENT_PROPERTY);
        if (sourceList != null && ((Boolean)sourceList)) {
          if (selected) {
            if (tree.hasFocus()) {
              LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
            }
            else {
              LIST_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
            }
          } else {
            rowGraphics.setColor(tree.getBackground());
            rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height);
          }
        } else {
          Color bg = tree.hasFocus() ? getTreeSelectionBackground() : UNFOCUSED_SELECTION_COLOR;
          if (!selected) {
            bg = tree.getBackground();
          }

          rowGraphics.setColor(bg);
          rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height - 1);
        }

        if (shouldPaintExpandControl(path, row, isExpanded, hasBeenExpanded, isLeaf)) {
          paintExpandControl(rowGraphics, bounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
        }

        rowGraphics.dispose();
      }

      super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
      final int containerWidth = tree.getParent() instanceof JViewport ? tree.getParent().getWidth() : tree.getWidth();
      final int xOffset = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewPosition().x : 0;
      final Rectangle bounds = g.getClipBounds();

      // draw background for the given clip bounds
      final Object sourceList = tree.getClientProperty(SOURCE_LIST_CLIENT_PROPERTY);
      if (sourceList != null && ((Boolean)sourceList)) {
        Graphics2D backgroundGraphics = (Graphics2D) g.create();
        backgroundGraphics.setClip(xOffset, bounds.y, containerWidth, bounds.height);
        LIST_BACKGROUND_PAINTER.paintBorder(tree, backgroundGraphics, xOffset, bounds.y, containerWidth, bounds.height);
        backgroundGraphics.dispose();
      }

      super.paint(g, c);
    }

    @Override
    protected CellRendererPane createCellRendererPane() {
      return new CellRendererPane() {
        @Override
        public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
          if (c instanceof JComponent) {
          }
          ((JComponent)c).setOpaque(false);

          super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
        }
      };
    }

    @Override
    public void uninstallUI(JComponent c) {
      super.uninstallUI(c);

      c.removeMouseListener(mySelectionListener);
    }

    @Override
    protected void paintExpandControl(Graphics g,
                                      Rectangle clipBounds,
                                      Insets insets,
                                      Rectangle bounds,
                                      TreePath path,
                                      int row,
                                      boolean isExpanded,
                                      boolean hasBeenExpanded,
                                      boolean isLeaf) {
      boolean isPathSelected = tree.getSelectionModel().isPathSelected(path);

      Icon expandIcon = isPathSelected && tree.hasFocus() ? TREE_SELECTED_EXPANDED_ICON
                                       : TREE_EXPANDED_ICON;
      Icon collapseIcon = isPathSelected && tree.hasFocus() ? TREE_SELECTED_COLLAPSED_ICON
                                         : TREE_COLLAPSED_ICON;


      if (!isLeaf(row)) {
        setExpandedIcon(expandIcon);
        setCollapsedIcon(collapseIcon);
      }

      super.paintExpandControl(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
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

}

