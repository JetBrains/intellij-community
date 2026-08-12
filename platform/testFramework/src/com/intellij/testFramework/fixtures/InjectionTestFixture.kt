// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.codeInsight.intention.impl.QuickEditHandler
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadActionBlocking
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.util.LinkedList

/**
 * Helper around [CodeInsightTestFixture] for testing language injections: inspecting the injections in the file currently opened in the
 * fixture, asserting on them, and opening injected fragments in a quick-edit editor.
 *
 * All assertions are performed against the top-level (host) file, see [topLevelFile].
 */
class InjectionTestFixture(private val javaFixture: CodeInsightTestFixture) {

  /** [InjectedLanguageManager] of the project of the underlying fixture. */
  val injectedLanguageManager: InjectedLanguageManager
    get() = InjectedLanguageManager.getInstance(javaFixture.project)

  /** Injected element at the caret position of the [topLevelEditor], or `null` if nothing is injected there. */
  val injectedElement: PsiElement?
    get() {
      return injectedLanguageManager.findInjectedElementAt(topLevelFile, topLevelCaretPosition)
    }

  /**
   * Asserts that the language injected at the caret has the given [lang] id.
   *
   * Passing `null` asserts that there is no injection at the caret at all.
   */
  fun assertInjectedLangAtCaret(lang: String?) {
    val injectedElement = injectedElement
    if (lang != null) {
      requireNotNull(injectedElement) { "injection of '$lang' expected" }
      TestCase.assertEquals(lang, injectedElement.language.id)
    }
    else {
      TestCase.assertNull(injectedElement)
    }
  }

  /**
   * Returns every injection found in the [topLevelFile] as a `host element to injected file` pair.
   *
   * A host with several injected fragments (a concatenation, for instance) contributes one pair per fragment.
   * Reads PSI, so it has to be called under a read action.
   */
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

  /**
   * Asserts that the texts of all distinct injected files in the [topLevelFile] are exactly [expectedInjectFileTexts], in any order.
   */
  fun assertInjectedContent(vararg expectedInjectFileTexts: String) {
    assertInjectedContent("injected content expected", expectedInjectFileTexts.toList())
  }

  /** Same as [assertInjectedContent], but reports the failure with the given [message]. */
  fun assertInjectedContent(message: String, expectedFilesTexts: List<String>) {
    UsefulTestCase.assertSameElements(message,
                                      getAllInjections().mapTo(HashSet()) { it.second }.map { it.text },
                                      expectedFilesTexts)
  }

  /**
   * Asserts that every one of [expectedInjections] is present in the [topLevelFile].
   *
   * Each expectation is matched against a host element with the same text and an injected file with the same language id, and consumes
   * that injection, so repeating the same expectation twice requires two matching injections. Extra injections are allowed,
   * use [assertInjectedContent] to assert on the complete set.
   */
  fun assertInjected(vararg expectedInjections: InjectionAssertionData) {
    runReadActionBlocking {
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
  }

  /** Asserts that none of [notExpectedInjections] is present in the [topLevelFile]. Matching works as in [assertInjected]. */
  fun assertNotInjected(vararg notExpectedInjections: InjectionAssertionData) {
    runReadActionBlocking {
      val notExpected = notExpectedInjections.toCollection(LinkedList())
      val foundInjections = getAllInjections().toCollection(LinkedList())

      while (notExpected.isNotEmpty()) {
        val (text, injectedLanguage) = notExpected.pop()
        val matchingInjection = foundInjections.find { (psi, psiFile) -> psi.text == text && psiFile.language.id == injectedLanguage }
        if (matchingInjection != null) fail("not expected injection '$text' -> '$injectedLanguage' is found")
      }
    }
  }

  /**
   * Invokes the "Edit Fragment" ([QuickEditAction]) at the caret and returns a fixture over the opened fragment editor.
   */
  fun openInFragmentEditor(): EditorTestFixture {
    val quickEditHandler = QuickEditAction().invokeImpl(javaFixture.project, topLevelEditor, topLevelFile)
    return openInFragmentEditor(quickEditHandler)
  }

  /**
   * Opens the fragment file of an already created [quickEditHandler] and returns a fixture over its editor.
   *
   * The caret of the fragment editor is placed at the position corresponding to the caret in the [topLevelEditor].
   */
  fun openInFragmentEditor(quickEditHandler: QuickEditHandler): EditorTestFixture {
    val injectedFile = quickEditHandler.newFile
    val project = javaFixture.project
    val documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedElement?.containingFile!!)
    val offset = topLevelEditor.caretModel.offset
    val unEscapedOffset = InjectedLanguageUtil.hostToInjectedUnescaped(documentWindow, offset)
    val fragmentEditor = FileEditorManagerEx.getInstanceEx(project).openTextEditor(
      OpenFileDescriptor(project, injectedFile.virtualFile, unEscapedOffset), true
    )
    return EditorTestFixture(project, fragmentEditor!!, injectedFile.virtualFile)
  }

