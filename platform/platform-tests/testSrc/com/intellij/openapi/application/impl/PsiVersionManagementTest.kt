// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("KotlinMisorderedAssertEqualsArguments")

package com.intellij.openapi.application.impl

import com.intellij.concurrency.resetThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.application
import com.intellij.util.concurrency.TransferredWriteActionService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
internal class PsiVersionManagementTest {

  private fun installVersioningListeners(disposable: Disposable) {
    val listener = PsiVersioningLockingListener()
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addSuspendingWriteActionListener(listener, disposable)
  }

  @Test
  fun `psi version in read action`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)
    runWriteAction {
      runBlockingMaybeCancellable {

      }
    }
  }

  @Test
  fun `resetThreadContext does not affect psi versions`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)
    runWriteAction {
      val version = InternalPsiVersioning.getCurrentPsiVersion()
      doCartesianTest(version)
      application.service<TransferredWriteActionService>().runOnEdtWithTransferredWriteActionAndWait {
        doCartesianTest(version)
        application.service<TransferredWriteActionService>().runOnBackgroundThreadWithTransferredWriteActionAndWait {
          doCartesianTest(version)
        }
      }
    }
  }

  fun doCartesianTest(expectedVersion: Long) {
    runScenarios {
      runScenarios {
        Assertions.assertEquals(expectedVersion, InternalPsiVersioning.getCurrentPsiVersion())
      }
    }
  }

  fun runScenarios(runCheck: () -> Unit) {
    runCheck()
    runReadActionBlocking {
      runCheck()
    }
    runWriteAction {
      runCheck()
    }
    resetThreadContext {
      runCheck()
    }
  }
}
