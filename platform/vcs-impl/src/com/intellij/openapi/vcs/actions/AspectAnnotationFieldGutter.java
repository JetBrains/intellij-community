// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class AspectAnnotationFieldGutter extends AnnotationFieldGutter {
  @NotNull protected final LineAnnotationAspect myAspect;
  private final boolean myIsGutterAction;

  public AspectAnnotationFieldGutter(@NotNull FileAnnotation annotation,
                                     @NotNull LineAnnotationAspect aspect,
                                     @NotNull TextAnnotationPresentation presentation,
                                     @Nullable Couple<Map<VcsRevisionNumber, Color>> colorScheme) {
    super(annotation, presentation, colorScheme);
    myAspect = aspect;
    myIsGutterAction = myAspect instanceof EditorGutterAction;
  }

  @Override
  public boolean isGutterAction() {
    return myIsGutterAction;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    final String value = isAvailable() ? myAspect.getValue(line) : "";
    if (LineAnnotationAspect.AUTHOR.equals(myAspect.getId())) {
      return ShortNameType.shorten(value, ShowShortenNames.getType());
    }
    return value;
  }

  @Nullable
  @Override
  public String getToolTip(final int line, final Editor editor) {
    String text = myAspect.getTooltipText(line);
    if (text != null) return text;
    return isAvailable() ? myAnnotation.getHtmlToolTip(line) : null;
  }

  @Override
  public void doAction(int line) {
    if (myIsGutterAction) {
      ((EditorGutterAction)myAspect).doAction(line);
    }
  }

  @Override
  public Cursor getCursor(final int line) {
    if (myIsGutterAction) {
      return ((EditorGutterAction)myAspect).getCursor(line);
    }
    return super.getCursor(line);
  }

  @Override
  public EditorFontType getStyle(int line, Editor editor) {
    EditorFontType style = myAspect.getStyle(line);
    if (style != null) return style;
    return super.getStyle(line, editor);
  }

  @Nullable
  @Override
  public ColorKey getColor(int line, Editor editor) {
    ColorKey color = myAspect.getColor(line);
    if (color != null) return color;
    return super.getColor(line, editor);
  }

  @Nullable
  @Override
  public Color getBgColor(int line, Editor editor) {
    Color color = myAspect.getBgColor(line);
    if (color != null) return color;
    return super.getBgColor(line, editor);
  }

  @Override
  public boolean isShowByDefault() {
    return myAspect.isShowByDefault();
  }

  @Nullable
  @Override
  public String getID() {
    return myAspect.getId();
  }

  @Override
  public @NlsContexts.ListItem @Nullable String getDisplayName() {
    return myAspect.getDisplayName();
  }
}
