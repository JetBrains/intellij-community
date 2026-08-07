// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
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
}
