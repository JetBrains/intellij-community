// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Function
import javax.swing.JComponent

@TestApplication
internal class EditorNotificationsImplTest {
  private companion object {
    val projectFixture = projectFixture()
    val moduleFixture = projectFixture.moduleFixture()
    val sourceRootFixture = moduleFixture.sourceRootFixture()
    val fileFixture = sourceRootFixture.psiFileFixture("file.txt", "text")
  }

  private val editorFixture = fileFixture.editorFixture()

  private val project: Project get() = projectFixture.get()

  @Test
  fun `updateAllNotifications from a background thread updates the open editor`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val provider = TestProvider()
    EditorNotificationProvider.EP_NAME.getPoint(project).registerExtension(provider, disposable)
    val editor = editorFixture.get()
    AsyncEditorLoader.awaitLoaded(editor)
    val file = fileFixture.get().virtualFile
    val fileEditor = withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).getAllEditorList(file).single()
    }
    val editorNotifications = EditorNotifications.getInstance(project) as EditorNotificationsImpl
    provider.firstCall.await()
    editorNotifications.awaitUpdateJobs()
    assertThat(providerNames(editorNotifications, fileEditor)).doesNotContain(TestProvider::class.java.name)

    provider.enabled = true
    withContext(Dispatchers.Default) {
      editorNotifications.updateAllNotifications()
    }
    editorNotifications.awaitUpdateJobs()

    assertThat(providerNames(editorNotifications, fileEditor)).contains(TestProvider::class.java.name)
  }

  private suspend fun providerNames(editorNotifications: EditorNotificationsImpl, fileEditor: FileEditor): List<String> {
    return withContext(Dispatchers.EDT) {
      editorNotifications.getNotificationPanels(fileEditor).keys.map { it.name }
    }
  }

  private class TestProvider : EditorNotificationProvider, DumbAware {
    val firstCall = CompletableDeferred<Unit>()

    @Volatile
    var enabled = false

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
      firstCall.complete(Unit)
      if (!enabled) {
        return null
      }
      return Function { fileEditor -> EditorNotificationPanel(fileEditor) }
    }
  }
}
