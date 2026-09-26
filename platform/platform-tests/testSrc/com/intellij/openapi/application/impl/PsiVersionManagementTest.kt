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
import com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap
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

  /**
   * AI-generated test.
   *
   * A regression test for `InternalPsiVersioning.PsiVersionRegistry.minVersionForCleaning`.
   *
   * A barrier for a cleanup must belong to the main timeline, because a forked version is observable by itself only.
   * A live forked version is odd, so the barrier must round it down to its even base.
   * The write action is necessary: it drops `forkBase` from the registry, which makes the forked version the lowest one.
   */
  @Test
  fun `minVersionForCleaning rounds a live forked timeline down to the main timeline`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)
    val registry = InternalPsiVersioning.PsiVersionRegistry.instance
    val forkBase = registry.latestPublishedVersion
    // this is what `PsiVersioningServiceImpl.doForkTimeline` does
    val forkedVersion = forkBase + 1
    registry.rememberFrozenVersionUnsafe(forkedVersion)
    try {
      runWriteAction { }

      val barrier = registry.minVersionForCleaning()
      Assertions.assertEquals(0L, barrier % 2, "the barrier $barrier does not belong to the main timeline")
      Assertions.assertTrue(barrier <= forkBase, "the barrier $barrier collects the base $forkBase of a live fork")
    }
    finally {
      registry.forgetFrozenVersionUnsafe(forkedVersion)
    }
  }

  /**
   * AI-generated test.
   *
   * A forked timeline holds the cleanup barrier of the whole application, so a release must let the barrier move again.
   * Without the release, the base of the fork stays alive forever.
   */
  @Test
  fun `a release of a forked timeline lets the cleanup barrier pass the forked version`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)
    val registry = InternalPsiVersioning.PsiVersionRegistry.instance
    val forkBase = registry.latestPublishedVersion
    val forkedVersion = forkBase + 1
    registry.rememberFrozenVersionUnsafe(forkedVersion)
    var released = false
    try {
      runWriteAction { }

      Assertions.assertTrue(registry.minVersionForCleaning() <= forkBase,
                            "a live fork must hold the barrier at or below its base $forkBase")

      registry.forgetFrozenVersionUnsafe(forkedVersion)
      released = true
      runWriteAction { }

      Assertions.assertTrue(registry.minVersionForCleaning() > forkedVersion,
                            "the barrier must pass the released forked version $forkedVersion")
    }
    finally {
      if (!released) {
        registry.forgetFrozenVersionUnsafe(forkedVersion)
      }
    }
  }

  /**
   * AI-generated test.
   *
   * The survival of a live fork, end to end over the registry and [VersionedPayloadMap].
   *
   * `minVersionForCleaning` is covered on its own. This test states the effect that the barrier must have: many write
   * actions move the main timeline far ahead, and the payload of the live fork still survives a cleanup.
   */
  @Test
  fun `a live forked timeline keeps its payload across many write actions`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)
    val registry = InternalPsiVersioning.PsiVersionRegistry.instance
    val forkBase = registry.latestPublishedVersion
    val forkedVersion = forkBase + 1
    registry.rememberFrozenVersionUnsafe(forkedVersion)
    try {
      val published = "published"
      val forked = "forked"
      val map = VersionedPayloadMap.create(forkBase, published, forkedVersion, forked)

      repeat(5) { runWriteAction { } }

      val cleaned = map.cleanupStaleVersions(registry.minVersionForCleaning()) ?: map

      Assertions.assertSame(forked, cleaned.lowerBound(forkedVersion),
                            "the payload of the live fork $forkedVersion must survive the cleanup")
      Assertions.assertSame(published, cleaned.lowerBound(registry.latestPublishedVersion),
                            "the published payload must stay visible to the main timeline")
    }
    finally {
      registry.forgetFrozenVersionUnsafe(forkedVersion)
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
