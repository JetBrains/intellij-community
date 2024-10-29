// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.ExperimentalUI;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AnnotationFieldGutter implements ActiveAnnotationGutter {
  @NotNull protected final FileAnnotation myAnnotation;
  @NotNull private final TextAnnotationPresentation myPresentation;
  @Nullable private final Couple<? extends Map<VcsRevisionNumber, Color>> myColorScheme;

  AnnotationFieldGutter(@NotNull FileAnnotation annotation,
                        @NotNull TextAnnotationPresentation presentation,
                        @Nullable Couple<? extends Map<VcsRevisionNumber, Color>> colorScheme) {
    myAnnotation = annotation;
    myPresentation = presentation;
    myColorScheme = colorScheme;
  }

  @NotNull
  public FileAnnotation getFileAnnotation() {
    return myAnnotation;
  }

  @ApiStatus.Internal
  @NotNull
  public TextAnnotationPresentation getPresentation() {
    return myPresentation;
  }

  public boolean isGutterAction() {
    return false;
  }

  @Nullable
  @Override
  public String getToolTip(final int line, final Editor editor) {
    return null;
  }

  @Override
  public void doAction(int line) {
  }

  @Override
  public Cursor getCursor(final int line) {
    return Cursor.getDefaultCursor();
  }

  @Override
  public EditorFontType getStyle(final int line, final Editor editor) {
    return myPresentation.getFontType(line);
  }

  @Nullable
  @Override
  public ColorKey getColor(final int line, final Editor editor) {
    return myPresentation.getColor(line);
  }

  @Override
  public List<AnAction> getPopupActions(int line, final Editor editor) {
    return myPresentation.getActions(line);
  }

  @Override
  public void gutterClosed() {
    myPresentation.gutterClosed();
  }

  @Nullable
  @Override
  public Color getBgColor(int line, Editor editor) {
    if (myColorScheme == null) return null;
    ColorMode type = ShowAnnotationColorsAction.getType();
    Map<VcsRevisionNumber, Color> colorMap = type == ColorMode.AUTHOR ? myColorScheme.second : myColorScheme.first;
    if (colorMap == null || type == ColorMode.NONE) return null;
    final VcsRevisionNumber number = myAnnotation.getLineRevisionNumber(line);
    if (number == null) return null;
    return colorMap.get(number);
  }

  public boolean isShowByDefault() {
    return true;
  }

  public boolean isAvailable() {
    return VcsUtil.isAspectAvailableByDefault(getID(), isShowByDefault());
  }

  public @NonNls @Nullable String getID() {
    return null;
  }

  public @NlsContexts.ListItem @Nullable String getDisplayName() {
    return null;
  }

  @Override
  public int getLeftMargin() {
    return ExperimentalUI.isNewUI() ? 0 : ActiveAnnotationGutter.super.getLeftMargin();
  }
}
