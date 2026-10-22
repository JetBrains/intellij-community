// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleText;
import javax.swing.text.AttributeSet;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;

final class EditorAccessibleContextDelegate extends AccessibleContextDelegateWithContextMenu implements AccessibleText {
  private final EditorComponentImpl component;

  EditorAccessibleContextDelegate(@NotNull EditorComponentImpl component) {
    super(new AccessibleEditorComponentImpl(component));
    this.component = component;
  }

  @Override
  protected void doShowContextMenu() {
    ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null,
                                             component.getEditor().getContentComponent(), null, true);
  }

  @Override
  protected Container getDelegateParent() {
    return component.getParent();
  }

  @Override
  public int getIndexAtPoint(Point point) {
    return ((AccessibleText) getDelegate()).getIndexAtPoint(point);
  }

  @Override
  public Rectangle getCharacterBounds(int i) {
    return ((AccessibleText) getDelegate()).getCharacterBounds(i);
  }

  @Override
  public int getCharCount() {
    return ((AccessibleText) getDelegate()).getCharCount();
  }

  @Override
  public int getCaretPosition() {
    return ((AccessibleText) getDelegate()).getCaretPosition();
  }

  @Override
  public String getAtIndex(int part, int index) {
    return ((AccessibleText) getDelegate()).getAtIndex(part, index);
  }

  @Override
  public String getAfterIndex(int part, int index) {
    return ((AccessibleText) getDelegate()).getAfterIndex(part, index);
  }

  @Override
  public String getBeforeIndex(int part, int index) {
    return ((AccessibleText) getDelegate()).getBeforeIndex(part, index);
  }

  @Override
  public AttributeSet getCharacterAttribute(int i) {
    return ((AccessibleText) getDelegate()).getCharacterAttribute(i);
  }

  @Override
  public int getSelectionStart() {
    return ((AccessibleText) getDelegate()).getSelectionStart();
  }

  @Override
  public int getSelectionEnd() {
    return ((AccessibleText) getDelegate()).getSelectionEnd();
  }

  @Override
  public String getSelectedText() {
    return ((AccessibleText) getDelegate()).getSelectedText();
  }
}
