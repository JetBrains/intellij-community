// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Processor
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction

class StoringFixPresenter : InjectLanguageAction.FixPresenter {
  private lateinit var processor: Processor<in PsiLanguageInjectionHost>
  private lateinit var pointer: SmartPsiElementPointer<PsiLanguageInjectionHost>

  override fun showFix(editor: Editor,
                       range: TextRange,
                       pointer: SmartPsiElementPointer<PsiLanguageInjectionHost>,
                       text: kotlin.String,
                       data: Processor<in PsiLanguageInjectionHost>) {
    this.processor = data
    this.pointer = pointer
  }

  fun process() = process(pointer.element ?: throw IllegalStateException("element was invalidated"))

  fun process(injectionHost: PsiLanguageInjectionHost) = processor.process(injectionHost)

}