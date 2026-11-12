// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorProjection
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class EditorCoroutineScopeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val sourceRootFixture = projectFixture.moduleFixture().sourceRootFixture()
  }

  private val project get() = projectFixture.get()
  private val xmlFileFixture = sourceRootFixture.psiFileFixture("a.xml", "<a>text</a>")
  private val xmlFile get() = xmlFileFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `the release of the host editor cancels the scope of an injected editor`(): Unit = timeoutRunBlocking {
    registerInjector()
    withContext(Dispatchers.EDT) {
      withHostEditor { hostEditor ->
        val scope = injectedEditor(hostEditor).coroutineScope
        assertThat(scope.isActive).isTrue()
        EditorFactory.getInstance().releaseEditor(hostEditor)
        assertThat(scope.isActive).isFalse()
      }
    }
  }

  @Test
  fun `the disposal of an injected editor cancels its scope`(): Unit = timeoutRunBlocking {
    registerInjector()
    withContext(Dispatchers.EDT) {
      withHostEditor { hostEditor ->
        val injectedEditor = injectedEditor(hostEditor)
        val scope = injectedEditor.coroutineScope
        removeInjection(hostEditor.document)
        assertThat(injectedEditor.isDisposed).isTrue()
        assertThat(scope.isActive).isFalse()
        assertThat(hostEditor.coroutineScope.isActive).isTrue()
      }
    }
  }

  @Test
  fun `a disposed injected editor returns a cancelled scope`(): Unit = timeoutRunBlocking {
    registerInjector()
    withContext(Dispatchers.EDT) {
      withHostEditor { hostEditor ->
        val injectedEditor = injectedEditor(hostEditor)
        removeInjection(hostEditor.document)
        assertThat(injectedEditor.isDisposed).isTrue()
        assertThat(injectedEditor.coroutineScope.isActive).isFalse()
      }
    }
  }

  @Test
  fun `the garbage collector keeps an injected editor while its scope is active`(): Unit = timeoutRunBlocking {
    registerInjector()
    withContext(Dispatchers.EDT) {
      withHostEditor { hostEditor ->
        val watchedScope = watchedInjectedEditorScope(hostEditor)
        val collected = watchedScope.watcher.tryCollect(0)
        assertThat(collected).isFalse()
        removeInjection(hostEditor.document)
        assertThat(watchedScope.scope.isActive).isFalse()
      }
    }
  }

  @Test
  fun `the release of the host editor cancels the scope of a projection`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      withHostEditor { hostEditor ->
        val scope = IdentityProjection(hostEditor).coroutineScope
        assertThat(scope.isActive).isTrue()
        EditorFactory.getInstance().releaseEditor(hostEditor)
        assertThat(scope.isActive).isFalse()
      }
    }
  }

  private fun registerInjector() {
    InjectedLanguageManager.getInstance(project).registerMultiHostInjector(PlainTextInXmlTextInjector(), disposable)
  }

  private fun xmlDocument(): Document {
    return PsiDocumentManager.getInstance(project).getDocument(xmlFile)!!
  }

  private fun withHostEditor(action: (Editor) -> Unit) {
    val hostEditor = EditorFactory.getInstance().createEditor(xmlDocument(), project)
    try {
      action(hostEditor)
    } finally {
      if (!hostEditor.isDisposed) {
        EditorFactory.getInstance().releaseEditor(hostEditor)
      }
    }
  }

  private fun injectedEditor(hostEditor: Editor): Editor {
    val injectedOffset = hostEditor.document.text.indexOf("text")
    val injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(hostEditor, xmlFile, injectedOffset)
    assertThat(injectedEditor).isInstanceOf(EditorWindow::class.java)
    return injectedEditor
  }

  /**
   * The scope of an injected editor, and a watcher of that editor.
   *
   * The result holds no strong reference to the injected editor.
   */
  private fun watchedInjectedEditorScope(hostEditor: Editor): WatchedScope {
    val injectedEditor = injectedEditor(hostEditor)
    return WatchedScope(
      scope = injectedEditor.coroutineScope,
      watcher = GCWatcher.tracking(injectedEditor),
    )
  }

  /**
   * Removes the injection host from the document and commits the document.
   */
  private fun removeInjection(document: Document) {
    WriteCommandAction.runWriteCommandAction(project) {
      document.setText("<a/>")
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }
}

private class WatchedScope(
  val scope: CoroutineScope,
  val watcher: GCWatcher,
)

private class PlainTextInXmlTextInjector : MultiHostInjector, DumbAware {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val host = context as PsiLanguageInjectionHost
    registrar.startInjecting(PlainTextLanguage.INSTANCE)
    registrar.addPlace(null, null, host, TextRange(0, host.textLength))
    registrar.doneInjecting()
  }

  override fun elementsToInjectIn(): List<Class<out PsiElement>> {
    return listOf(XmlText::class.java)
  }
}

/**
 * A projection that shows the whole document of its host editor at the same offsets.
 */
private class IdentityProjection(
  override val hostEditor: Editor,
) : ImaginaryEditor(hostEditor.project!!, hostEditor.document), EditorProjection {
  override fun toHostOffset(offset: Int): Int = offset

  override fun fromHostOffset(hostOffset: Int): Int = hostOffset

  override fun toHostRange(range: TextRange): TextRange = range
}
