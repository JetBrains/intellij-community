// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.DarculaMenuItemBorder;
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicMenuUI;
import java.awt.*;

import static com.intellij.ui.plaf.beg.BegMenuItemUI.isSelected;

public class IdeaMenuUI extends BasicMenuUI {
  private static final Rectangle ourZeroRect = new Rectangle(0, 0, 0, 0);
  private static final Rectangle ourTextRect = new Rectangle();
  private static final Rectangle ourArrowIconRect = new Rectangle();
  private int myMaxGutterIconWidth;
  private int myMaxGutterIconWidth2;
  private int a;
  private int k;
  private int e;
  private static final Rectangle ourAcceleratorRect = new Rectangle();
  private static final Rectangle ourCheckIconRect = new Rectangle();
  private static final Rectangle ourIconRect = new Rectangle();
  private static final Rectangle ourViewRect = new Rectangle(32767, 32767);

  /** invoked by reflection */
  public static ComponentUI createUI(JComponent component) {
    return new IdeaMenuUI();
  }

  public static void paintRoundSelection(Graphics g, Component c, int width, int height) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    int radius;
    JBInsets outerInsets;
    if (IdeaPopupMenuUI.isPartOfPopupMenu(c)) {
      radius = JBUI.CurrentTheme.PopupMenu.Selection.ARC.get();
      outerInsets = JBUI.CurrentTheme.PopupMenu.Selection.outerInsets();
    }
    else if (IdeaPopupMenuUI.isMenuBarItem(c)) {
      outerInsets = DarculaMenuItemBorder.menuBarItemOuterInsets();
      radius = 0;
    }
    else {
      radius = JBUI.CurrentTheme.Menu.Selection.ARC.get();
      outerInsets = JBUI.CurrentTheme.Menu.Selection.outerInsets();
    }

