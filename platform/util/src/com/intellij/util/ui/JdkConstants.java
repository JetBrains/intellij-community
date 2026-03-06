// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.intellij.lang.annotations.MagicConstant;

import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Adjustable;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.util.Calendar;
import java.util.regex.Pattern;


/**
 * IJ copy for the deprecated {@link org.intellij.lang.annotations.JdkConstants}.
 *
 * <p>This class keeps the same nested {@link MagicConstant}-based annotations but lives in IJ sources,
 * so platform code does not depend on the deprecated upstream holder.
 *
 * <p>Use nested annotations from this class (for example, {@link FontStyle} or {@link InputEventMask})
 * in IJ modules instead of the deprecated upstream type.
 */
public final class JdkConstants {
  private JdkConstants() {
  }

  @MagicConstant(intValues = {SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.RIGHT, SwingConstants.LEADING, SwingConstants.TRAILING})
  public @interface HorizontalAlignment {}

  @MagicConstant(intValues = {FlowLayout.LEFT, FlowLayout.CENTER, FlowLayout.RIGHT, FlowLayout.LEADING, FlowLayout.TRAILING})
  public @interface FlowLayoutAlignment {}

  @MagicConstant(valuesFromClass = Cursor.class)
  public @interface CursorType {}

  @MagicConstant(flagsFromClass = InputEvent.class)
  public @interface InputEventMask {}

  @MagicConstant(intValues = {Adjustable.HORIZONTAL, Adjustable.VERTICAL})
  public @interface AdjustableOrientation {}

  @MagicConstant(intValues = {
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
  })
  public @interface VerticalScrollBarPolicy {}

  @MagicConstant(intValues = {
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
  })
  public @interface HorizontalScrollBarPolicy {}

  @SuppressWarnings("unused")
  @MagicConstant(intValues = {
    Calendar.JANUARY,
    Calendar.FEBRUARY,
    Calendar.MARCH,
    Calendar.APRIL,
    Calendar.MAY,
    Calendar.JUNE,
    Calendar.JULY,
    Calendar.AUGUST,
    Calendar.SEPTEMBER,
    Calendar.OCTOBER,
    Calendar.NOVEMBER,
    Calendar.DECEMBER,
  })
  public @interface CalendarMonth {}

  @MagicConstant(flagsFromClass = Pattern.class)
  public @interface PatternFlags {}

  @SuppressWarnings("unused")
  @MagicConstant(valuesFromClass = BoxLayout.class)
  public @interface BoxLayoutAxis {}

  @MagicConstant(valuesFromClass = ListSelectionModel.class)
  public @interface ListSelectionMode {}

  @MagicConstant(valuesFromClass = TreeSelectionModel.class)
  public @interface TreeSelectionMode {}

  @MagicConstant(flags = {Font.PLAIN, Font.BOLD, Font.ITALIC})
  public @interface FontStyle {}

  @MagicConstant(intValues = {
    TitledBorder.DEFAULT_JUSTIFICATION,
    TitledBorder.LEFT,
    TitledBorder.CENTER,
    TitledBorder.RIGHT,
    TitledBorder.LEADING,
    TitledBorder.TRAILING,
  })
  public @interface TitledBorderJustification {}

  @MagicConstant(intValues = {
    TitledBorder.DEFAULT_POSITION,
    TitledBorder.ABOVE_TOP,
    TitledBorder.TOP,
    TitledBorder.BELOW_TOP,
    TitledBorder.ABOVE_BOTTOM,
    TitledBorder.BOTTOM,
    TitledBorder.BELOW_BOTTOM,
  })
  public @interface TitledBorderTitlePosition {}

  @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT})
  public @interface TabPlacement {}

  @MagicConstant(intValues = {JTabbedPane.WRAP_TAB_LAYOUT, JTabbedPane.SCROLL_TAB_LAYOUT})
  public @interface TabLayoutPolicy {}
}
