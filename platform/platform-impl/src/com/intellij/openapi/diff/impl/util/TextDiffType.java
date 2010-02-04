/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class TextDiffType implements DiffStatusBar.LegendTypeDescriptor {
  public static final TextDiffType INSERT = new TextDiffType(TextDiffTypeEnum.INSERT, DiffBundle.message("diff.type.inserted.name"), DiffColors.DIFF_INSERTED);
  public static final TextDiffType CHANGED = new TextDiffType(TextDiffTypeEnum.CHANGED, DiffBundle.message("diff.type.changed.name"), DiffColors.DIFF_MODIFIED);
  public static final TextDiffType DELETED = new TextDiffType(TextDiffTypeEnum.DELETED, DiffBundle.message("diff.type.deleted.name"), DiffColors.DIFF_DELETED);
  public static final TextDiffType CONFLICT = new TextDiffType(TextDiffTypeEnum.CONFLICT, DiffBundle.message("diff.type.conflict.name"), DiffColors.DIFF_CONFLICT);

  public static final TextDiffType NONE = new TextDiffType(TextDiffTypeEnum.NONE, DiffBundle.message("diff.type.none.name"), null);

  private final TextDiffTypeEnum myType;
  public static final List<TextDiffType> DIFF_TYPES = Arrays.asList(new TextDiffType[]{DELETED, CHANGED, INSERT});
  public static final List<TextDiffType> MERGE_TYPES = Arrays.asList(new TextDiffType[]{DELETED, CHANGED, INSERT, CONFLICT});
  private final TextAttributesKey myAttributesKey;
  private final String myDisplayName;
  public static final Convertor<TextDiffType, TextAttributesKey> ATTRIBUTES_KEY = new Convertor<TextDiffType, TextAttributesKey>() {
    public TextAttributesKey convert(TextDiffType textDiffType) {
      return textDiffType.getAttributesKey();
    }
  };

  public static TextDiffType create(@NotNull final TextDiffTypeEnum type) {
    if (TextDiffTypeEnum.INSERT.equals(type)) {
      return INSERT;
    } else if (TextDiffTypeEnum.CHANGED.equals(type)) {
      return CHANGED;
    } else if (TextDiffTypeEnum.DELETED.equals(type)) {
      return DELETED;
    } else if (TextDiffTypeEnum.CONFLICT.equals(type)) {
      return CONFLICT;
    } else {
      // NONE
      return NONE;
    }
  }

  private TextDiffType(TextDiffTypeEnum type, String displayName, TextAttributesKey attrubutesKey) {
    myType = type;
    myAttributesKey = attrubutesKey;
    myDisplayName = displayName;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public Color getLegendColor(EditorColorsScheme colorScheme) {
    TextAttributes attributes = getTextAttributes(colorScheme);
    return attributes != null ? attributes.getBackgroundColor() : null;
  }

  public TextAttributesKey getAttributesKey() {
    return myAttributesKey;
  }

  public TextAttributes getTextAttributes(EditorColorsScheme scheme) {
    return scheme.getAttributes(myAttributesKey);
  }

  public Color getPoligonColor(Editor editor1) {
    return getLegendColor(editor1.getColorsScheme());
  }

  public TextAttributes getTextAttributes(Editor editor1) {
    return getTextAttributes(editor1.getColorsScheme());
  }

  public Color getTextBackground(Editor editor) {
    TextAttributes attributes = getTextAttributes(editor);
    return attributes != null ? attributes.getBackgroundColor() : null;
  }

  public String toString(){
    return myDisplayName;
  }

  public TextDiffTypeEnum getType() {
    return myType;
  }
}
