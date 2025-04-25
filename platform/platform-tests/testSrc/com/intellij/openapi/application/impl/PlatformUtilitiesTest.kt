// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class PlatformUtilitiesTest {
  val project = projectFixture(openAfterCreation = true)

  @Test
  fun `waitUntilIndexesReady works with background WA`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val project = project.get()
    val dumbActionCompleted = AtomicBoolean(false)
    DumbService.getInstance(project).queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        application.invokeAndWait {
          dumbActionCompleted.set(true)
        }
      }
    })
    launch(Dispatchers.Default) {
      backgroundWriteAction {
      }
    }
    Thread.sleep(100) // do not release EDT event
    Assertions.assertFalse(dumbActionCompleted.get())
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    Assertions.assertTrue(dumbActionCompleted.get())
  }

  @Test
  fun `invokeAndWaitRelaxed does not take lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    ApplicationManagerEx.getApplicationEx().invokeAndWaitRelaxed(
      {
        assertThat(application.isWriteAccessAllowed).isFalse()
        assertThat(application.isReadAccessAllowed).isFalse()
        assertThat(application.isWriteIntentLockAcquired).isFalse()
        assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue()
      }, ModalityState.nonModal())
  }

  @Test
  fun `raw background write action is not allowed`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    assertThrows<IllegalStateException> {
      ApplicationManager.getApplication().runWriteAction {
        fail<Nothing>()
      }
    }
  }
}