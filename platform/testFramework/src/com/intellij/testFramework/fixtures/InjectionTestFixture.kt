// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import junit.framework.TestCase
import java.util.*
import kotlin.test.fail

class InjectionTestFixture(private val javaFixture: CodeInsightTestFixture) {

  val injectedLanguageManager: InjectedLanguageManager
    get() = InjectedLanguageManager.getInstance(javaFixture.project)

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

  fun getAllInjections(): List<Pair<PsiElement, PsiFile>> {
    val injected = mutableListOf<Pair<PsiElement, PsiFile>>()
    val hosts = PsiTreeUtil.collectElementsOfType(topLevelFile, PsiLanguageInjectionHost::class.java)
    for (host in hosts) {
      injectedLanguageManager.enumerate(host,
                                        PsiLanguageInjectionHost.InjectedPsiVisitor { injectedPsi, places ->
                                          injected.add(host to injectedPsi)
                                        })
    }
    return injected
  }

  fun assertInjected(vararg expectedInjections: InjectionAssertionData) {

    val expected = expectedInjections.toCollection(LinkedList())
    val foundInjections = getAllInjections().toCollection(LinkedList())

    while (expected.isNotEmpty()) {
      val (text, injectedLanguage) = expected.pop()
      val found = (foundInjections.find { (psi, file) -> psi.text == text && file.language.id == injectedLanguage }
                   ?: fail(
                     "no injection '$text' -> '$injectedLanguage' were found, remains: ${foundInjections.joinToString { (psi, file) -> "'${psi.text}' -> '${file.language}'" }}   "))
      foundInjections.remove(found)
    }

  }

  val topLevelFile: PsiFile get() = javaFixture.file.let { injectedLanguageManager.getTopLevelFile(it) }

  val topLevelCaretPosition get() = topLevelEditor.caretModel.offset

  val topLevelEditor
    get() = (FileEditorManager.getInstance(
      javaFixture.project).getSelectedEditor(topLevelFile.virtualFile) as TextEditor).editor

}

data class InjectionAssertionData(val text: String, val injectedLanguage: String? = null) {
  fun hasLanguage(lang: String): InjectionAssertionData = this.copy(injectedLanguage = lang)
}

fun injectionForHost(text: String) = InjectionAssertionData(text)