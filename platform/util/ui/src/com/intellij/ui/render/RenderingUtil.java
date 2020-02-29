// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.render;

import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Color;

public final class RenderingUtil {
  /**
   * This key can be set to a list or a tree to paint unfocused selection as focused.
   *
   * @see JComponent#putClientProperty
   */
  public static final Key<Boolean> ALWAYS_PAINT_SELECTION_AS_FOCUSED = Key.create("ALWAYS_PAINT_SELECTION_AS_FOCUSED");

  /**
   * This key is set to a tree, which is a part of a compound tree table.
   * It is needed to provide corresponding colors from a table to an internal tree.
   */
  @ApiStatus.Internal
  public static final Key<JTable> CORRESPONDING_TREE_TABLE = Key.create("CORRESPONDING_TREE_TABLE");


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
    JTable table = UIUtil.getClientProperty(tree, CORRESPONDING_TREE_TABLE);
    if (table != null) return getBackground(table);
    Color background = tree.getBackground();
    return background != null ? background : UIUtil.getTreeBackground();
  }


  @NotNull
  public static Color getSelectionBackground(@NotNull JList<?> list) {
    return UIUtil.getListSelectionBackground(isFocused(list));
  }

  @NotNull
  public static Color getSelectionBackground(@NotNull JTable table) {
    return UIUtil.getTableSelectionBackground(isFocused(table));
  }

  @NotNull
  public static Color getSelectionBackground(@NotNull JTree tree) {
    JTable table = UIUtil.getClientProperty(tree, CORRESPONDING_TREE_TABLE);
    if (table != null) return getSelectionBackground(table);
    return UIUtil.getTreeSelectionBackground(isFocused(tree));
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
    JTable table = UIUtil.getClientProperty(tree, CORRESPONDING_TREE_TABLE);
    if (table != null) return getForeground(table);
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
    JTable table = UIUtil.getClientProperty(tree, CORRESPONDING_TREE_TABLE);
    if (table != null) return getSelectionForeground(table);
    return UIUtil.getTreeSelectionForeground(isFocused(tree));
  }


  private static boolean isFocused(@NotNull JComponent component) {
    return component.hasFocus() || UIUtil.isClientPropertyTrue(component, ALWAYS_PAINT_SELECTION_AS_FOCUSED);
  }
}
