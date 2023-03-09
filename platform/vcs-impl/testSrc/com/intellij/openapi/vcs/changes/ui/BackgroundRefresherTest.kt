// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.idea.TestFor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@TestFor(classes = [BackgroundRefresher::class])
class BackgroundRefresherTest {
  val disposableRule = DisposableRule()

  @JvmField
  @Rule
  val chain = RuleChain(ApplicationRule(), disposableRule)

  @Test
  fun requestCancelsPreviousRequests() {
    val refresher = BackgroundRefresher<Int>("T", disposableRule.disposable)

    val refresh1Started = CompletableFuture<Unit>()
    val result1 = refresher.requestRefresh(0) {
      try {
        refresh1Started.complete(Unit)
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 10000) {
          ProgressManager.checkCanceled()
          Thread.sleep(10)
        }
        1
      }
      catch (t: ProcessCanceledException) {
        666
      }
    }
    refresh1Started.get()
    val result2 = refresher.requestRefresh(0) {
      2
    }

    Assert.assertEquals(2, result2.blockingGet(5000))
    Assert.assertEquals(2, result1.blockingGet(5000))
  }

  @Test
  fun disposeLeadsToProgressCancel() {
    val disposable = Disposer.newDisposable()
    val refresher = BackgroundRefresher<Unit>("T", disposable)

    val cancelled = CompletableFuture<Unit>()
    val refreshStarted = CompletableFuture<Unit>()
    refresher.requestRefresh(0) {
      try {
        refreshStarted.complete(Unit)

        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 3000) {
          ProgressManager.checkCanceled()
          Thread.sleep(10)
        }
        if (Thread.interrupted()) {
          cancelled.completeExceptionally(IllegalStateException("must not be interrupted"))
        }
        cancelled.completeExceptionally(IllegalStateException("must be cancelled"))
      }
      catch (t: ProcessCanceledException) {
        if (Thread.interrupted()) {
          cancelled.completeExceptionally(IllegalStateException("must not be interrupted"))
        }
        cancelled.complete(Unit)
      }
    }
    refreshStarted.get()
    Disposer.dispose(disposable)
    cancelled.get(10, TimeUnit.SECONDS)
  }

  @Test
  fun disposeLeadsToImmediateCancelResult() {
    val disposable = Disposer.newDisposable()
    val refresher = BackgroundRefresher<Unit>("T", disposable)

    val refreshStarted = CompletableFuture<Unit>()
    val refreshEnded = CompletableFuture<Unit>()
    val result = refresher.requestRefresh(0) {
      refreshStarted.complete(Unit)
      Thread.sleep(2000)
      refreshEnded.complete(Unit)
    }
    refreshStarted.get()
    Disposer.dispose(disposable)
    Assert.assertNull(result.blockingGet(10000))
    Assert.assertFalse(refreshEnded.isDone)
  }

  @Test
  fun refreshWhenDisposed() {
    val disposable = Disposer.newDisposable()
    val refresher = BackgroundRefresher<Any>("T", disposable)
    Disposer.dispose(disposable)

    val refreshWhenDisposed = refresher.requestRefresh(0) {
      1
    }
    Assert.assertNull(refreshWhenDisposed.blockingGet(10000))
  }

  @Test
  fun refreshReturnsException() {
    val refresher = BackgroundRefresher<Any>("T", disposableRule.disposable)

    val result = refresher.requestRefresh(0) {
      throw IllegalStateException("some text")
    }

    try {
      result.blockingGet(10000)
      Assert.fail()
    } catch (e: IllegalStateException) {
      Assert.assertEquals("some text", e.message)
    }
  }

  @Test
  fun refreshReturnsCanceledException() {
    val refresher = BackgroundRefresher<Any>("T", disposableRule.disposable)
    val result = refresher.requestRefresh(0) {
      throw ProcessCanceledException()
    }
    try {
      result.blockingGet(10000)
      Assert.fail()
    } catch (_: ProcessCanceledException) {
    }
  }
}