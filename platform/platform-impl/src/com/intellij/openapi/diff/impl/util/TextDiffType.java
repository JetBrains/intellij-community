// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextDiffType {
  public static final TextDiffType INSERT = new TextDiffType(TextDiffTypeEnum.INSERT, DiffBundle.message("diff.type.inserted.name"), DiffColors.DIFF_INSERTED);
  public static final TextDiffType CHANGED = new TextDiffType(TextDiffTypeEnum.CHANGED, DiffBundle.message("diff.type.changed.name"), DiffColors.DIFF_MODIFIED);
  public static final TextDiffType DELETED = new TextDiffType(TextDiffTypeEnum.DELETED, DiffBundle.message("diff.type.deleted.name"), DiffColors.DIFF_DELETED);
  public static final TextDiffType CONFLICT = new TextDiffType(TextDiffTypeEnum.CONFLICT, DiffBundle.message("diff.type.conflict.name"), DiffColors.DIFF_CONFLICT);

  public static final TextDiffType NONE = new TextDiffType(TextDiffTypeEnum.NONE, DiffBundle.message("diff.type.none.name"), null);

  private final TextDiffTypeEnum myType;
  private final TextAttributesKey myAttributesKey;
  private final String myDisplayName;

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

  private TextDiffType(TextDiffTypeEnum type, String displayName, TextAttributesKey attributesKey) {
    myType = type;
    myAttributesKey = attributesKey;
    myDisplayName = displayName;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public TextAttributesKey getAttributesKey() {
    return myAttributesKey;
  }

  @Nullable
  public TextAttributes getTextAttributes(@NotNull EditorColorsScheme scheme) {
    return scheme.getAttributes(myAttributesKey);
  }

  @Nullable
  public TextAttributes getTextAttributes(@NotNull Editor editor) {
    return getTextAttributes(editor.getColorsScheme());
  }

  @Override
  public String toString() {
    return myDisplayName;
  }

  public TextDiffTypeEnum getType() {
    return myType;
  }
}
