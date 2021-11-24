// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.Processor
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction

abstract class AbstractLanguageInjectionTestCase : LightJavaCodeInsightFixtureTestCase() {
  val injectionTestFixture: InjectionTestFixture get() = InjectionTestFixture(myFixture)

  fun assertInjectedLangAtCaret(lang: String?) {
    injectionTestFixture.assertInjectedLangAtCaret(lang)
  }

  val topLevelEditor: Editor
    get() = injectionTestFixture.topLevelEditor

  val topLevelCaretPosition
    get() = injectionTestFixture.topLevelCaretPosition

  val topLevelFile: PsiFile
    get() = injectionTestFixture.topLevelFile
}

class StoringFixPresenter : InjectLanguageAction.FixPresenter {
  private lateinit var processor: Processor<PsiLanguageInjectionHost>
  private lateinit var pointer: SmartPsiElementPointer<PsiLanguageInjectionHost>

  override fun showFix(editor: Editor,
                       range: TextRange,
                       pointer: SmartPsiElementPointer<PsiLanguageInjectionHost>,
                       text: String,
                       data: Processor<PsiLanguageInjectionHost>) {
    this.processor = data
    this.pointer = pointer
  }

  fun process() = process(pointer.element ?: throw IllegalStateException("element was invalidated"))

  fun process(injectionHost: PsiLanguageInjectionHost) = processor.process(injectionHost)

}