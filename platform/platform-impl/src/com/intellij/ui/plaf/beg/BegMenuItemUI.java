
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
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
@ApiStatus.Internal
public final class BegMenuItemUI extends BasicMenuItemUI {
  private static final String KEEP_MENU_OPEN_PROP = "BegMenuItemUI.keep-menu-open";

  private static final Rectangle ourEmptyRect = new Rectangle(0, 0, 0, 0);
  private static final Rectangle ourTextRect = new Rectangle();
  private static final Rectangle ourArrowIconRect = new Rectangle();
  private static final Rectangle ourAcceleratorRect = new Rectangle();  // the shortcut rect
  private static final Rectangle ourCheckIconRect = new Rectangle();
  private static final Rectangle ourIconRect = new Rectangle();
  private static final Rectangle ourSecondaryIconRect = new Rectangle();
  private static final Rectangle ourViewRect = new Rectangle(32767, 32767);

  private int myMaxGutterIconWidth;
  private int myMaxGutterIconWidth2;

  public static final @NonNls String PLAY_SOUND_METHOD = "playSound";

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

  static boolean isSelected(JMenuItem item) {
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
    myMaxGutterIconWidth = getCheckIcon() == null && IdeaPopupMenuUI.hideEmptyIcon(comp) ? 0 : myMaxGutterIconWidth2;
  }

