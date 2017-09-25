
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
package com.intellij.ui.plaf.beg;

import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.customUsageCollectors.actions.MainMenuCollector;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.MenuDragMouseEvent;
import javax.swing.event.MenuDragMouseListener;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public class BegMenuItemUI extends BasicMenuItemUI {
  private static final Rectangle b = new Rectangle(0, 0, 0, 0);
  private static final Rectangle j = new Rectangle();
  private static final Rectangle d = new Rectangle();
  private int myMaxGutterIconWidth;
  private int a;
  private static Rectangle i = new Rectangle();
  private int k;
  private int e;
  private static final Rectangle c = new Rectangle();
  private static final Rectangle h = new Rectangle();
  private static final Rectangle l = new Rectangle();
  private static final Rectangle f = new Rectangle(32767, 32767);
  @NonNls public static final String PLAY_SOUND_METHOD = "playSound";
  @NonNls public static final String AQUA_LOOK_AND_FEEL_CLASS_NAME = "apple.laf.AquaLookAndFeel";
  @NonNls public static final String GET_KEY_MODIFIERS_TEXT = "getKeyModifiersText";

  private Border myAquaSelectedBackgroundPainter;

  /** invoked by reflection */
  public static ComponentUI createUI(JComponent component) {
    return new BegMenuItemUI();
  }

  public BegMenuItemUI() {
    myMaxGutterIconWidth = 18;

    if (UIUtil.isUnderAquaBasedLookAndFeel() && myAquaSelectedBackgroundPainter == null) {
      myAquaSelectedBackgroundPainter = (Border) UIManager.get("MenuItem.selectedBackgroundPainter");
    }
  }

  protected void installDefaults() {
    super.installDefaults();
    final String propertyPrefix = getPropertyPrefix();
    Integer integer = UIUtil.getPropertyMaxGutterIconWidth(propertyPrefix);
    if (integer != null){
      myMaxGutterIconWidth = integer.intValue();
    }
  }

  public void paint(Graphics g, JComponent comp) {
    UISettings.setupAntialiasing(g);
    JMenuItem jmenuitem = (JMenuItem)comp;
    ButtonModel buttonmodel = jmenuitem.getModel();
    int mnemonicIndex = jmenuitem.getDisplayedMnemonicIndex();
    Icon icon1 = getIcon();
    Icon icon2 = getAllowedIcon();
    int j1 = jmenuitem.getWidth();
    int k1 = jmenuitem.getHeight();
    Insets insets = comp.getInsets();
    initBounds();
    f.setBounds(0, 0, j1, k1);
    f.x += insets.left;
    f.y += insets.top;
    f.width -= insets.right + f.x;
    f.height -= insets.bottom + f.y;
    Font font = g.getFont();
    Font font1 = comp.getFont();
    g.setFont(font1);
    FontMetrics fontmetrics = g.getFontMetrics(font1);
    FontMetrics fontmetrics1 = g.getFontMetrics(acceleratorFont);
    String keyStrokeText;
    if (jmenuitem instanceof ActionMenuItem) {
      keyStrokeText = ((ActionMenuItem)jmenuitem).getFirstShortcutText();
    }else{
      keyStrokeText = getKeyStrokeText(jmenuitem.getAccelerator());
    }
    String s1 = layoutMenuItem(fontmetrics, jmenuitem.getText(), fontmetrics1, keyStrokeText, icon1, icon2, arrowIcon, jmenuitem.getVerticalAlignment(), jmenuitem.getHorizontalAlignment(), jmenuitem.getVerticalTextPosition(), jmenuitem.getHorizontalTextPosition(), f, l, j, c, h, d, jmenuitem.getText() != null ? defaultTextIconGap : 0, defaultTextIconGap);
    Color color2 = g.getColor();
    if (comp.isOpaque() || (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())){
      g.setColor(jmenuitem.getBackground());
      g.fillRect(0, 0, j1, k1);
      if (buttonmodel.isArmed() || (comp instanceof JMenu) && buttonmodel.isSelected()){
        if (UIUtil.isUnderAquaLookAndFeel()) {
          myAquaSelectedBackgroundPainter.paintBorder(comp, g, 0, 0, j1, k1);
        } else {
          g.setColor(selectionBackground);
          if (icon2 != null && !(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())){
            g.fillRect(k, 0, j1 - k, k1);
          }
          else{
            g.fillRect(0, 0, j1, k1);
            //graphics.setColor(BegResources.q);
            //graphics.drawLine(0, 0, 0, k1);
            g.setColor(selectionBackground);
          }
          //graphics.setColor(BegResources.r);
          //graphics.drawLine(j1 - 1, 0, j1 - 1, k1);
        }
      }
      g.setColor(color2);
    }
    if (icon2 != null){
      if (buttonmodel.isArmed() || (comp instanceof JMenu) && buttonmodel.isSelected()){
        g.setColor(selectionForeground);
      }
      else{
        g.setColor(jmenuitem.getForeground());
      }
      if (useCheckAndArrow()){
        icon2.paintIcon(comp, g, h.x, h.y);
      }
      g.setColor(color2);
      if (menuItem.isArmed()){
        drawIconBorder(g);
      }
    }
    if (icon1 != null){
      if (!buttonmodel.isEnabled()){
        icon1 = jmenuitem.getDisabledIcon();
      }
      else
        if (buttonmodel.isPressed() && buttonmodel.isArmed()){
          icon1 = jmenuitem.getPressedIcon();
          if (icon1 == null){
            icon1 = jmenuitem.getIcon();
          }
        }
      if (icon1 != null){
        icon1.paintIcon(comp, g, l.x, l.y);
      }
    }
    if (s1 != null && s1.length() > 0){
      if (buttonmodel.isEnabled()){
        if (buttonmodel.isArmed() || (comp instanceof JMenu) && buttonmodel.isSelected()){
          g.setColor(selectionForeground);
        }
        else{
          g.setColor(jmenuitem.getForeground());
        }
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, j.x, j.y + fontmetrics.getAscent());
      }
      else {
        final Object disabledForeground = UIUtil.getMenuItemDisabledForegroundObject();
        if (disabledForeground instanceof Color){
          g.setColor((Color)disabledForeground);
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, j.x, j.y + fontmetrics.getAscent());
        }
        else{
          g.setColor(jmenuitem.getBackground().brighter());
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, j.x, j.y + fontmetrics.getAscent());
          g.setColor(jmenuitem.getBackground().darker());
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, j.x - 1, (j.y + fontmetrics.getAscent()) - 1);
        }
      }
    }
    if (keyStrokeText != null && !keyStrokeText.isEmpty()){
      g.setFont(acceleratorFont);
      if (buttonmodel.isEnabled()){
        if (UIUtil.isUnderAquaBasedLookAndFeel() && (buttonmodel.isArmed() || (comp instanceof JMenu) && buttonmodel.isSelected())) {
          g.setColor(selectionForeground);
        }
        else {
          if (buttonmodel.isArmed() || (comp instanceof JMenu) && buttonmodel.isSelected()) {
            g.setColor(acceleratorSelectionForeground);
          }
          else {
            g.setColor(acceleratorForeground);
          }
        }
        BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x, c.y + fontmetrics.getAscent());
      }
      else
        if (disabledForeground != null){
          g.setColor(disabledForeground);
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x, c.y + fontmetrics.getAscent());
        }
        else{
          g.setColor(jmenuitem.getBackground().brighter());
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x, c.y + fontmetrics.getAscent());
          g.setColor(jmenuitem.getBackground().darker());
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x - 1, (c.y + fontmetrics.getAscent()) - 1);
        }
    }
    if (arrowIcon != null){
      if (buttonmodel.isArmed() || (comp instanceof JMenu) && buttonmodel.isSelected()){
        g.setColor(selectionForeground);
      }
      if (useCheckAndArrow()){
        arrowIcon.paintIcon(comp, g, d.x, d.y);
      }
    }
    g.setColor(color2);
    g.setFont(font);
  }

  private String getKeyStrokeText(KeyStroke keystroke) {
    String s1 = "";
    if (keystroke != null){
      int j1 = keystroke.getModifiers();
      if (j1 > 0){
        if (SystemInfo.isMac) {
          try {
            Class appleLaf = Class.forName(AQUA_LOOK_AND_FEEL_CLASS_NAME);
            Method getModifiers = appleLaf.getMethod(GET_KEY_MODIFIERS_TEXT, new Class[] {int.class, boolean.class});
            s1 = (String)getModifiers.invoke(appleLaf, new Object[] {new Integer(j1), Boolean.FALSE});
          }
          catch (Exception e) {
            if (SystemInfo.isMacOSLeopard) {
              s1 = KeymapUtil.getKeyModifiersTextForMacOSLeopard(j1);
            }
            else {
              s1 = KeyEvent.getKeyModifiersText(j1) + '+';
            }
          }
        }
        else {
          s1 = KeyEvent.getKeyModifiersText(j1) + '+';
        }

      }
      s1 = s1 + KeyEvent.getKeyText(keystroke.getKeyCode());
    }
    return s1;
  }

  private boolean useCheckAndArrow() {
    if ((menuItem instanceof JMenu) && ((JMenu)menuItem).isTopLevelMenu()){
      return false;
    }
    return true;
  }

  public MenuElement[] getPath() {
    MenuSelectionManager menuselectionmanager = MenuSelectionManager.defaultManager();
    MenuElement amenuelement[] = menuselectionmanager.getSelectedPath();
    int i1 = amenuelement.length;
    if (i1 == 0){
      return new MenuElement[0];
    }
    java.awt.Container container = menuItem.getParent();
    MenuElement amenuelement1[];
    if (amenuelement[i1 - 1].getComponent() == container){
      amenuelement1 = new MenuElement[i1 + 1];
      System.arraycopy(amenuelement, 0, amenuelement1, 0, i1);
      amenuelement1[i1] = menuItem;
    }
    else{
      int j1;
      for(j1 = amenuelement.length - 1; j1 >= 0; j1--){
        if (amenuelement[j1].getComponent() == container){
          break;
        }
      }
      amenuelement1 = new MenuElement[j1 + 2];
      System.arraycopy(amenuelement, 0, amenuelement1, 0, j1 + 1);
      amenuelement1[j1 + 1] = menuItem;
    }
    return amenuelement1;
  }

  public Dimension getMinimumSize(JComponent jcomponent) {
    return null;
  }

  private String layoutMenuItem(
    FontMetrics fontmetrics,
    String text,
    FontMetrics fontmetrics1,
    String keyStrokeText,
    Icon icon,
    Icon checkIcon,
    Icon arrowIcon,
    int verticalAlignment,
    int horizontalAlignment,
    int verticalTextPosition,
    int horizontalTextPosition,
    Rectangle viewRect,
    Rectangle iconRect,
    Rectangle textRect,
    Rectangle acceleratorRect,
    Rectangle checkIconRect,
    Rectangle arrowIconRect,
    int textIconGap,
    int menuItemGap
  ) {
    SwingUtilities.layoutCompoundLabel(menuItem, fontmetrics, text, icon, verticalAlignment, horizontalAlignment, verticalTextPosition, horizontalTextPosition, viewRect, iconRect, textRect, textIconGap);
    if (keyStrokeText == null || "".equals(keyStrokeText)){
      acceleratorRect.width = acceleratorRect.height = 0;
    }
    else{
      acceleratorRect.width = SwingUtilities.computeStringWidth(fontmetrics1, keyStrokeText);
      acceleratorRect.height = fontmetrics1.getHeight();
    }

    /* Initialize the checkIcon bounds rectangle's width & height.
     */
    if (useCheckAndArrow()){
      if (checkIcon != null){
        checkIconRect.width = checkIcon.getIconWidth();
        checkIconRect.height = checkIcon.getIconHeight();
      }else{
        checkIconRect.width = checkIconRect.height = 0;
      }

      /* Initialize the arrowIcon bounds rectangle width & height.
       */
      if (arrowIcon != null){
        arrowIconRect.width = arrowIcon.getIconWidth();
        arrowIconRect.height = arrowIcon.getIconHeight();
      }else{
        arrowIconRect.width = arrowIconRect.height = 0;
      }
      textRect.x += myMaxGutterIconWidth;
      iconRect.x += myMaxGutterIconWidth;
    }
    textRect.x += menuItemGap;
    iconRect.x += menuItemGap;
    Rectangle labelRect = iconRect.union(textRect);

    // Position the Accelerator text rect

    acceleratorRect.x += viewRect.width - arrowIconRect.width - menuItemGap - acceleratorRect.width;
    acceleratorRect.y = (viewRect.y + viewRect.height / 2) - acceleratorRect.height / 2;

    // Position the Check and Arrow Icons

    if (useCheckAndArrow()){
      arrowIconRect.x += viewRect.width - arrowIconRect.width;
      arrowIconRect.y = (viewRect.y + labelRect.height / 2) - arrowIconRect.height / 2;
      if (checkIcon != null){
        checkIconRect.y = (viewRect.y + labelRect.height / 2) - checkIconRect.height / 2;
        checkIconRect.x += (viewRect.x + myMaxGutterIconWidth / 2) - checkIcon.getIconWidth() / 2;
        a = viewRect.x;
        e = (viewRect.y + labelRect.height / 2) - myMaxGutterIconWidth / 2;
        k = viewRect.x + myMaxGutterIconWidth + 2;
      }
      else{
        checkIconRect.x = checkIconRect.y = 0;
      }
    }
    return text;
  }

  private Icon getIcon() {
    Icon icon = menuItem.getIcon();
    if (icon != null && getAllowedIcon() != null){
      icon = null;
    }
    return icon;
  }

  public Dimension getPreferredSize(JComponent comp) {
    JMenuItem jmenuitem = (JMenuItem)comp;
    Icon icon1 = getIcon();
    Icon icon2 = getAllowedIcon();
    String text = jmenuitem.getText();
    String keyStrokeText;
    if (jmenuitem instanceof ActionMenuItem) {
      keyStrokeText = ((ActionMenuItem)jmenuitem).getFirstShortcutText();
    }else{
      keyStrokeText = getKeyStrokeText(jmenuitem.getAccelerator());
    }
    Font font = jmenuitem.getFont();
    FontMetrics fontmetrics = comp.getFontMetrics(font);
    FontMetrics fontmetrics1 = comp.getFontMetrics(acceleratorFont);
    initBounds();
    layoutMenuItem(fontmetrics, text, fontmetrics1, keyStrokeText, icon1, icon2, arrowIcon, jmenuitem.getVerticalAlignment(), jmenuitem.getHorizontalAlignment(), jmenuitem.getVerticalTextPosition(), jmenuitem.getHorizontalTextPosition(), f, l, j, c, h, d, text != null ? defaultTextIconGap : 0, defaultTextIconGap);
    i.setBounds(j);
    i = SwingUtilities.computeUnion(l.x, l.y, l.width, l.height, i);
    if (!(keyStrokeText == null || "".equals(keyStrokeText))){
      i.width += c.width;
      i.width += 7 * defaultTextIconGap;
    }
    if (useCheckAndArrow()){
      i.width += myMaxGutterIconWidth;
      i.width += defaultTextIconGap;
      i.width += defaultTextIconGap;
      i.width += d.width;
    }
    i.width += 2 * defaultTextIconGap;
    Insets insets = jmenuitem.getInsets();
    if (insets != null){
      i.width += insets.left + insets.right;
      i.height += insets.top + insets.bottom;
    }
    if (i.width % 2 == 0){
      i.width++;
    }
    if (i.height % 2 == 0){
      i.height++;
    }
    return i.getSize();
  }

  private void drawIconBorder(Graphics g) {
/*
    int i1 = a - 1;
    int j1 = e - 2;
    int k1 = i1 + myMaxGutterIconWidth + 1;
    int l1 = j1 + myMaxGutterIconWidth + 4;
    g.setColor(BegResources.m);
    g.drawLine(i1, j1, i1, l1);
    g.drawLine(i1, j1, k1, j1);
    g.setColor(BegResources.j);
    g.drawLine(k1, j1, k1, l1);
    g.drawLine(i1, l1, k1, l1);
*/
  }

  private void initBounds() {
    l.setBounds(b);
    j.setBounds(b);
    c.setBounds(b);
    h.setBounds(b);
    d.setBounds(b);
    f.setBounds(0, 0, 32767, 32767);
    i.setBounds(b);
  }

  private Icon getAllowedIcon() {
    Icon icon = menuItem.isEnabled() ? menuItem.getIcon() : menuItem.getDisabledIcon();
    if (icon != null && icon.getIconWidth() > myMaxGutterIconWidth){
      icon = null;
    }
    return icon;
  }

  public Dimension getMaximumSize(JComponent comp) {
    return null;
  }

  public void update(Graphics g, JComponent comp) {
    paint(g, comp);
  }

  /** Copied from BasicMenuItemUI */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private boolean isInternalFrameSystemMenu(){
    String actionCommand=menuItem.getActionCommand();
    if(
      ("Close".equals(actionCommand))||
      ("Minimize".equals(actionCommand))||
      ("Restore".equals(actionCommand))||
      ("Maximize".equals(actionCommand))
    ){
      return true;
    } else{
      return false;
    }
  }

  /** Copied from BasicMenuItemUI */
  private void doClick(MenuSelectionManager msm,MouseEvent e) {
    // Auditory cue
    if(!isInternalFrameSystemMenu()){
      @NonNls ActionMap map=menuItem.getActionMap();
      if(map!=null){
        Action audioAction=map.get(getPropertyPrefix()+".commandSound");
        if(audioAction!=null){
          // pass off firing the Action to a utility method
          BasicLookAndFeel lf=(BasicLookAndFeel)UIManager.getLookAndFeel();
          // It's a hack. The method BasicLookAndFeel.playSound has protected access, so
          // it's imposible to mormally invoke it.
          try {
            Method playSoundMethod=BasicLookAndFeel.class.getDeclaredMethod(PLAY_SOUND_METHOD,new Class[]{Action.class});
            playSoundMethod.setAccessible(true);
            playSoundMethod.invoke(lf,new Object[]{audioAction});
          } catch(Exception ignored) {}
        }
      }
    }
    // Visual feedback
    if(msm==null){
      msm=MenuSelectionManager.defaultManager();
    }
    ActionMenuItem item = (ActionMenuItem)menuItem;
    AnAction action = item.getAnAction();
    if (action != null && ActionPlaces.MAIN_MENU.equals(item.getPlace())) {
      MainMenuCollector.getInstance().record(action);
    }
    msm.clearSelectedPath();
    item.fireActionPerformed(
      new ActionEvent(
        menuItem,
        ActionEvent.ACTION_PERFORMED,
        null,
        e.getWhen(),
        e.getModifiers()
      )
    );
  }

  protected MouseInputListener createMouseInputListener(JComponent c){
    return new MyMouseInputHandler();
  }

  private class MyMouseInputHandler extends MouseInputHandler{
    public void mouseReleased(MouseEvent e){
      MenuSelectionManager manager=MenuSelectionManager.defaultManager();
      Point p=e.getPoint();
      if(p.x>=0&&p.x<menuItem.getWidth()&&p.y>=0&&p.y<menuItem.getHeight()){
        doClick(manager,e);
      } else{
        manager.processMouseEvent(e);
      }
    }
  }

  protected MenuDragMouseListener createMenuDragMouseListener(JComponent c){
    return new MyMenuDragMouseHandler();
  }

  private class MyMenuDragMouseHandler implements MenuDragMouseListener {
    public void menuDragMouseEntered(MenuDragMouseEvent e){}

    public void menuDragMouseDragged(MenuDragMouseEvent e){
      MenuSelectionManager manager=e.getMenuSelectionManager();
      MenuElement path[]=e.getPath();
      manager.setSelectedPath(path);
    }

    public void menuDragMouseExited(MenuDragMouseEvent e){}

    public void menuDragMouseReleased(MenuDragMouseEvent e){
      MenuSelectionManager manager=e.getMenuSelectionManager();
      Point p=e.getPoint();
      if(p.x>=0&&p.x<menuItem.getWidth()&&
         p.y>=0&&p.y<menuItem.getHeight()){
        doClick(manager,e);
      } else{
        manager.clearSelectedPath();
      }
    }
  }
}
