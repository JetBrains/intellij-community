// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.injected.editor.EditorWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Checks that the blocking open methods of [TestEditorManagerImpl] open the host file for a [VirtualFileWindow], as the IDE does.
 */
@TestApplication
class TestEditorManagerInjectedFileTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `openTextEditor opens the host file at the host offset`() {
    val injection = configureInjection()
    val editor = runInEdtAndGet {
      FileEditorManager.getInstance(myFixture.project).openTextEditor(injection.descriptor(), false)
    }
    assertHostEditor(editor, injection)
  }

  @Test
  fun `openFileEditor opens the host file at the host offset`() {
    val injection = configureInjection()
    val fileEditors = runInEdtAndGet {
      @Suppress("DEPRECATION")
      FileEditorManager.getInstance(myFixture.project).openFileEditor(injection.descriptor(), false)
    }
    assertThat(fileEditors.map { it.file }).containsExactly(injection.hostFile)
    assertHostEditor((fileEditors.single() as TextEditor).editor, injection)
  }

  @Test
  fun `openFileInEditor of the code insight fixture takes the injected editor`() {
    val injection = configureInjection()
    runInEdtAndWait { myFixture.openFileInEditor(injection.windowFile) }
    assertThat(myFixture.editor).isInstanceOf(EditorWindow::class.java)
    assertThat((myFixture.editor as EditorWindow).delegate.document).isSameAs(injection.hostDocument)
    assertThat(myFixture.file.virtualFile).isSameAs(injection.windowFile)
    assertThat(FileEditorManager.getInstance(myFixture.project).openFiles).doesNotContain(injection.windowFile)
  }

  private fun configureInjection(): Injection {
    myFixture.configureByText("A.java", """
      class A {
        // language=JSON
        String s = "{\"key\": 1}";
      }
    """.trimIndent())
    return runReadActionBlocking {
      val hostFile = InjectedLanguageManager.getInstance(myFixture.project).getTopLevelFile(myFixture.file)
      val hostOffset = hostFile.text.indexOf("key")
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(hostFile, hostOffset)!!
      val windowFile = injectedElement.containingFile.virtualFile
      val documentWindow = (windowFile as VirtualFileWindow).documentWindow
      Injection(hostFile.virtualFile, hostFile.viewProvider.document, windowFile,
                injectedOffset = documentWindow.hostToInjected(hostOffset), hostOffset = hostOffset)
    }
  }

  private fun Injection.descriptor(): OpenFileDescriptor = OpenFileDescriptor(myFixture.project, windowFile, injectedOffset)

  private fun assertHostEditor(editor: Editor?, injection: Injection) {
    assertThat(editor).isNotNull.isNotInstanceOf(EditorWindow::class.java)
    assertThat(editor!!.document).isSameAs(injection.hostDocument)
    assertThat(runInEdtAndGet { editor.caretModel.offset }).isEqualTo(injection.hostOffset)
    assertThat(FileEditorManager.getInstance(myFixture.project).openFiles).doesNotContain(injection.windowFile)
  }

  private class Injection(
    val hostFile: VirtualFile,
    val hostDocument: Document,
    val windowFile: VirtualFile,
    val injectedOffset: Int,
    val hostOffset: Int,
  )
}