  @Override
  public void paint(Graphics g, JComponent comp) {
    checkArrowIcon();
    UISettings.setupAntialiasing(g);
    JMenuItem menuItem = (JMenuItem)comp;
    ButtonModel buttonModel = menuItem.getModel();
    Icon icon = getIcon();
    Icon secondaryIcon = getSecondaryIcon();
    Icon checkIcon = getCheckIcon();
    checkEmptyIcon(comp);
    int menuWidth = menuItem.getWidth();
    int menuHeight = menuItem.getHeight();
    initBounds();
    ourViewRect.setBounds(0, 0, menuWidth, menuHeight);
    JBInsets.removeFrom(ourViewRect, comp.getInsets());
    Font oldFont = g.getFont();
    Font font = comp.getFont();
    g.setFont(font);
    FontMetrics fontMetrics = g.getFontMetrics(font);
    FontMetrics keyStrokeMetrics = g.getFontMetrics(acceleratorFont);
    String keyStrokeText = getKeyStrokeText(menuItem);
    String text = layoutMenuItem(fontMetrics, menuItem.getText(), keyStrokeMetrics, keyStrokeText, icon, secondaryIcon, checkIcon, arrowIcon,
                                 menuItem.getVerticalAlignment(), menuItem.getHorizontalAlignment(), menuItem.getVerticalTextPosition(),
                                 menuItem.getHorizontalTextPosition(),
                                 ourViewRect, ourIconRect, ourSecondaryIconRect, ourTextRect, ourAcceleratorRect, ourCheckIconRect, ourArrowIconRect,
                                 menuItem.getText() != null ? defaultTextIconGap : 0, defaultTextIconGap);
    Color oldColor = g.getColor();
    if (comp.isOpaque()) {
      g.setColor(menuItem.getBackground());
      g.fillRect(0, 0, menuWidth, menuHeight);
      if (isSelected(menuItem)) {
        g.setColor(selectionBackground);
        if (ExperimentalUI.isNewUI() || IdeaPopupMenuUI.isRoundBorder()) {
          IdeaMenuUI.paintRoundSelection(g, comp, menuWidth, menuHeight);
        }
        else {
          g.fillRect(0, 0, menuWidth, menuHeight);
        }
      }
      g.setColor(oldColor);
    }
    if (checkIcon != null) {
      if (isSelected(menuItem)) {
        g.setColor(selectionForeground);
      }
      else {
        g.setColor(menuItem.getForeground());
      }
      if (useCheckAndArrow()) {
        IconUtil.paintSelectionAwareIcon(checkIcon, menuItem, g, ourCheckIconRect.x, ourCheckIconRect.y, isSelected(menuItem));
      }
      g.setColor(oldColor);
    }
    if (icon != null) {
      if (!buttonModel.isEnabled()) {
        icon = menuItem.getDisabledIcon();
      }
      else if (buttonModel.isPressed() && buttonModel.isArmed()) {
        icon = menuItem.getPressedIcon();
        if (icon == null) {
          icon = menuItem.getIcon();
        }
      }
      if (icon != null) {
        IconUtil.paintSelectionAwareIcon(icon, menuItem, g, ourIconRect.x, ourIconRect.y, isSelected(menuItem));
      }
    }
    if (text != null && !text.isEmpty()) {
      int mnemonicIndex = menuItem.getDisplayedMnemonicIndex();
      if (buttonModel.isEnabled()) {
        if (isSelected(menuItem)) {
          g.setColor(selectionForeground);
        }
        else{
          g.setColor(menuItem.getForeground());
        }
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex, ourTextRect.x, ourTextRect.y + fontMetrics.getAscent());
      }
      else {
        final Object disabledForeground = UIUtil.getMenuItemDisabledForegroundObject();
        if (disabledForeground instanceof Color disabledColor) {
          g.setColor(disabledColor);
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex, ourTextRect.x, ourTextRect.y + fontMetrics.getAscent());
        }
        else{
          g.setColor(menuItem.getBackground().brighter());
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex, ourTextRect.x, ourTextRect.y + fontMetrics.getAscent());
          g.setColor(menuItem.getBackground().darker());
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex, ourTextRect.x - 1,
                                                       (ourTextRect.y + fontMetrics.getAscent()) - 1);
        }
      }
    }
    if (secondaryIcon != null) {
      IconUtil.paintSelectionAwareIcon(secondaryIcon, menuItem, g, ourSecondaryIconRect.x, ourSecondaryIconRect.y, isSelected(menuItem));
    }
    if (keyStrokeText != null && !keyStrokeText.isEmpty()) {
      g.setFont(acceleratorFont);
      if (buttonModel.isEnabled()) {
        if (UIUtil.isUnderAquaBasedLookAndFeel() && isSelected(menuItem)) {
          g.setColor(selectionForeground);
        }
        else {
          if (isSelected(menuItem)) {
            g.setColor(acceleratorSelectionForeground);
          }
          else {
            g.setColor(acceleratorForeground);
          }
        }
        BasicGraphicsUtils.drawString(g, keyStrokeText, 0, ourAcceleratorRect.x, ourAcceleratorRect.y + keyStrokeMetrics.getAscent());
      }
      else
        if (disabledForeground != null) {
          g.setColor(disabledForeground);
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, ourAcceleratorRect.x, ourAcceleratorRect.y + keyStrokeMetrics.getAscent());
        }
        else {
          g.setColor(menuItem.getBackground().brighter());
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, ourAcceleratorRect.x, ourAcceleratorRect.y + keyStrokeMetrics.getAscent());
          g.setColor(menuItem.getBackground().darker());
          BasicGraphicsUtils.drawString(g, keyStrokeText, 0, ourAcceleratorRect.x - 1,
                                        (ourAcceleratorRect.y + keyStrokeMetrics.getAscent()) - 1);
        }
    }
    if (arrowIcon != null) {
      if (isSelected(menuItem)) {
        g.setColor(selectionForeground);
      }
      if (useCheckAndArrow()){
        IconUtil.paintSelectionAwareIcon(arrowIcon, comp, g, ourArrowIconRect.x, ourArrowIconRect.y, isSelected(menuItem));
      }
    }
    g.setColor(oldColor);
    g.setFont(oldFont);
  }

  private static @NlsSafe String getKeyStrokeText(@NotNull JMenuItem item) {
    return item instanceof ActionMenuItem
           ? ((ActionMenuItem)item).getFirstShortcutText()
           : getKeyStrokeText(item.getAccelerator());
  }

  private static @NlsSafe String getKeyStrokeText(KeyStroke keystroke) {
    String s1 = "";
    if (keystroke != null){
      int j1 = keystroke.getModifiers();
      if (j1 > 0){
        if (ClientSystemInfo.isMac()) {
          s1 = MacKeymapUtil.getKeyModifiersTextForMacOSLeopard(j1);
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

  @SuppressWarnings("SameParameterValue")
  private String layoutMenuItem(
    FontMetrics fontMetrics,
    @NlsContexts.Command String text,
    FontMetrics keyStrokeMetrics,
    @NlsContexts.Label String keyStrokeText,
    Icon icon,
    Icon secondaryIcon,
    Icon checkIcon,
    Icon arrowIcon,
    int verticalAlignment,
    int horizontalAlignment,
    int verticalTextPosition,
    int horizontalTextPosition,
    Rectangle viewRect,
    Rectangle iconRect,
    Rectangle secondaryIconRect,
    Rectangle textRect,
    Rectangle acceleratorRect,
    Rectangle checkIconRect,
    Rectangle arrowIconRect,
    int textIconGap,
    int menuItemGap
  ) {
    SwingUtilities.layoutCompoundLabel(menuItem, fontMetrics, text, icon, verticalAlignment, horizontalAlignment, verticalTextPosition,
                                       horizontalTextPosition, viewRect, iconRect, textRect, textIconGap);
    if (keyStrokeText == null || keyStrokeText.isEmpty()){
      acceleratorRect.width = acceleratorRect.height = 0;
    }
    else{
      acceleratorRect.width = SwingUtilities.computeStringWidth(keyStrokeMetrics, keyStrokeText);
      acceleratorRect.height = keyStrokeMetrics.getHeight();
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

    // Position the secondary icon
    if (secondaryIcon != null) {
      secondaryIconRect.width = secondaryIcon.getIconWidth();
      secondaryIconRect.height = secondaryIcon.getIconHeight();
      secondaryIconRect.x = labelRect.x + labelRect.width + menuItemGap;
      secondaryIconRect.y = (labelRect.y + labelRect.height / 2) - secondaryIcon.getIconHeight() / 2;
    }

    // Position the Accelerator (shortcut) text rect
    acceleratorRect.x = viewRect.x + viewRect.width - arrowIconRect.width - (arrowIconRect.width > 0 ? menuItemGap : 0) - acceleratorRect.width;
    acceleratorRect.y = (viewRect.y + viewRect.height / 2) - acceleratorRect.height / 2;

    // Position the Check and Arrow Icons
    if (useCheckAndArrow()){
      arrowIconRect.x = viewRect.x + viewRect.width - arrowIconRect.width;
      arrowIconRect.y = (labelRect.y + labelRect.height / 2) - arrowIconRect.height / 2;
      if (checkIcon != null){
        checkIconRect.y = (labelRect.y + labelRect.height / 2) - checkIconRect.height / 2;
        checkIconRect.x += (viewRect.x + myMaxGutterIconWidth / 2) - checkIcon.getIconWidth() / 2;
      }
      else{
        checkIconRect.x = checkIconRect.y = 0;
      }
    }
    return text;
  }

  private Icon getIcon() {
    Icon icon = menuItem.getIcon();
    if (icon != null && getCheckIcon() != null) {
      icon = null;
    }
    return icon;
  }

  @Override
  public Dimension getPreferredSize(JComponent comp) {
    checkArrowIcon();
    JMenuItem menuItem = (JMenuItem)comp;
    Icon icon = getIcon();
    Icon secondaryIcon = getSecondaryIcon();
    Icon checkIcon = getCheckIcon();
    checkEmptyIcon(comp);
    String text = menuItem.getText();
    String keyStrokeText = getKeyStrokeText(menuItem);
    FontMetrics fontMetrics = comp.getFontMetrics(menuItem.getFont());
    FontMetrics keyStrokeMetrics = comp.getFontMetrics(acceleratorFont);
    initBounds();
    layoutMenuItem(fontMetrics, text, keyStrokeMetrics, keyStrokeText, icon, secondaryIcon, checkIcon, arrowIcon, menuItem.getVerticalAlignment(),
                   menuItem.getHorizontalAlignment(), menuItem.getVerticalTextPosition(), menuItem.getHorizontalTextPosition(),
                   ourViewRect, ourIconRect, ourSecondaryIconRect, ourTextRect, ourAcceleratorRect, ourCheckIconRect, ourArrowIconRect,
                   text != null ? defaultTextIconGap : 0, defaultTextIconGap);
    Rectangle rect = new Rectangle();
    rect.setBounds(ourTextRect);
    SwingUtilities.computeUnion(ourIconRect.x, ourIconRect.y, ourIconRect.width, ourIconRect.height, rect);
    if (!(keyStrokeText == null || keyStrokeText.isEmpty())){
      rect.width += ourAcceleratorRect.width;
      rect.width += 7 * defaultTextIconGap;
    }
    if (secondaryIcon != null) {
      rect.width += ourSecondaryIconRect.width + 2 * defaultTextIconGap;
    }
    if (useCheckAndArrow()){
      rect.width += myMaxGutterIconWidth;
      rect.width += defaultTextIconGap;
      rect.width += defaultTextIconGap;
      rect.width += ourArrowIconRect.width;
    }
    rect.width += 2 * defaultTextIconGap;
    Insets insets = menuItem.getInsets();
    if (insets != null){
      rect.width += insets.left + insets.right;
      rect.height += insets.top + insets.bottom;
    }
    if (rect.width % 2 == 0) {
      rect.width++;
    }
    if (rect.height % 2 == 0) {
      rect.height++;
    }

    return IdeaMenuUI.patchPreferredSize(comp, rect.getSize());
  }

  private static void initBounds() {
    ourIconRect.setBounds(ourEmptyRect);
    ourSecondaryIconRect.setBounds(ourEmptyRect);
    ourTextRect.setBounds(ourEmptyRect);
    ourAcceleratorRect.setBounds(ourEmptyRect);
    ourCheckIconRect.setBounds(ourEmptyRect);
    ourArrowIconRect.setBounds(ourEmptyRect);
    ourViewRect.setBounds(0, 0, 32767, 32767);
  }

  private Icon getCheckIcon() {
    Icon icon = !menuItem.isEnabled() ? menuItem.getDisabledIcon() : isSelected(menuItem) ? menuItem.getSelectedIcon() : menuItem.getIcon();
    if (icon != null && icon.getIconWidth() > myMaxGutterIconWidth){
      icon = null;
    }
    return icon;
  }

  private Icon getSecondaryIcon() {
    return menuItem instanceof ActionMenuItem actionMenuItem ? actionMenuItem.getSecondaryIcon() : null;
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
    boolean keepMenuOpen = Utils.isKeepPopupOpen(item.getKeepPopupOnPerform(), e);
    if (!keepMenuOpen) {
      msm.clearSelectedPath();
    }
    ActionEvent event = new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, null, e.getWhen(), e.getModifiers());
    item.fireActionPerformed(event);
    if (keepMenuOpen) {
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

  private final class MyMouseInputHandler extends MouseInputHandler {
    @Override
    public void mouseReleased(MouseEvent e){
      MenuSelectionManager manager=MenuSelectionManager.defaultManager();
      Point p = e.getPoint();
      if (p.x >= 0 && p.x < menuItem.getWidth() && p.y >= 0 && p.y < menuItem.getHeight()) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          doClick(manager, e);
        }
      }
      else {
        manager.processMouseEvent(e);
      }
    }
  }

  @Override
  protected MenuDragMouseListener createMenuDragMouseListener(JComponent c){
    return new MyMenuDragMouseHandler();
  }

  private final class MyMenuDragMouseHandler implements MenuDragMouseListener {
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
      if (!AdvancedSettings.getBoolean("ide.trigger.menu.actions.on.rmb.release")) return;
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
