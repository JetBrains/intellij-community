// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

final class ThemeSpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    return SpellcheckingStrategy.EMPTY_TOKENIZER;
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    return ThemeColorAnnotator.isTargetElement(element);
  }
}
