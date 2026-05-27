// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ModalityStateListener
import com.intellij.openapi.application.impl.LaterInvocator.enterModal
import com.intellij.openapi.application.impl.LaterInvocator.invokeLater
import com.intellij.openapi.application.impl.LaterInvocator.leaveModal
import com.intellij.openapi.util.Conditions
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.concurrency.ThreadingAssertions
import java.awt.Dialog
import java.util.concurrent.atomic.AtomicInteger

private class NumberedRunnable (private val myNumber: Int, private val myConsumer: (Int) -> Unit = {}) : Runnable {
  override fun run() = myConsumer.invoke(myNumber)
}

@SkipInHeadlessEnvironment
class RunnableActionsTest : HeavyPlatformTestCase() {
  private val myPerProjectModalDialog = Dialog(null, "Per-project modal dialog", Dialog.ModalityType.DOCUMENT_MODAL)
  private val myApplicationModalDialog = Dialog(null, "Owned dialog", Dialog.ModalityType.DOCUMENT_MODAL)

  fun testModalityStateChangedListener() {
    LaterInvocator.addModalityStateListener(createModalityStateListener(), testRootDisposable)

    assertModalityStateNotifications()
  }

  fun testModalityStateChangedTopicListener() {
    ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(ModalityStateListener.TOPIC, createModalityStateListener())

    assertModalityStateNotifications()
  }

  private fun createModalityStateListener(): ModalityStateListener {
    val enteringOrder = booleanArrayOf(true, true, false, false)
    val enteringIndex = AtomicInteger(-1)

    return object : ModalityStateListener {
      override fun beforeModalityStateChanged(entering: Boolean, modalEntity: Any) {
        if (entering != enteringOrder[enteringIndex.incrementAndGet()]) {
          throw RuntimeException(
            "Entrance index: " + enteringIndex + "; value: " + entering + " expected value: " + enteringOrder[enteringIndex.get()])
        }
      }
    }
  }

  private fun assertModalityStateNotifications() {
    ThreadingAssertions.assertEventDispatchThread()
    val project = getProject()
    assertTrue(
    Testable()
      .suspendEDT()
      .execute { invokeLater(ModalityState.nonModal(), Conditions.alwaysFalse<Any>(), NumberedRunnable(1)) }
      .flushEDT()
      .execute { enterModal(myApplicationModalDialog) }
      .flushEDT()
      .execute { invokeLater(ModalityState.current(), Conditions.alwaysFalse<Any>(), NumberedRunnable(2)) }
      .flushEDT()
      .execute { enterModal(project, myPerProjectModalDialog) }
      .flushEDT()
      .execute { invokeLater(ModalityState.nonModal(), Conditions.alwaysFalse<Any>(), NumberedRunnable(3)) }
      .flushEDT()
      .execute { invokeLater(ModalityState.current(), Conditions.alwaysFalse<Any>(), NumberedRunnable(4)) }
      .flushEDT()
      .execute { leaveModal(project, myPerProjectModalDialog) }
      .flushEDT()
      .execute { invokeLater(ModalityState.nonModal(), Conditions.alwaysFalse<Any>(), NumberedRunnable(5)) }
      .flushEDT()
      .execute { leaveModal(myApplicationModalDialog) }
      .flushEDT()
      .continueEDT()
      .ifExceptions { exception -> fail(exception.toString()) }
      .completed()
      .waitCompletion(100_000))
  }
}
