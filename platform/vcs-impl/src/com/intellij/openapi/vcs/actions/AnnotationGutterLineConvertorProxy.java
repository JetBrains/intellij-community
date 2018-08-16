// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.vcs.annotate.AnnotationSource;

import java.awt.*;
import java.util.List;

/**
 * @author Irina Chernushina
 * @author Konstantin Bulenkov
 */
public class AnnotationGutterLineConvertorProxy implements ActiveAnnotationGutter {
  private final UpToDateLineNumberProvider myGetUpToDateLineNumber;
  private final ActiveAnnotationGutter myDelegate;

  public AnnotationGutterLineConvertorProxy(final UpToDateLineNumberProvider getUpToDateLineNumber, final ActiveAnnotationGutter delegate) {
    myGetUpToDateLineNumber = getUpToDateLineNumber;
    myDelegate = delegate;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (!canBeAnnotated(currentLine)) return "";
    return myDelegate.getLineText(currentLine, editor);
  }

  @Override
  public String getToolTip(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (!canBeAnnotated(currentLine)) return "";
    return myDelegate.getToolTip(currentLine, editor);
  }

  @Override
  public EditorFontType getStyle(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (!canBeAnnotated(currentLine)) return EditorFontType.PLAIN;
    return myDelegate.getStyle(currentLine, editor);
  }

  @Override
  public ColorKey getColor(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (!canBeAnnotated(currentLine)) return AnnotationSource.LOCAL.getColor();
    return myDelegate.getColor(currentLine, editor);
  }

  @Override
  public Color getBgColor(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (!canBeAnnotated(currentLine)) return null;
    return myDelegate.getBgColor(currentLine, editor);
  }

  @Override
  public List<AnAction> getPopupActions(int line, Editor editor) {
    return myDelegate.getPopupActions(line, editor);
  }

  @Override
  public void gutterClosed() {
    myDelegate.gutterClosed();
  }

  @Override
  public void doAction(int lineNum) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(lineNum);
    if (!canBeAnnotated(currentLine)) return;
    myDelegate.doAction(currentLine);
  }

  @Override
  public Cursor getCursor(int lineNum) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(lineNum);
    if (!canBeAnnotated(currentLine)) return Cursor.getDefaultCursor();
    return myDelegate.getCursor(currentLine);
  }

  private static boolean canBeAnnotated(int currentLine) {
    return currentLine >= 0;
  }
}
