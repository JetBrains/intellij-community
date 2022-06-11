// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.UncaughtExceptionsExtension
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

abstract class CancellableReadActionTests {

  @Suppress("unused")
  companion object {

    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()

    @RegisterExtension
    @JvmField
    val uncaughtExceptionsExtension = UncaughtExceptionsExtension()

    @BeforeAll
    @JvmStatic
    fun init() {
      ProgressIndicatorUtils.cancelActionsToBeCancelledBeforeWrite() // init write action listener
    }
  }
}

fun <X> computeCancellable(action: () -> X): X {
  return ReadAction.computeCancellable<X, Nothing>(action)
}

fun testComputeCancellableRethrow() {
  testComputeCancellableRethrow(object : Throwable() {})
  testComputeCancellableRethrow(CancellationException())
  testComputeCancellableRethrow(ProcessCanceledException())
  testComputeCancellableRethrow(CannotReadException())
}

private inline fun <reified T : Throwable> testComputeCancellableRethrow(t: T) {
  val thrown = assertThrows<T> {
    computeCancellable {
      throw t
    }
  }
  assertSame(t, thrown)
  assertFalse(Cancellation.isCancelled())
}

fun testThrowsIfPendingWrite() {
  val finishWrite = waitForPendingWrite()
  assertThrows<CannotReadException> {
    computeCancellable {
      fail()
    }
  }
  finishWrite.up()
}

fun testThrowsIfRunningWrite() {
  val finishWrite = waitForWrite()
  assertThrows<CannotReadException> {
    computeCancellable {
      fail()
    }
  }
  finishWrite.up()
}

private fun waitForWrite(): Semaphore {
  val inWrite = Semaphore(1)
  val finishWrite = Semaphore(1)
  ApplicationManager.getApplication().invokeLater {
    runWriteAction {
      inWrite.up()
      finishWrite.timeoutWaitUp()
    }
  }
  inWrite.timeoutWaitUp()
  return finishWrite
}

fun testDoesntThrowWhenAlmostFinished() {
  val result = computeCancellable {
    testNoExceptions()
    waitForPendingWrite().up()
    assertThrows<JobCanceledException> { // cancelled
      testExceptions()
    }
    42 // but returning the result doesn't throw CannotReadException
  }
  assertEquals(42, result)
}

fun testThrowsOnWrite() {
  assertThrows<CannotReadException> {
    computeCancellable {
      testNoExceptions()
      waitForPendingWrite().up()
      testExceptions()
    }
  }
}

fun waitForPendingWrite(): Semaphore {
  val finishWrite = Semaphore(1)
  val pendingWrite = Semaphore(1)
  val listenerDisposable = Disposer.newDisposable()
  ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
    override fun beforeWriteActionStart(action: Any) {
      pendingWrite.up()
      finishWrite.timeoutWaitUp()
      Disposer.dispose(listenerDisposable)
    }
  }, listenerDisposable)
  ApplicationManager.getApplication().invokeLater {
    runWriteAction {}
  }
  pendingWrite.timeoutWaitUp()
  return finishWrite
}
