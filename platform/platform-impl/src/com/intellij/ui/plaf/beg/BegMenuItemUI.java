
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.MainMenuCollector;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
import java.util.function.Consumer;

/**
 * @author Eugene Belyaev
 */
public final class BegMenuItemUI extends BasicMenuItemUI {
  private static final String KEEP_MENU_OPEN_PROP = "BegMenuItemUI.keep-menu-open";

  private static final Rectangle b = new Rectangle(0, 0, 0, 0);
  private static final Rectangle j = new Rectangle();
  private static final Rectangle d = new Rectangle();
  private int myMaxGutterIconWidth;
  private int myMaxGutterIconWidth2;
  private int k;
  private static final Rectangle c = new Rectangle();
  private static final Rectangle h = new Rectangle();
  private static final Rectangle l = new Rectangle();
  private static final Rectangle f = new Rectangle(32767, 32767);
  @NonNls public static final String PLAY_SOUND_METHOD = "playSound";
  @NonNls public static final String AQUA_LOOK_AND_FEEL_CLASS_NAME = "apple.laf.AquaLookAndFeel";
  @NonNls public static final String GET_KEY_MODIFIERS_TEXT = "getKeyModifiersText";

  /** invoked by reflection */
  public static ComponentUI createUI(JComponent component) {
    return new BegMenuItemUI();
  }

  public BegMenuItemUI() {
    myMaxGutterIconWidth2 = myMaxGutterIconWidth = 18;
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    final String propertyPrefix = getPropertyPrefix();
    Integer integer = UIUtil.getPropertyMaxGutterIconWidth(propertyPrefix);
    if (integer != null){
      myMaxGutterIconWidth2 = myMaxGutterIconWidth = integer.intValue();
    }

    selectionBackground = JBColor.namedColor("Menu.selectionBackground", UIUtil.getListSelectionBackground(true));
  }

  private static boolean isSelected(JMenuItem item) {
    if (item == null) return false;
    ButtonModel model = item.getModel();
    if (model == null) return false;
    return model.isArmed() || (item instanceof JMenu) && model.isSelected();
  }

  private void checkArrowIcon() {
    if (arrowIcon != null && IdeaPopupMenuUI.isPartOfPopupMenu(menuItem)) {
      arrowIcon = null;
    }
  }

  private void checkEmptyIcon(JComponent comp) {
    myMaxGutterIconWidth = getAllowedIcon() == null && IdeaPopupMenuUI.hideEmptyIcon(comp) ? 0 : myMaxGutterIconWidth2;
  }