    g.fillRoundRect(outerInsets.left, outerInsets.top, width - outerInsets.width(),
                    height - outerInsets.height(), radius, radius);
    config.restore();
  }

  public static @NotNull Dimension patchPreferredSize(Component c, Dimension preferredSize) {
    if (ExperimentalUI.isNewUI() && !IdeaPopupMenuUI.isMenuBarItem(c)) {
      JBInsets outerInsets = IdeaPopupMenuUI.isPartOfPopupMenu(c)
                             ? JBUI.CurrentTheme.PopupMenu.Selection.outerInsets()
                             : JBUI.CurrentTheme.Menu.Selection.outerInsets();
      return new Dimension(preferredSize.width, JBUI.CurrentTheme.List.rowHeight() + outerInsets.height());
    }

    return preferredSize;
  }

  public IdeaMenuUI() {
    myMaxGutterIconWidth = JBUIScale.scale(18);
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    Integer integer = UIUtil.getPropertyMaxGutterIconWidth(getPropertyPrefix());
    if (integer != null){
      myMaxGutterIconWidth2 = myMaxGutterIconWidth = integer.intValue();
    }

    selectionBackground = getDefaultSelectionBackground();
    if (isHeaderMenu()) {
      menuItem.setBackground(getMenuBackgroundColor());
      menuItem.setForeground(JBColor.namedColor("MainMenu.foreground", UIManager.getColor("Menu.foreground")));
      selectionForeground = JBColor.namedColor("MainMenu.selectionForeground", selectionForeground);
      selectionBackground = JBColor.namedColor("MainMenu.selectionBackground", selectionBackground);
    }
  }

  @ApiStatus.Internal
  public static Color getDefaultSelectionBackground() {
    return JBColor.namedColor("Menu.selectionBackground", UIUtil.getListSelectionBackground(true));
  }

  @ApiStatus.Internal
  public void setSelectionBackground(Color selectionBackground) {
    this.selectionBackground = selectionBackground;
  }

  @ApiStatus.Internal
  public static @NotNull Color getMenuBackgroundColor() {
    return JBColor.namedColor("MainMenu.background", UIManager.getColor("Menu.background"));
  }

  private boolean isHeaderMenu() {
    return menuItem instanceof ActionMenu actionMenu && actionMenu.isHeaderMenuItem;
  }

  private void checkEmptyIcon(JComponent comp) {
    myMaxGutterIconWidth = getAllowedIcon() == null && IdeaPopupMenuUI.hideEmptyIcon(comp) ? 0 : myMaxGutterIconWidth2;
  }

  @Override
  public void paint(Graphics g, JComponent comp) {
    UISettings.setupAntialiasing(g);
    JMenu jMenu = (JMenu)comp;
    ButtonModel buttonmodel = jMenu.getModel();
    int mnemonicIndex = jMenu.getDisplayedMnemonicIndex();
    Icon icon = getIcon();
    Icon allowedIcon = getAllowedIcon();
    checkEmptyIcon(comp);
    Insets insets = comp.getInsets();
    resetRects();

    ourViewRect.setBounds(0, 0, jMenu.getWidth(), jMenu.getHeight());
    JBInsets.removeFrom(ourViewRect, insets);

    Font font = g.getFont();
    Font font1 = comp.getFont();
    g.setFont(font1);
    FontMetrics fontmetrics = g.getFontMetrics(font1);
    String s1 = layoutMenuItem(
      fontmetrics,
      jMenu.getText(),
      icon,
      allowedIcon,
      arrowIcon,
      jMenu.getVerticalAlignment(),
      jMenu.getHorizontalAlignment(),
      jMenu.getVerticalTextPosition(),
      jMenu.getHorizontalTextPosition(),
      ourViewRect,
      ourIconRect,
      ourTextRect,
      ourAcceleratorRect,
      ourCheckIconRect,
      ourArrowIconRect,
      jMenu.getText() != null ? defaultTextIconGap : 0,
      defaultTextIconGap
    );
    Color mainColor = g.getColor();
    fillBackground(g, comp, jMenu, buttonmodel, allowedIcon);
    if (allowedIcon != null){
      if (buttonmodel.isArmed() || buttonmodel.isSelected()){
        g.setColor(selectionForeground);
      }
      else{
        g.setColor(jMenu.getForeground());
      }
      if (useCheckAndArrow()){
        allowedIcon.paintIcon(comp, g, ourCheckIconRect.x, ourCheckIconRect.y);
      }
      g.setColor(mainColor);
      if (menuItem.isArmed()){
        drawIconBorder(g);
      }
    }
    if (icon != null){
      if (!buttonmodel.isEnabled()){
        icon = jMenu.getDisabledIcon();
      }
      else
        if (buttonmodel.isPressed() && buttonmodel.isArmed()){
          icon = jMenu.getPressedIcon();
          if (icon == null){
            icon = jMenu.getIcon();
          }
        }
      if (icon != null){
        icon.paintIcon(comp, g, ourIconRect.x, ourIconRect.y);
      }
    }
    if (s1 != null && !s1.isEmpty()){
      if (buttonmodel.isEnabled()){
        if (buttonmodel.isArmed() || buttonmodel.isSelected()){
          g.setColor(selectionForeground);
        }
        else{
          g.setColor(jMenu.getForeground());
        }
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, ourTextRect.x, ourTextRect.y + fontmetrics.getAscent());
      }
      else {
        final Color disabledForeground = UIUtil.getMenuItemDisabledForeground();
        if (disabledForeground != null){
          g.setColor(disabledForeground);
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, ourTextRect.x, ourTextRect.y + fontmetrics.getAscent());
        }
        else{
          g.setColor(jMenu.getBackground().brighter());
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, ourTextRect.x, ourTextRect.y + fontmetrics.getAscent());
          g.setColor(jMenu.getBackground().darker());
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, s1, mnemonicIndex, ourTextRect.x - 1, (ourTextRect.y + fontmetrics.getAscent()) - 1);
        }
      }
    }
    if (arrowIcon != null){
      if (SystemInfo.isMac) {
        ourArrowIconRect.y += JBUIScale.scale(1);
      }

      if (buttonmodel.isArmed() || buttonmodel.isSelected()){
        g.setColor(selectionForeground);
      }

      if (useCheckAndArrow()) {
        arrowIcon.paintIcon(comp, g, ourArrowIconRect.x, ourArrowIconRect.y);
      }
    }
    g.setColor(mainColor);
    g.setFont(font);
  }

  private void fillBackground(Graphics g, JComponent comp, JMenu jMenu, ButtonModel buttonmodel, Icon allowedIcon) {
    if (comp.isOpaque()) {
      g.setColor(jMenu.getBackground());
      g.fillRect(0, 0, jMenu.getWidth(), jMenu.getHeight());
    }
    if (buttonmodel.isArmed() || buttonmodel.isSelected()){
      paintHover(g, comp, jMenu, allowedIcon);
    }
  }

  protected final void paintHover(Graphics g, JComponent comp, JMenu jMenu, Icon allowedIcon) {
    g.setColor(selectionBackground);
    if (allowedIcon != null && !(UIUtil.isUnderIntelliJLaF() || StartupUiUtil.isUnderDarcula())) {
      g.fillRect(k, 0, jMenu.getWidth() - k, jMenu.getHeight());
    }
    else if (ExperimentalUI.isNewUI() || IdeaPopupMenuUI.isRoundBorder()) {
      paintRoundSelection(g, comp, jMenu.getWidth(), jMenu.getHeight());
    }
    else {
      g.fillRect(0, 0, jMenu.getWidth(), jMenu.getHeight());
    }
  }

  private boolean useCheckAndArrow() {
    return !((JMenu)menuItem).isTopLevelMenu();
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

  private String layoutMenuItem(
    FontMetrics fontmetrics,
    @Nls String text,
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
    acceleratorRect.width = acceleratorRect.height = 0;

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

    acceleratorRect.x = viewRect.x + viewRect.width - arrowIconRect.width - menuItemGap - acceleratorRect.width;
    acceleratorRect.y = (viewRect.y + viewRect.height / 2) - acceleratorRect.height / 2;

    // Position the Check and Arrow Icons

    if (useCheckAndArrow()){
      arrowIconRect.x = viewRect.x + viewRect.width - arrowIconRect.width;
      arrowIconRect.y = (labelRect.y + labelRect.height / 2) - arrowIconRect.height / 2;
      if (checkIcon != null){
        checkIconRect.y = (labelRect.y + labelRect.height / 2) - checkIconRect.height / 2;
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

  @Override
  protected Dimension getPreferredMenuItemSize(
    JComponent comp,
    Icon checkIcon,
    Icon arrowIcon,
    int defaultTextIconGap
  ){
    JMenu jMenu = (JMenu)comp;
    Icon icon1 = getIcon();
    Icon icon2 = getAllowedIcon();
    checkEmptyIcon(comp);
    String text = jMenu.getText();
    Font font = jMenu.getFont();
    FontMetrics fontmetrics = jMenu.getToolkit().getFontMetrics(font);
    resetRects();
    layoutMenuItem(
      fontmetrics,
      text,
      icon1,
      icon2,
      arrowIcon,
      jMenu.getVerticalAlignment(),
      jMenu.getHorizontalAlignment(),
      jMenu.getVerticalTextPosition(),
      jMenu.getHorizontalTextPosition(),
      ourViewRect,
      ourIconRect,
      ourTextRect,
      ourAcceleratorRect,
      ourCheckIconRect,
      ourArrowIconRect,
      text != null ? defaultTextIconGap : 0,
      defaultTextIconGap
    );
    Rectangle ourPreferredSizeRect = new Rectangle();
    ourPreferredSizeRect.setBounds(ourTextRect);
    SwingUtilities.computeUnion(ourIconRect.x, ourIconRect.y, ourIconRect.width, ourIconRect.height, ourPreferredSizeRect);
    if (useCheckAndArrow()){
      ourPreferredSizeRect.width += myMaxGutterIconWidth;
      ourPreferredSizeRect.width += defaultTextIconGap;
      ourPreferredSizeRect.width += defaultTextIconGap;
      ourPreferredSizeRect.width += ourArrowIconRect.width;
    }
    ourPreferredSizeRect.width += 2 * defaultTextIconGap;
    Insets insets = jMenu.getInsets();
    if (insets != null){
      ourPreferredSizeRect.width += insets.left + insets.right;
      ourPreferredSizeRect.height += insets.top + insets.bottom;
    }
    if (ourPreferredSizeRect.width % 2 == 0){
      ourPreferredSizeRect.width++;
    }
    if (ourPreferredSizeRect.height % 2 == 0){
      ourPreferredSizeRect.height++;
    }

    return patchPreferredSize(comp, ourPreferredSizeRect.getSize());
  }

  private void drawIconBorder(Graphics g) {
    int i1 = a - 1;
    int j1 = e - 2;
    int k1 = i1 + myMaxGutterIconWidth + 1;
    int l1 = j1 + myMaxGutterIconWidth + 4;
    g.setColor(BegResources.m);
    LinePainter2D.paint((Graphics2D)g, i1, j1, i1, l1);
    LinePainter2D.paint((Graphics2D)g, i1, j1, k1, j1);
    g.setColor(BegResources.j);
    LinePainter2D.paint((Graphics2D)g, k1, j1, k1, l1);
    LinePainter2D.paint((Graphics2D)g, i1, l1, k1, l1);
  }

  private static void resetRects() {
    ourIconRect.setBounds(ourZeroRect);
    ourTextRect.setBounds(ourZeroRect);
    ourAcceleratorRect.setBounds(ourZeroRect);
    ourCheckIconRect.setBounds(ourZeroRect);
    ourArrowIconRect.setBounds(ourZeroRect);
    ourViewRect.setBounds(0, 0, Short.MAX_VALUE, Short.MAX_VALUE);
  }

  private Icon getAllowedIcon() {
    Icon icon = menuItem.isEnabled() ? menuItem.getIcon() : menuItem.getDisabledIcon();
    if (menuItem.isEnabled() && isSelected(menuItem) && menuItem.getSelectedIcon() != null) {
      icon = menuItem.getSelectedIcon();
    }
    if (icon != null && icon.getIconWidth() > myMaxGutterIconWidth){
      icon = null;
    }
    return icon;
  }

  @Override
  public void update(Graphics g, JComponent comp) {
    paint(g, comp);
  }
}
