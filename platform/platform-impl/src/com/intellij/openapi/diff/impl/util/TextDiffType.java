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
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class TextDiffType implements DiffStatusBar.LegendTypeDescriptor {
  public static final TextDiffType INSERT = new TextDiffType(TextDiffTypeEnum.INSERT, DiffBundle.message("diff.type.inserted.name"), DiffColors.DIFF_INSERTED);
  public static final TextDiffType CHANGED = new TextDiffType(TextDiffTypeEnum.CHANGED, DiffBundle.message("diff.type.changed.name"), DiffColors.DIFF_MODIFIED);
  public static final TextDiffType DELETED = new TextDiffType(TextDiffTypeEnum.DELETED, DiffBundle.message("diff.type.deleted.name"), DiffColors.DIFF_DELETED);
  public static final TextDiffType CONFLICT = new TextDiffType(TextDiffTypeEnum.CONFLICT, DiffBundle.message("diff.type.conflict.name"), DiffColors.DIFF_CONFLICT);

  public static final TextDiffType NONE = new TextDiffType(TextDiffTypeEnum.NONE, DiffBundle.message("diff.type.none.name"), null);

  public static final List<TextDiffType> DIFF_TYPES = Arrays.asList(DELETED, CHANGED, INSERT);
  public static final List<TextDiffType> MERGE_TYPES = Arrays.asList(DELETED, CHANGED, INSERT, CONFLICT);

  public static final Convertor<TextDiffType, TextAttributesKey> ATTRIBUTES_KEY = new Convertor<TextDiffType, TextAttributesKey>() {
    public TextAttributesKey convert(TextDiffType textDiffType) {
      return textDiffType.getAttributesKey();
    }
  };

  private final TextDiffTypeEnum myType;
  private final TextAttributesKey myAttributesKey;
  private final String myDisplayName;
  private final boolean myApplied;

  public boolean isApplied() {
    return myApplied;
  }

  @NotNull
  public static TextDiffType create(@Nullable final TextDiffTypeEnum type) {
    if (TextDiffTypeEnum.INSERT.equals(type)) {
      return INSERT;
    } else if (TextDiffTypeEnum.CHANGED.equals(type)) {
      return CHANGED;
    } else if (TextDiffTypeEnum.DELETED.equals(type)) {
      return DELETED;
    } else if (TextDiffTypeEnum.CONFLICT.equals(type)) {
      return CONFLICT;
    } else {
      return NONE;
    }
  }

  /**
   * Creates a new TextDiffType based on the given one.
   * @param source
   * @return
   */
  @NotNull
  public static TextDiffType deriveApplied(@NotNull TextDiffType source) {
    return new TextDiffType(source.myType, source.myDisplayName, source.myAttributesKey, true);
  }

  private TextDiffType(TextDiffTypeEnum type, String displayName, TextAttributesKey attributesKey) {
    this(type, displayName, attributesKey, false);
  }

  private TextDiffType(TextDiffTypeEnum type, String displayName, TextAttributesKey attributesKey, boolean applied) {
    myType = type;
    myAttributesKey = attributesKey;
    myDisplayName = displayName;
    myApplied = applied;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  public Color getLegendColor(EditorColorsScheme colorScheme) {
    TextAttributes attributes = colorScheme.getAttributes(myAttributesKey);
    return attributes != null ? attributes.getBackgroundColor() : null;
  }

  public TextAttributesKey getAttributesKey() {
    return myAttributesKey;
  }

  @Nullable
  public TextAttributes getTextAttributes(EditorColorsScheme scheme) {
    TextAttributes originalAttrs = scheme.getAttributes(myAttributesKey);
    if (originalAttrs == null) {
      return null;
    }
    TextAttributes overridingAttributes = new TextAttributes();
    if (myApplied) {
      overridingAttributes.setBackgroundColor(scheme.getDefaultBackground());
    }
    return TextAttributes.merge(originalAttrs, overridingAttributes);
  }

  @Nullable
  public Color getPolygonColor(Editor editor) {
    if (isApplied()) {
      return getLegendColor(editor.getColorsScheme());
    }
    else {
      TextAttributes attributes = getTextAttributes(editor.getColorsScheme());
      return attributes == null ? null : attributes.getBackgroundColor();
    }
  }

  @Nullable
  public TextAttributes getTextAttributes(Editor editor) {
    return getTextAttributes(editor.getColorsScheme());
  }

  @Nullable
  public Color getTextBackground(Editor editor) {
    TextAttributes attributes = getTextAttributes(editor);
    return attributes != null ? attributes.getBackgroundColor() : null;
  }

  public String toString(){
    return myApplied ? myDisplayName + "_applied" : myDisplayName;
  }

  public TextDiffTypeEnum getType() {
    return myType;
  }

}
