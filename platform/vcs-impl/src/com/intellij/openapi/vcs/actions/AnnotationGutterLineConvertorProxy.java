// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Cursor;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class AnnotationGutterLineConvertorProxy implements ActiveAnnotationGutter {
  private final UpToDateLineNumberProvider myGetUpToDateLineNumber;
  final ActiveAnnotationGutter myDelegate;

  public AnnotationGutterLineConvertorProxy(final UpToDateLineNumberProvider getUpToDateLineNumber, final ActiveAnnotationGutter delegate) {
    myGetUpToDateLineNumber = getUpToDateLineNumber;
    myDelegate = delegate;
  }

  public @NotNull ActiveAnnotationGutter getDelegate() {
    return myDelegate;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    int currentLine = myGetUpToDateLineNumber.getLineNumber(line);
    if (!canBeAnnotated(currentLine)) return getNonAnnotatedLineText(line);
    return myDelegate.getLineText(currentLine, editor);
  }

  private @Nullable String getNonAnnotatedLineText(int line) {
    if (myGetUpToDateLineNumber instanceof NonAnnotatedLineTextProvider textProvider &&
        myDelegate instanceof AnnotationFieldGutter fieldGutter &&
        LineAnnotationAspect.AUTHOR.equals(fieldGutter.getID())) {
      return textProvider.getNonAnnotatedLineText(line);
    }
    return null;
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
  public boolean useMargin() {
    return myDelegate.useMargin();
  }

  @Override
  public int getLeftMargin() {
    return myDelegate.getLeftMargin();
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

  /**
   * Opt-in for {@link UpToDateLineNumberProvider}s that want a placeholder rendered on lines which are not part of the
   * annotated revision (i.e. {@link #canBeAnnotated} is {@code false}), instead of leaving them blank.
   */
  @ApiStatus.Internal
  public interface NonAnnotatedLineTextProvider {
    @Nls
    @Nullable String getNonAnnotatedLineText(int line);
  }

  static class Filler extends AnnotationGutterLineConvertorProxy implements TextAnnotationGutterProvider.Filler {
    public Filler(UpToDateLineNumberProvider getUpToDateLineNumber, ActiveAnnotationGutter delegate) {
      super(getUpToDateLineNumber, delegate);
    }

    @Override
    public int getWidth() {
      return ((TextAnnotationGutterProvider.Filler)myDelegate).getWidth();
    }
  }
}
