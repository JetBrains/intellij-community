// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import java.util.*
import kotlin.collections.HashSet
import kotlin.test.fail

class InjectionTestFixture(private val javaFixture: CodeInsightTestFixture) {

  val injectedLanguageManager: InjectedLanguageManager
    get() = InjectedLanguageManager.getInstance(javaFixture.project)

  val injectedElement: PsiElement?
    get() {
      return injectedLanguageManager.findInjectedElementAt(topLevelFile ?: return null, topLevelCaretPosition)
    }

  fun assertInjectedLangAtCaret(lang: String?) {
    val injectedElement = injectedElement
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
      injectedLanguageManager.enumerate(host, PsiLanguageInjectionHost.InjectedPsiVisitor { injectedPsi, _ ->
        injected.add(host to injectedPsi)
      })
    }
    return injected
  }

  fun assertInjectedContent(vararg expectedInjectFileTexts: String) {
    UsefulTestCase.assertSameElements("injected content expected",
                                      getAllInjections().mapTo(HashSet()) { it.second }.map { it.text },
                                      expectedInjectFileTexts.toList())
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

  fun openInFragmentEditor(): EditorTestFixture {
    val project = javaFixture.project
    val quickEditHandler = QuickEditAction().invokeImpl(project, topLevelEditor, topLevelFile)
    val injectedFile = quickEditHandler.newFile
    val documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedElement?.containingFile!!)
    val offset = topLevelEditor.caretModel.offset
    val unEscapedOffset = InjectedLanguageUtil.hostToInjectedUnescaped(documentWindow, offset)
    val fragmentEditor = FileEditorManagerEx.getInstanceEx(project).openTextEditor(
      OpenFileDescriptor(project, injectedFile.virtualFile, unEscapedOffset), true
    )
    return EditorTestFixture(project, fragmentEditor, injectedFile.virtualFile)
  }

  val topLevelFile: PsiFile
    get() = javaFixture.file!!.let { injectedLanguageManager.getTopLevelFile(it) }

  val topLevelCaretPosition
    get() = topLevelEditor.caretModel.offset

  val topLevelEditor: Editor
    get() = (FileEditorManager.getInstance(javaFixture.project).getSelectedEditor(topLevelFile!!.virtualFile) as TextEditor).editor
}

data class InjectionAssertionData(val text: String, val injectedLanguage: String? = null) {
  fun hasLanguage(lang: String): InjectionAssertionData = this.copy(injectedLanguage = lang)
}

fun injectionForHost(text: String) = InjectionAssertionData(text)