  @Override
  public void paint(Graphics g, JComponent comp) {
    checkArrowIcon();
    UISettings.setupAntialiasing(g);
    JMenuItem jmenuitem = (JMenuItem)comp;
    ButtonModel buttonmodel = jmenuitem.getModel();
    int mnemonicIndex = jmenuitem.getDisplayedMnemonicIndex();
    Icon icon1 = getIcon();
    Icon icon2 = getAllowedIcon();
    checkEmptyIcon(comp);
    int j1 = jmenuitem.getWidth();
    int k1 = jmenuitem.getHeight();
    Insets insets = comp.getInsets();
    initBounds();
    f.setBounds(0, 0, j1, k1);
    JBInsets.removeFrom(f, insets);
    Font font = g.getFont();
    Font font1 = comp.getFont();
    g.setFont(font1);
    FontMetrics fontmetrics = g.getFontMetrics(font1);
    FontMetrics fontmetrics1 = g.getFontMetrics(acceleratorFont);
    String keyStrokeText = getKeyStrokeText(jmenuitem);
    String s1 = layoutMenuItem(fontmetrics, jmenuitem.getText(), fontmetrics1, keyStrokeText, icon1, icon2, arrowIcon, jmenuitem.getVerticalAlignment(), jmenuitem.getHorizontalAlignment(), jmenuitem.getVerticalTextPosition(), jmenuitem.getHorizontalTextPosition(), f, l, j, c, h, d, jmenuitem.getText() != null ? defaultTextIconGap : 0, defaultTextIconGap);
    Color color2 = g.getColor();
    if (comp.isOpaque() || StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      g.setColor(jmenuitem.getBackground());
      g.fillRect(0, 0, j1, k1);
      if (isSelected(jmenuitem)) {
        g.setColor(selectionBackground);
        if (icon2 != null && !(StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())) {
          g.fillRect(k, 0, j1 - k, k1);
        }
        else if (ExperimentalUI.isNewUI() || IdeaPopupMenuUI.isRoundBorder()) {
          IdeaMenuUI.paintRoundSelection(g, comp, j1, k1);
        }
        else {
          g.fillRect(0, 0, j1, k1);
        }
      }
      g.setColor(color2);
    }
    if (icon2 != null){
      if (isSelected(jmenuitem)) {
        g.setColor(selectionForeground);
      }
      else {
        g.setColor(jmenuitem.getForeground());
      }
      if (useCheckAndArrow()) {
        IconUtil.paintSelectionAwareIcon(icon2, jmenuitem, g, h.x, h.y, isSelected(jmenuitem));
      }
      g.setColor(color2);
      if (menuItem.isArmed()) {
        drawIconBorder(g);
      }
    }
    if (icon1 != null) {
      if (!buttonmodel.isEnabled()){
        icon1 = jmenuitem.getDisabledIcon();
      }
      else
        if (buttonmodel.isPressed() && buttonmodel.isArmed()) {
          icon1 = jmenuitem.getPressedIcon();
          if (icon1 == null){
            icon1 = jmenuitem.getIcon();
          }
        }
      if (icon1 != null) {
        IconUtil.paintSelectionAwareIcon(icon1, jmenuitem, g, l.x, l.y, isSelected(jmenuitem));
      }
    }
    if (s1 != null && s1.length() > 0) {
      if (buttonmodel.isEnabled()) {
        if (isSelected(jmenuitem)) {
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
    if (keyStrokeText != null && !keyStrokeText.isEmpty()) {
      g.setFont(acceleratorFont);
      if (buttonmodel.isEnabled()){
        if (UIUtil.isUnderAquaBasedLookAndFeel() && isSelected(jmenuitem)) {
          g.setColor(selectionForeground);
        }
        else {
          if (isSelected(jmenuitem)) {
            g.setColor(acceleratorSelectionForeground);
          }
          else {
            g.setColor(acceleratorForeground);
          }
        }
        BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x, c.y + fontmetrics1.getAscent());
      }
      else
        if (disabledForeground != null) {
          g.setColor(disabledForeground);
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x, c.y + fontmetrics1.getAscent());
        }
        else {
          g.setColor(jmenuitem.getBackground().brighter());
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x, c.y + fontmetrics1.getAscent());
          g.setColor(jmenuitem.getBackground().darker());
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, c.x - 1, (c.y + fontmetrics1.getAscent()) - 1);
        }
    }
    if (arrowIcon != null) {
      if (isSelected(jmenuitem)) {
        g.setColor(selectionForeground);
      }
      if (useCheckAndArrow()){
        IconUtil.paintSelectionAwareIcon(arrowIcon, comp, g, d.x, d.y, isSelected(jmenuitem));
      }
    }
    g.setColor(color2);
    g.setFont(font);
  }

  private static @NlsSafe String getKeyStrokeText(@NotNull JMenuItem item) {
    return item instanceof ActionMenuItem
           ? ((ActionMenuItem)item).getFirstShortcutText()
           : getKeyStrokeText(item.getAccelerator());
  }

  @NlsSafe
  private static String getKeyStrokeText(KeyStroke keystroke) {
    String s1 = "";
    if (keystroke != null){
      int j1 = keystroke.getModifiers();
      if (j1 > 0){
        if (SystemInfoRt.isMac) {
          try {
            Class<?> appleLaf = Class.forName(AQUA_LOOK_AND_FEEL_CLASS_NAME);
            Method getModifiers = appleLaf.getMethod(GET_KEY_MODIFIERS_TEXT, int.class, boolean.class);
            s1 = (String)getModifiers.invoke(appleLaf, new Object[] {Integer.valueOf(j1), Boolean.FALSE});
          }
          catch (Exception e) {
            s1 = KeymapUtil.getKeyModifiersTextForMacOSLeopard(j1);
          }
        }
        else {
          s1 = KeyEvent.getKeyModifiersText(j1) + '+';
        }

      }
      s1 += KeyEvent.getKeyText(keystroke.getKeyCode());
    }
    return s1;
  }

  private boolean useCheckAndArrow() {
    if ((menuItem instanceof JMenu) && ((JMenu)menuItem).isTopLevelMenu()){
      return false;
    }
    return true;
  }

  @Override
  public MenuElement[] getPath() {
    MenuSelectionManager menuselectionmanager = MenuSelectionManager.defaultManager();
    MenuElement[] amenuelement = menuselectionmanager.getSelectedPath();
    int i1 = amenuelement.length;
    if (i1 == 0){
      return new MenuElement[0];
    }
    Container container = menuItem.getParent();
    MenuElement[] amenuelement1;
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

  @Override
  public Dimension getMinimumSize(JComponent jcomponent) {
    return null;
  }

  private String layoutMenuItem(
    FontMetrics fontmetrics,
    @NlsContexts.Command String text,
    FontMetrics fontmetrics1,
    @NlsContexts.Label String keyStrokeText,
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
    if (keyStrokeText == null || keyStrokeText.isEmpty()){
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

    acceleratorRect.x = viewRect.x + viewRect.width - arrowIconRect.width - (arrowIconRect.width > 0 ? menuItemGap : 0) - acceleratorRect.width;
    acceleratorRect.y = (viewRect.y + viewRect.height / 2) - acceleratorRect.height / 2;

    // Position the Check and Arrow Icons

    if (useCheckAndArrow()){
      arrowIconRect.x = viewRect.x + viewRect.width - arrowIconRect.width;
      arrowIconRect.y = (labelRect.y + labelRect.height / 2) - arrowIconRect.height / 2;
      if (checkIcon != null){
        checkIconRect.y = (labelRect.y + labelRect.height / 2) - checkIconRect.height / 2;
        checkIconRect.x += (viewRect.x + myMaxGutterIconWidth / 2) - checkIcon.getIconWidth() / 2;
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

  @Override
  public Dimension getPreferredSize(JComponent comp) {
    checkArrowIcon();
    JMenuItem jmenuitem = (JMenuItem)comp;
    Icon icon1 = getIcon();
    Icon icon2 = getAllowedIcon();
    checkEmptyIcon(comp);
    String text = jmenuitem.getText();
    String keyStrokeText = getKeyStrokeText(jmenuitem);
    Font font = jmenuitem.getFont();
    FontMetrics fontmetrics = comp.getFontMetrics(font);
    FontMetrics fontmetrics1 = comp.getFontMetrics(acceleratorFont);
    initBounds();
    layoutMenuItem(fontmetrics, text, fontmetrics1, keyStrokeText, icon1, icon2, arrowIcon, jmenuitem.getVerticalAlignment(), jmenuitem.getHorizontalAlignment(), jmenuitem.getVerticalTextPosition(), jmenuitem.getHorizontalTextPosition(), f, l, j, c, h, d, text != null ? defaultTextIconGap : 0, defaultTextIconGap);
    Rectangle i = new Rectangle();
    i.setBounds(j);
    SwingUtilities.computeUnion(l.x, l.y, l.width, l.height, i);
    if (!(keyStrokeText == null || keyStrokeText.isEmpty())){
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

    return IdeaMenuUI.patchPreferredSize(comp, i.getSize());
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

  private static void initBounds() {
    l.setBounds(b);
    j.setBounds(b);
    c.setBounds(b);
    h.setBounds(b);
    d.setBounds(b);
    f.setBounds(0, 0, 32767, 32767);
  }

  private Icon getAllowedIcon() {
    Icon icon = !menuItem.isEnabled() ? menuItem.getDisabledIcon() : isSelected(menuItem) ? menuItem.getSelectedIcon() : menuItem.getIcon();
    if (icon != null && icon.getIconWidth() > myMaxGutterIconWidth){
      icon = null;
    }
    return icon;
  }

  @Override
  public Dimension getMaximumSize(JComponent comp) {
    return null;
  }

  @Override
  public void update(Graphics g, JComponent comp) {
    paint(g, comp);
  }

  /** Copied from BasicMenuItemUI */
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
  private void doClick(MenuSelectionManager msm, MouseEvent e) {
    // Auditory cue
    if (!isInternalFrameSystemMenu()) {
      @NonNls ActionMap map = menuItem.getActionMap();
      if (map != null) {
        Action audioAction = map.get(getPropertyPrefix() + ".commandSound");
        if (audioAction != null) {
          // pass off firing the Action to a utility method
          BasicLookAndFeel lf = (BasicLookAndFeel)UIManager.getLookAndFeel();
          // It's a hack. The method BasicLookAndFeel.playSound has protected access, so
          // it's impossible to normally invoke it.
          try {
            Method playSoundMethod = BasicLookAndFeel.class.getDeclaredMethod(PLAY_SOUND_METHOD, Action.class);
            playSoundMethod.setAccessible(true);
            playSoundMethod.invoke(lf, audioAction);
          }
          catch (Exception ignored) {
          }
        }
      }
    }
    // Visual feedback
    if (msm == null) {
      msm = MenuSelectionManager.defaultManager();
    }
    ActionMenuItem item = (ActionMenuItem)menuItem;
    AnAction action = item.getAnAction();
    if (ActionPlaces.MAIN_MENU.equals(item.getPlace()) && ApplicationManager.getApplication() != null) {
      MainMenuCollector.getInstance().record(action);
    }
    if (!item.isKeepMenuOpen()) {
      msm.clearSelectedPath();
    }
    ActionEvent event = new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, null, e.getWhen(), e.getModifiers());
    item.fireActionPerformed(event);
    if (item.isKeepMenuOpen()) {
      Container parent = item.getParent();
      if (parent instanceof JComponent) {
        //Fake event to trigger update in ActionPopupMenuImpl.MyMenu
        ((JComponent)parent).putClientProperty(KEEP_MENU_OPEN_PROP, System.currentTimeMillis());
      }
    }
  }

  /**
   * To update items in case of multiple choice when there are dependencies between items like:
   * <ol>
   *   <li>Selected A means unselected B and vise versa</li>
   *   <li>Selected/unselected A means enabled/disabled B</li>
   * </ol>
   */
  public static void registerMultiChoiceSupport(@NotNull JPopupMenu component, @NotNull Consumer<? super JPopupMenu> onUpdate) {
    component.addPropertyChangeListener(KEEP_MENU_OPEN_PROP, evt -> onUpdate.accept((JPopupMenu)evt.getSource()));
  }

  @Override
  protected MouseInputListener createMouseInputListener(JComponent c){
    return new MyMouseInputHandler();
  }

  private class MyMouseInputHandler extends MouseInputHandler{
    @Override
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

  @Override
  protected MenuDragMouseListener createMenuDragMouseListener(JComponent c){
    return new MyMenuDragMouseHandler();
  }

  private class MyMenuDragMouseHandler implements MenuDragMouseListener {
    @Override
    public void menuDragMouseEntered(MenuDragMouseEvent e){}

    @Override
    public void menuDragMouseDragged(MenuDragMouseEvent e){
      MenuSelectionManager manager=e.getMenuSelectionManager();
      MenuElement[] path = e.getPath();
      manager.setSelectedPath(path);
    }

    @Override
    public void menuDragMouseExited(MenuDragMouseEvent e){}

    @Override
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
