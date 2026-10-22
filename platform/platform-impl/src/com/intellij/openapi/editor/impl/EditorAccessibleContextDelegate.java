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

  private @NotNull AccessibleText getTextDelegate() {
    return (AccessibleText)getDelegate();
  }

  @Override
  public int getIndexAtPoint(Point point) {
    return getTextDelegate().getIndexAtPoint(point);
  }

  @Override
  public Rectangle getCharacterBounds(int i) {
    return getTextDelegate().getCharacterBounds(i);
  }

  @Override
  public int getCharCount() {
    return getTextDelegate().getCharCount();
  }

  @Override
  public int getCaretPosition() {
    return getTextDelegate().getCaretPosition();
  }

  @Override
  public String getAtIndex(int part, int index) {
    return getTextDelegate().getAtIndex(part, index);
  }

  @Override
  public String getAfterIndex(int part, int index) {
    return getTextDelegate().getAfterIndex(part, index);
  }

  @Override
  public String getBeforeIndex(int part, int index) {
    return getTextDelegate().getBeforeIndex(part, index);
  }

  @Override
  public AttributeSet getCharacterAttribute(int i) {
    return getTextDelegate().getCharacterAttribute(i);
  }

  @Override
  public int getSelectionStart() {
    return getTextDelegate().getSelectionStart();
  }

  @Override
  public int getSelectionEnd() {
    return getTextDelegate().getSelectionEnd();
  }

  @Override
  public String getSelectedText() {
    return getTextDelegate().getSelectedText();
  }
}
