// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.render;

import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Color;
import java.util.function.Supplier;

public final class RenderingUtil {
  /**
   * This key can be set to a list or a tree to paint unfocused selection as focused.
   *
   * @see JComponent#putClientProperty
   */
  public static final Key<Boolean> ALWAYS_PAINT_SELECTION_AS_FOCUSED = Key.create("ALWAYS_PAINT_SELECTION_AS_FOCUSED");

  /**
   * This key allows to paint focused selection even if a component does not have a focus.
   * Our tree table implementations use a table as a focusable sibling of a tree.
   * In such case the table colors will be used to paint the tree.
   */
  @ApiStatus.Internal
  public static final Key<JComponent> FOCUSABLE_SIBLING = Key.create("FOCUSABLE_SIBLING");

  /**
   * This key can be set to provide a custom selection background.
   */
  @ApiStatus.Internal
  public static final Key<Supplier<Color>> CUSTOM_SELECTION_BACKGROUND = Key.create("CUSTOM_SELECTION_BACKGROUND");


  @NotNull
  public static Color getBackground(@NotNull JList<?> list, boolean selected) {
    return selected ? getSelectionBackground(list) : getBackground(list);
  }

  @NotNull
  public static Color getBackground(@NotNull JTable table, boolean selected) {
    return selected ? getSelectionBackground(table) : getBackground(table);
  }

  @NotNull
  public static Color getBackground(@NotNull JTree tree, boolean selected) {
    return selected ? getSelectionBackground(tree) : getBackground(tree);
  }


  @NotNull
  public static Color getBackground(@NotNull JList<?> list) {
    Color background = list.getBackground();
    return background != null ? background : UIUtil.getListBackground();
  }

  @NotNull
  public static Color getBackground(@NotNull JTable table) {
    Color background = table.getBackground();
    return background != null ? background : UIUtil.getTableBackground();
  }

  @NotNull
  public static Color getBackground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getBackground(table); // tree table
    Color background = tree.getBackground();
    return background != null ? background : UIUtil.getTreeBackground();
  }


  @NotNull
  public static Color getSelectionBackground(@NotNull JList<?> list) {
    Color background = getCustomSelectionBackground(list);
    return background != null ? background : UIUtil.getListSelectionBackground(isFocused(list));
  }

  @NotNull
  public static Color getSelectionBackground(@NotNull JTable table) {
    Color background = getCustomSelectionBackground(table);
    return background != null ? background : UIUtil.getTableSelectionBackground(isFocused(table));
  }

  @NotNull
  public static Color getSelectionBackground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getSelectionBackground(table); // tree table
    Color background = getCustomSelectionBackground(tree);
    return background != null ? background : UIUtil.getTreeSelectionBackground(isFocused(tree));
  }


  @NotNull
  public static Color getForeground(@NotNull JList<?> list, boolean selected) {
    return selected ? getSelectionForeground(list) : getForeground(list);
  }

  @NotNull
  public static Color getForeground(@NotNull JTable table, boolean selected) {
    return selected ? getSelectionForeground(table) : getForeground(table);
  }

  @NotNull
  public static Color getForeground(@NotNull JTree tree, boolean selected) {
    return selected ? getSelectionForeground(tree) : getForeground(tree);
  }


  @NotNull
  public static Color getForeground(@NotNull JList<?> list) {
    Color foreground = list.getForeground();
    return foreground != null ? foreground : UIUtil.getListForeground();
  }

  @NotNull
  public static Color getForeground(@NotNull JTable table) {
    Color foreground = table.getForeground();
    return foreground != null ? foreground : UIUtil.getTableForeground();
  }

  @NotNull
  public static Color getForeground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getForeground(table); // tree table
    Color foreground = tree.getForeground();
    return foreground != null ? foreground : UIUtil.getTreeForeground();
  }


  @NotNull
  public static Color getSelectionForeground(@NotNull JList<?> list) {
    return UIUtil.getListSelectionForeground(isFocused(list));
  }

  @NotNull
  public static Color getSelectionForeground(@NotNull JTable table) {
    return UIUtil.getTableSelectionForeground(isFocused(table));
  }

  @NotNull
  public static Color getSelectionForeground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getSelectionForeground(table); // tree table
    return UIUtil.getTreeSelectionForeground(isFocused(tree));
  }


  public static boolean isFocused(@NotNull JComponent component) {
    if (isFocusedImpl(component)) return true;
    JComponent sibling = UIUtil.getClientProperty(component, FOCUSABLE_SIBLING);
    return sibling != null && isFocusedImpl(sibling);
  }

  private static boolean isFocusedImpl(@NotNull JComponent component) {
    return component.hasFocus() || UIUtil.isClientPropertyTrue(component, ALWAYS_PAINT_SELECTION_AS_FOCUSED);
  }

  private static JTable getTableFor(@NotNull JTree tree) {
    @SuppressWarnings("deprecation")
    Object property = tree.getClientProperty(WideSelectionTreeUI.TREE_TABLE_TREE_KEY);
    if (property instanceof JTable) return (JTable)property;
    JComponent sibling = UIUtil.getClientProperty(tree, FOCUSABLE_SIBLING);
    return sibling instanceof JTable ? (JTable)sibling : null;
  }

  private static Color getCustomSelectionBackground(@NotNull JComponent component) {
    Supplier<Color> supplier = UIUtil.getClientProperty(component, CUSTOM_SELECTION_BACKGROUND);
    return supplier == null ? null : supplier.get();
  }
}
