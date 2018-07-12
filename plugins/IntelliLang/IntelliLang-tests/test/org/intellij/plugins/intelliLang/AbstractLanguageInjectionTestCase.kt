// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.Processor
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction

abstract class AbstractLanguageInjectionTestCase : LightCodeInsightFixtureTestCase() {

  fun assertInjectedLangAtCaret(lang: String?) {
    val injectedElement = injectedLanguageManager.findInjectedElementAt(topLevelFile, topLevelCaretPosition)
    if (lang != null) {
      TestCase.assertNotNull("injection of '$lang' expected", injectedElement)
      TestCase.assertEquals(lang, injectedElement!!.language.id)
    }
    else {
      TestCase.assertNull(injectedElement)
    }
  }

  val topLevelEditor get() = (FileEditorManager.getInstance(project).getSelectedEditor(topLevelFile.virtualFile) as TextEditor).editor

  val topLevelCaretPosition get() = topLevelEditor.caretModel.offset

  val injectedLanguageManager: InjectedLanguageManager get() = InjectedLanguageManager.getInstance(project)

  val topLevelFile: PsiFile get() = file.let { injectedLanguageManager.getTopLevelFile(it) }

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