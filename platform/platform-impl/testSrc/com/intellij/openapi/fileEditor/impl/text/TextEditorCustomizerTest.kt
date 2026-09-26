// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class TextEditorCustomizerTest {
  private companion object {
    val project = projectFixture()
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("file.txt", "text")
    val textEditorCustomizerEp = ExtensionPointName<TextEditorCustomizer>("com.intellij.textEditorCustomizer")
  }

  @Test
  fun `long-lived customizer coroutine is not an async loader child`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val customizerScope = CompletableDeferred<CoroutineScope>()
    val customizerCancelled = CompletableDeferred<Unit>()
    val testProject = project.get()

    ExtensionTestUtil.maskExtensions(textEditorCustomizerEp, listOf(object : TextEditorCustomizer {
      override fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope) {
        customizerScope.complete(coroutineScope)
        coroutineScope.launch {
          try {
            awaitCancellation()
          }
          finally {
            customizerCancelled.complete(Unit)
          }
        }
      }
    }), disposable)

    val textEditor = withContext(Dispatchers.UiWithModelAccess) {
      writeIntentReadAction {
        TextEditorProvider.getInstance().createEditor(testProject, file.get().virtualFile) as TextEditorImpl
      }
    }
    val customizerJob = try {
      val scope = withTimeout(10.seconds) { customizerScope.await() }
      val asyncLoaderJob = textEditor.asyncLoader.coroutineScope.coroutineContext.job
      val customizerJob = scope.coroutineContext.job

      assertThat(AsyncEditorLoader.isEditorLoaded(textEditor.editor)).isTrue()
      assertThat(customizerJob.isActive).isTrue()
      assertThat(asyncLoaderJob.children.toList()).doesNotContain(customizerJob)
      customizerJob
    }
    finally {
      withContext(Dispatchers.UiWithModelAccess) {
        Disposer.dispose(textEditor)
      }
    }

    withTimeout(10.seconds) { customizerCancelled.await() }
    assertThat(customizerJob.isCancelled).isTrue()
  }
}
