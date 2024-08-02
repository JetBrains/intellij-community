// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.spellchecker

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import org.editorconfig.language.psi.EditorConfigCharClassPattern
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.psi.interfaces.EditorConfigHeaderElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor

class EditorConfigSpellcheckerStrategy : SpellcheckingStrategy(), DumbAware {
  override fun getTokenizer(element: PsiElement): Tokenizer<*> {
    if (element is PsiComment) return super.getTokenizer(element)
    if (element is EditorConfigCharClassPattern) return EMPTY_TOKENIZER
    if (element is EditorConfigHeaderElement) return super.getTokenizer(element)

    val describable = element as? EditorConfigDescribableElement
    return when (describable?.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor -> super.getTokenizer(element)
      else -> EMPTY_TOKENIZER
    }
  }
}
