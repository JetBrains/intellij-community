// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.psi.impl.source.tree.mvcc.VersionedPsiReference
import com.intellij.psi.impl.source.tree.mvcc.PsiVersioningGarbageCollector
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestApplication
internal class VersionedPsiReferenceTest {

  @Test
  fun `overwritten referents stay reachable while psi version is frozen`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val reference = VersionedPsiReference<Payload>()
    val initialPayload = Payload("initial")
    val replacementPayload = Payload("replacement")

    runWriteAction {
      assertNull(reference.set(initialPayload))
    }

    assertReferencedWhilePsiVersionIsFrozen(reference, initialPayload) {
      assertSame(initialPayload, reference.get())

      async(Dispatchers.Default) {
        backgroundWriteAction {
          assertSame(initialPayload, reference.set(replacementPayload))
        }
      }.asCompletableFuture().get()

      assertSame(initialPayload, reference.get())
    }

    assertSame(replacementPayload, reference.get())
    application.service<PsiVersioningGarbageCollector>().awaitCleanup()
    assertNotReferenced(reference, initialPayload)
    assertReferenced(reference, replacementPayload)

    assertReferencedWhilePsiVersionIsFrozen(reference, replacementPayload) {
      assertSame(replacementPayload, reference.get())

      async(Dispatchers.Default) {
        backgroundWriteAction {
          assertSame(replacementPayload, reference.set(null))
        }
      }.asCompletableFuture().get()

      assertSame(replacementPayload, reference.get())
    }

    assertNull(reference.get())
    assertNotReferenced(reference, replacementPayload)
  }

  private fun CoroutineScope.assertReferencedWhilePsiVersionIsFrozen(
    reference: VersionedPsiReference<Payload>,
    referenced: Payload,
    action: CoroutineScope.() -> Unit,
  ) {
    val frozenVersionReady = CompletableFuture<Unit>()
    val releaseFrozenVersion = CompletableFuture<Unit>()
    val frozenVersionTask = async(Dispatchers.Default) {
      PsiVersioningService.freezePsiVersion {
        try {
          assertNotNull(InternalPsiVersioning.getCurrentPsiVersionInsideFrozenPsi())
          action()
          frozenVersionReady.complete(Unit)
          releaseFrozenVersion.get()
        }
        catch (e: Throwable) {
          frozenVersionReady.completeExceptionally(e)
          throw e
        }
      }
    }
    try {
      frozenVersionReady.get()
      assertReferenced(reference, referenced)
    }
    finally {
      releaseFrozenVersion.complete(Unit)
      frozenVersionTask.asCompletableFuture().get()
    }
  }

  private fun installVersioningListeners(disposable: Disposable) {
    val listener = PsiVersioningLockingListener()
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addSuspendingWriteActionListener(listener, disposable)
  }

  private class Payload(private val name: String) {
    override fun toString(): String {
      return name
    }
  }
}
