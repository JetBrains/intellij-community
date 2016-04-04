/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Irina Chernushina
 * @author Konstantin Bulenkov
 */
public class AnnotationFieldGutter implements ActiveAnnotationGutter {
  protected final FileAnnotation myAnnotation;
  protected final LineAnnotationAspect myAspect;
  private final TextAnnotationPresentation myPresentation;
  private final boolean myIsGutterAction;
  private Couple<Map<VcsRevisionNumber, Color>> myColorScheme;

  @Deprecated
  AnnotationFieldGutter(FileAnnotation annotation,
                        Editor editor,
                        LineAnnotationAspect aspect,
                        final TextAnnotationPresentation presentation,
                        Couple<Map<VcsRevisionNumber, Color>> colorScheme) {
    this(annotation, aspect, presentation, colorScheme);
  }

  AnnotationFieldGutter(FileAnnotation annotation,
                        LineAnnotationAspect aspect,
                        final TextAnnotationPresentation presentation,
                        Couple<Map<VcsRevisionNumber, Color>> colorScheme) {
    myAnnotation = annotation;
    myAspect = aspect;
    myPresentation = presentation;
    myIsGutterAction = myAspect instanceof EditorGutterAction;
    myColorScheme = colorScheme;
  }

  public boolean isGutterAction() {
    return myIsGutterAction;
  }

  public String getLineText(int line, Editor editor) {
    final String value = isAvailable() ? myAspect.getValue(line) : "";
    if (myAspect.getId() == LineAnnotationAspect.AUTHOR && ShowShortenNames.isSet()) {
      return shorten(value, ShowShortenNames.getType());
    }
    return value;
  }

  @Nullable
  public static String shorten(String name, ShortNameType type) {
    if (name != null) {
      // Vasya Pupkin <vasya.pupkin@jetbrains.com> -> Vasya Pupkin
      final int[] ind = {name.indexOf('<'), name.indexOf('@'), name.indexOf('>')};
      if (0 < ind[0] && ind[0] < ind[1] && ind[1] < ind[2]) {
        return shorten(name.substring(0, ind[0]).trim(), type);
      }

      // vasya.pupkin@email.com --> vasya pupkin
      if (!name.contains(" ") && name.contains("@")) { //simple e-mail check. john@localhost
        final String firstPart = name.substring(0, name.indexOf('@')).replace('.', ' ').replace('_', ' ').replace('-', ' ');
        if (firstPart.length() < name.length()) {
          return shorten(firstPart, type);
        } else {
          return firstPart;
        }
      }

      final List<String> strings = StringUtil.split(name.replace('.', ' ').replace('_', ' ').replace('-', ' '), " ");
      if (strings.size() > 1) {
        //Middle name check: Vasya S. Pupkin
        return StringUtil.capitalize(type == ShortNameType.FIRSTNAME ? strings.get(0) : strings.get(strings.size() - 1));
      }
    }
    return name;
  }

  @Nullable
  public String getToolTip(final int line, final Editor editor) {
    return isAvailable() ?
    XmlStringUtil.escapeString(myAnnotation.getToolTip(line)) : null;
  }

  public void doAction(int line) {
    if (myIsGutterAction) {
      ((EditorGutterAction)myAspect).doAction(line);
    }
  }

  public Cursor getCursor(final int line) {
    if (myIsGutterAction) {
      return ((EditorGutterAction)myAspect).getCursor(line);
    } else {
      return Cursor.getDefaultCursor();
    }

  }

  public EditorFontType getStyle(final int line, final Editor editor) {
    return myPresentation.getFontType(line);
  }

  @Nullable
  public ColorKey getColor(final int line, final Editor editor) {
    return myPresentation.getColor(line);
  }

  public List<AnAction> getPopupActions(int line, final Editor editor) {
    return myPresentation.getActions(line);
  }

  public void gutterClosed() {
    myAnnotation.unregister();
    myAnnotation.dispose();
  }

  @Nullable
  public Color getBgColor(int line, Editor editor) {
    ColorMode type = ShowAnnotationColorsAction.getType();
    Map<VcsRevisionNumber, Color> colorMap = type == ColorMode.AUTHOR ? myColorScheme.second : myColorScheme.first;
    if (colorMap == null || type == ColorMode.NONE) return null;
    final VcsRevisionNumber number = myAnnotation.getLineRevisionNumber(line);
    if (number == null) return null;
    return colorMap.get(number);
  }

  public boolean isAvailable() {
    if (myAspect == null) return false;
    return VcsUtil.isAspectAvailableByDefault(getID(), myAspect.isShowByDefault());
  }

  @Nullable
  public String getID() {
    return myAspect == null ? null : myAspect.getId();
  }


  public static void main(String[] args) {
    assert shorten("Vasya Pavlovich Pupkin <asdasd@localhost>", ShortNameType.FIRSTNAME).equals("Vasya");
    assert shorten("Vasya Pavlovich Pupkin <asdasd@localhost>", ShortNameType.LASTNAME).equals("Pupkin");
    assert shorten("Vasya Pavlovich Pupkin", ShortNameType.FIRSTNAME).equals("Vasya");
    assert shorten("Vasya Pavlovich Pupkin", ShortNameType.LASTNAME).equals("Pupkin");
    assert shorten("vasya.pupkin@localhost.com", ShortNameType.LASTNAME).equals("Pupkin");
    assert shorten("vasya.pupkin@localhost.com", ShortNameType.FIRSTNAME).equals("Vasya");
  }
}
