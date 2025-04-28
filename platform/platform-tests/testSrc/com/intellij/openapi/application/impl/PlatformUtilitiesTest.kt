// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
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

  @Test
  fun `edt wa means that background wa is not pending`(): Unit = concurrencyTest {
    launch {
      edtWriteAction {
        checkpoint(1)
        checkpoint(4)
      }
    }
    checkpoint(2)
    assertThat(ApplicationManagerEx.getApplicationEx().isBackgroundWriteActionRunningOrPending).isFalse
    checkpoint(3)
  }

  @Test
  fun `bg wa means that background wa is not pending`(): Unit = concurrencyTest {
    Assumptions.assumeTrue(useBackgroundWriteAction)
    launch {
      backgroundWriteAction {
        checkpoint(1)
        checkpoint(4)
      }
    }
    checkpoint(2)
    assertThat(ApplicationManagerEx.getApplicationEx().isBackgroundWriteActionRunningOrPending).isTrue
    checkpoint(3)
  }

  private class DummyAction() : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  @Test
  fun `action can be updated when background write action is in progress under write intent`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    withContext(Dispatchers.EDT) {
      launch(Dispatchers.Default) {
        backgroundWriteAction {
        }
      }
      Thread.sleep(50)
      ActionManager.getInstance().tryToExecute(DummyAction(), null, null, null, true)
    }
  }
}