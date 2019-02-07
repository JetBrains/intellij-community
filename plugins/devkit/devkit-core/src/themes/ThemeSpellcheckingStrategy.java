// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

public class ThemeSpellcheckingStrategy extends SpellcheckingStrategy {

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    return EMPTY_TOKENIZER;
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    return ThemeColorAnnotator.isTargetElement(element);
  }
}
