// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ModalityStateListener
import com.intellij.openapi.application.impl.LaterInvocator.*
import com.intellij.openapi.util.Conditions
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.SkipInHeadlessEnvironment
import junit.framework.TestCase
import java.awt.Dialog
import java.util.concurrent.atomic.AtomicInteger

private class NumberedRunnable private constructor(private val myNumber: Int, private val myConsumer: (Int) -> Unit = {}) : Runnable {
  override fun run() = myConsumer.invoke(myNumber)

  companion object {
    internal fun withNumber(number: Int) = NumberedRunnable(number)
  }
}

@SkipInHeadlessEnvironment
class RunnableActionsTest : HeavyPlatformTestCase() {
  private val myPerProjectModalDialog = Dialog(null, "Per-project modal dialog", Dialog.ModalityType.DOCUMENT_MODAL)
  private val myApplicationModalDialog = Dialog(null, "Owned dialog", Dialog.ModalityType.DOCUMENT_MODAL)

  fun testModalityStateChangedListener() {
    val enteringOrder = booleanArrayOf(true, true, false, false)

    val enteringIndex = AtomicInteger(-1)

    val modalityStateListener = object: ModalityStateListener {
      override fun beforeModalityStateChanged(entering: Boolean, modalEntity: Any) {
        if (entering != enteringOrder[enteringIndex.incrementAndGet()]) {
          throw RuntimeException(
            "Entrance index: " + enteringIndex + "; value: " + entering + " expected value: " + enteringOrder[enteringIndex.get()])
        }
      }
    }

    LaterInvocator.addModalityStateListener(modalityStateListener, testRootDisposable)

    val project = getProject()
    Testable()
      .suspendEDT()
      .execute { invokeLater(ModalityState.nonModal(), Conditions.alwaysFalse<Any>(), NumberedRunnable.withNumber(1)) }
      .flushEDT()
      .execute { enterModal(myApplicationModalDialog) }
      .flushEDT()
      .execute { invokeLater(ModalityState.current(), Conditions.alwaysFalse<Any>(), NumberedRunnable.withNumber(2)) }
      .flushEDT()
      .execute { enterModal(project, myPerProjectModalDialog) }
      .flushEDT()
      .execute { invokeLater(ModalityState.nonModal(), Conditions.alwaysFalse<Any>(), NumberedRunnable.withNumber(3)) }
      .flushEDT()
      .execute { invokeLater(ModalityState.current(), Conditions.alwaysFalse<Any>(), NumberedRunnable.withNumber(4)) }
      .flushEDT()
      .execute { leaveModal(project, myPerProjectModalDialog) }
      .flushEDT()
      .execute { invokeLater(ModalityState.nonModal(), Conditions.alwaysFalse<Any>(), NumberedRunnable.withNumber(5)) }
      .flushEDT()
      .execute { leaveModal(myApplicationModalDialog) }
      .flushEDT()
      .continueEDT()
      .ifExceptions { exception -> TestCase.fail(exception.toString()) }
  }
}