  /** Host file of the file currently opened in the fixture: the file itself unless the fixture is positioned inside an injected fragment. */
  val topLevelFile: PsiFile
    get() = javaFixture.file!!.let { injectedLanguageManager.getTopLevelFile(it) }

  /** Caret offset in the [topLevelEditor], i.e. in host coordinates. */
  val topLevelCaretPosition: Int
    get() = topLevelEditor.caretModel.offset

  /** Editor of the [topLevelFile]. */
  val topLevelEditor: Editor
    get() = (FileEditorManager.getInstance(javaFixture.project).getSelectedEditor(topLevelFile.virtualFile) as TextEditor).editor
}

/**
 * A single injection expected by [InjectionTestFixture.assertInjected] or [InjectionTestFixture.assertNotInjected].
 *
 * @param text text of the injection host element, as it appears in the host file (including quotes and escapes, if any)
 * @param injectedLanguage id of the language expected to be injected into that host
 */
data class InjectionAssertionData(val text: String, val injectedLanguage: String? = null) {
  /** Returns a copy of this expectation with [injectedLanguage] set to [lang]. */
  fun hasLanguage(lang: String): InjectionAssertionData = this.copy(injectedLanguage = lang)
}

/** Starts building an injection expectation for a host element with the given [text], see [InjectionAssertionData.hasLanguage]. */
fun injectionForHost(text: String): InjectionAssertionData = InjectionAssertionData(text)

/**
 * Asserts that the language with the given [langId] is injected in the middle of each of [fragmentTexts].
 *
 * Each fragment text is looked up in the document of the currently opened editor, and the injection is checked at the offset in the middle
 * of the first occurrence. Passing `null` as [langId] asserts that there is no injection at these offsets.
 */
fun CodeInsightTestFixture.assertInjectedLanguage(langId: String?, vararg fragmentTexts: String) {
  runReadActionBlocking {
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    val doc = editor.document

    for (text in fragmentTexts) {
      val index = doc.text.indexOf(text)
      if (index < 0) fail("No such text in document: $text")

      val pos = index + text.length / 2
      val injectedElement = injectedLanguageManager.findInjectedElementAt(file, pos)

      if (langId != null) {
        requireNotNull(injectedElement) { "There should be injected element at $pos with text '$text'" }
        assertEquals("Injected Language don't match", langId, injectedElement.language.id)
      }
      else {
        assertNull("There should be no injected element at $pos with text '$text'", injectedElement)
      }
    }
  }
}

/**
 * Asserts that the injection host containing each of [referenceTexts] has a reference of type [referenceClass].
 *
 * Each reference text is looked up in the document of the currently opened editor, and the host is taken at the offset in the middle of the
 * first occurrence: either the element there or its parent.
 */
fun CodeInsightTestFixture.assertInjectedReference(referenceClass: Class<*>, vararg referenceTexts: String) {
  runReadActionBlocking {
    val provider = file.viewProvider
    val documentText = editor.document.text

    for (refText in referenceTexts) {
      val pos = documentText.indexOf(refText) + refText.length / 2

      val element = provider.findElementAt(pos)
      requireNotNull(element) { "There should be element at $pos" }

      val host = element as? PsiLanguageInjectionHost ?: element.parent as? PsiLanguageInjectionHost
      requireNotNull(host) { "There should be injection host at $pos" }

      val references = host.references
      assertTrue("There should be references in element", references.isNotEmpty())

      val reference = references.find { referenceClass.isInstance(it) }
      requireNotNull(reference) { "There should be reference of type ${referenceClass} in element" }
    }
  }
}

/** Reified overload of [assertInjectedReference] taking the expected reference type as a type parameter. */
inline fun <reified T> CodeInsightTestFixture.assertInjectedReference(vararg fragmentTexts: String) {
  this.assertInjectedReference(T::class.java, *fragmentTexts)
}
