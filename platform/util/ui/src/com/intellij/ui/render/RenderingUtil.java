// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.render;

import com.intellij.openapi.util.Key;
import com.intellij.util.ui.JBUI.CurrentTheme;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

import static com.intellij.openapi.util.IconLoader.getDarkIcon;
import static com.intellij.util.ui.StartupUiUtil.isUnderDarcula;

public final class RenderingUtil {
  /**
   * This key can be set to a list or a tree to paint unfocused selection as focused.
   *
   * @see JComponent#putClientProperty
   */
  public static final Key<Boolean> ALWAYS_PAINT_SELECTION_AS_FOCUSED = Key.create("ALWAYS_PAINT_SELECTION_AS_FOCUSED");

  /**
   * This key allows to paint a background of a hovered row if it is not selected.
   */
  @ApiStatus.Experimental
  public static final Key<Boolean> PAINT_HOVERED_BACKGROUND = Key.create("PAINT_HOVERED_BACKGROUND");

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

  /**
   * This key can be set to provide a custom selection foreground.
   */
  @ApiStatus.Internal
  public static final Key<Supplier<Color>> CUSTOM_SELECTION_FOREGROUND = Key.create("CUSTOM_SELECTION_FOREGROUND");


  /**
   * @param icon     an icon to render
   * @param selected specifies whether is a selection background expected
   * @return a lighter icon if applicable, the given icon otherwise
   */
  public static @Nullable Icon getIcon(@Nullable Icon icon, boolean selected) {
    return !selected || icon == null || isUnderDarcula() ? icon : getDarkIcon(icon, true);
  }


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
    return background != null ? background : CurrentTheme.List.BACKGROUND;
  }

  @NotNull
  public static Color getBackground(@NotNull JTable table) {
    Color background = table.getBackground();
    return background != null ? background : CurrentTheme.Table.BACKGROUND;
  }

  @NotNull
  public static Color getBackground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getBackground(table); // tree table
    Color background = tree.getBackground();
    return background != null ? background : CurrentTheme.Tree.BACKGROUND;
  }


  @NotNull
  public static Color getSelectionBackground(@NotNull JList<?> list) {
    Color background = getCustomColor(list, CUSTOM_SELECTION_BACKGROUND);
    return background != null ? background : CurrentTheme.List.Selection.background(isFocused(list));
  }

  @NotNull
  public static Color getSelectionBackground(@NotNull JTable table) {
    Color background = getCustomColor(table, CUSTOM_SELECTION_BACKGROUND);
    return background != null ? background : CurrentTheme.Table.Selection.background(isFocused(table));
  }

  @NotNull
  public static Color getSelectionBackground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getSelectionBackground(table); // tree table
    Color background = getCustomColor(tree, CUSTOM_SELECTION_BACKGROUND);
    return background != null ? background : CurrentTheme.Tree.Selection.background(isFocused(tree));
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
    return foreground != null ? foreground : CurrentTheme.List.FOREGROUND;
  }

  @NotNull
  public static Color getForeground(@NotNull JTable table) {
    Color foreground = table.getForeground();
    return foreground != null ? foreground : CurrentTheme.Table.FOREGROUND;
  }

  @NotNull
  public static Color getForeground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getForeground(table); // tree table
    Color foreground = tree.getForeground();
    return foreground != null ? foreground : CurrentTheme.Tree.FOREGROUND;
  }


  @NotNull
  public static Color getSelectionForeground(@NotNull JList<?> list) {
    Color foreground = getCustomColor(list, CUSTOM_SELECTION_FOREGROUND);
    return foreground != null ? foreground : CurrentTheme.List.Selection.foreground(isFocused(list));
  }

  @NotNull
  public static Color getSelectionForeground(@NotNull JTable table) {
    Color foreground = getCustomColor(table, CUSTOM_SELECTION_FOREGROUND);
    return foreground != null ? foreground : CurrentTheme.Table.Selection.foreground(isFocused(table));
  }

  @NotNull
  public static Color getSelectionForeground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getSelectionForeground(table); // tree table
    Color foreground = getCustomColor(tree, CUSTOM_SELECTION_FOREGROUND);
    return foreground != null ? foreground : CurrentTheme.Tree.Selection.foreground(isFocused(tree));
  }


  @ApiStatus.Internal
  public static boolean isHoverPaintingDisabled(@NotNull JComponent component) {
    return Boolean.FALSE.equals(component.getClientProperty(PAINT_HOVERED_BACKGROUND));
  }

  public static @Nullable Color getHoverBackground(@NotNull JList<?> list) {
    if (isHoverPaintingDisabled(list)) return null;
    return CurrentTheme.List.Hover.background(isFocused(list));
  }

  public static @Nullable Color getHoverBackground(@NotNull JTable table) {
    if (isHoverPaintingDisabled(table)) return null;
    return CurrentTheme.Table.Hover.background(isFocused(table));
  }

  public static @Nullable Color getHoverBackground(@NotNull JTree tree) {
    JTable table = getTableFor(tree);
    if (table != null) return getHoverBackground(table); // tree table
    if (isHoverPaintingDisabled(tree)) return null;
    return CurrentTheme.Tree.Hover.background(isFocused(tree));
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
    JComponent sibling = UIUtil.getClientProperty(tree, FOCUSABLE_SIBLING);
    return sibling instanceof JTable ? (JTable)sibling : null;
  }

  private static Color getCustomColor(@NotNull JComponent component, @NotNull Key<Supplier<Color>> key) {
    Supplier<Color> supplier = UIUtil.getClientProperty(component, key);
    return supplier == null ? null : supplier.get();
  }
}
