// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;

import javax.swing.plaf.TextUI;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.View;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Specialized TextUI intended *only* for accessibility usage. Not all the methods are called; only viewToModel, not modelToView.
 * <p>
 * The {@link JTextComponent} constructor installs this UI before {@link EditorComponentImpl} sets its fields.
 * So each method gets the editor from the component when it runs.
 */
final class EditorAccessibilityTextUI extends TextUI {
  private final EditorComponentImpl component;

  EditorAccessibilityTextUI(@NotNull EditorComponentImpl component) {
    this.component = component;
  }

  @Override
  public @NotNull Rectangle modelToView(JTextComponent tc, int offset) {
    return modelToView(tc, offset, Position.Bias.Forward);
  }

  @Override
  public int viewToModel(JTextComponent tc, Point pt) {
    return component.getEditorAccessibleText().getOffsetAt(pt);
  }

  @Override
  public @NotNull Rectangle modelToView(JTextComponent tc, int offset, Position.Bias bias) {
    EditorImpl editor = component.getEditor();
    LogicalPosition pos = editor.offsetToLogicalPosition(offset).leanForward(bias == Position.Bias.Forward);
    LogicalPosition posNext = editor.offsetToLogicalPosition(bias == Position.Bias.Forward ? offset + 1 : offset - 1)
      .leanForward(bias != Position.Bias.Forward);
    Point point = editor.logicalPositionToXY(pos);
    Point pointNext = editor.logicalPositionToXY(posNext);
    return point.y == pointNext.y
           ? new Rectangle(Math.min(point.x, pointNext.x), point.y, Math.abs(point.x - pointNext.x), editor.getLineHeight())
           : new Rectangle(point.x, point.y, 0, editor.getLineHeight());
  }

  @Override
  public int viewToModel(JTextComponent tc, Point pt, Position.Bias[] ignored) {
    return viewToModel(tc, pt);
  }

  @Override
  public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b,
                                       int direction,
                                       Position.Bias[] biasRet) {
    notSupported();
    return 0;
  }

  @Override
  public void damageRange(JTextComponent t, int p0, int p1) {
    component.getEditor().repaint(p0, p1);
  }

  @Override
  public void damageRange(JTextComponent t, int p0, int p1, Position.Bias ignored1, Position.Bias ignored2) {
    damageRange(t, p0, p1);
  }

  @Override
  public @NotNull EditorKit getEditorKit(JTextComponent t) {
    notSupported();
    return null;
  }

  @Override
  public @NotNull View getRootView(JTextComponent t) {
    notSupported();
    return null;
  }

  private static void notSupported() {
    throw new RuntimeException("Not supported for this text implementation");
  }
}
