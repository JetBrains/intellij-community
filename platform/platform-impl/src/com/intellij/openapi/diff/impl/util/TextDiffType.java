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
  private final TextDiffTypeEnum myType;
  private final TextAttributesKey myAttributesKey;
  private final String myDisplayName;

  @NotNull
  public static TextDiffType create(@Nullable final TextDiffTypeEnum type) {
    if (TextDiffTypeEnum.INSERT.equals(type)) {
      return new TextDiffType(TextDiffTypeEnum.INSERT, DiffBundle.message("diff.type.inserted.name"), DiffColors.DIFF_INSERTED);
    } else if (TextDiffTypeEnum.CHANGED.equals(type)) {
      return new TextDiffType(TextDiffTypeEnum.CHANGED, DiffBundle.message("diff.type.changed.name"), DiffColors.DIFF_MODIFIED);
    } else if (TextDiffTypeEnum.DELETED.equals(type)) {
      return new TextDiffType(TextDiffTypeEnum.DELETED, DiffBundle.message("diff.type.deleted.name"), DiffColors.DIFF_DELETED);
    } else if (TextDiffTypeEnum.CONFLICT.equals(type)) {
      return new TextDiffType(TextDiffTypeEnum.CONFLICT, DiffBundle.message("diff.type.conflict.name"), DiffColors.DIFF_CONFLICT);
    } else {
      return new TextDiffType(TextDiffTypeEnum.NONE, DiffBundle.message("diff.type.none.name"), null);
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
