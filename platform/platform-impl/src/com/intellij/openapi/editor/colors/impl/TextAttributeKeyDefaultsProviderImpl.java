// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public final class TextAttributeKeyDefaultsProviderImpl implements TextAttributesKey.TextAttributeKeyDefaultsProvider {
  @Override
  public TextAttributes getDefaultAttributes(@NotNull TextAttributesKey key) {
    return ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getDefaultAttributes(key);
  }
}
