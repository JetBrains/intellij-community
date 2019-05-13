/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ModalityStateListener
import com.intellij.openapi.application.impl.LaterInvocator.*
import com.intellij.testFramework.PlatformTestCase
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
class RunnableActionsTest : PlatformTestCase() {
  private val myPerProjectModalDialog = Dialog(null, "Per-project modal dialog", Dialog.ModalityType.DOCUMENT_MODAL)
  private val myApplicationModalDialog = Dialog(null, "Owned dialog", Dialog.ModalityType.DOCUMENT_MODAL)

  fun testModalityStateChangedListener() {
    val enteringOrder = booleanArrayOf(true, true, false, false)

    val enteringIndex = AtomicInteger(-1)

    val modalityStateListener = ModalityStateListener { entering ->
      if (entering != enteringOrder[enteringIndex.incrementAndGet()]) {
        throw RuntimeException(
          "Entrance index: " + enteringIndex + "; value: " + entering + " expected value: " + enteringOrder[enteringIndex.get()])
      }
    }

    LaterInvocator.addModalityStateListener(modalityStateListener, testRootDisposable)

    val removeModalityListener = { LaterInvocator.removeModalityStateListener(modalityStateListener) }

    val project = getProject()
    Testable()
      .suspendEDT()
      .execute { invokeLater(NumberedRunnable.withNumber(1), ModalityState.NON_MODAL) }
      .flushEDT()
      .execute { enterModal(myApplicationModalDialog) }
      .flushEDT()
      .execute { invokeLater(NumberedRunnable.withNumber(2), ModalityState.current()) }
      .flushEDT()
      .execute { enterModal(project, myPerProjectModalDialog) }
      .flushEDT()
      .execute { invokeLater(NumberedRunnable.withNumber(3), ModalityState.NON_MODAL) }
      .flushEDT()
      .execute { invokeLater(NumberedRunnable.withNumber(4), ModalityState.current()) }
      .flushEDT()
      .execute { leaveModal(project, myPerProjectModalDialog) }
      .flushEDT()
      .execute { invokeLater(NumberedRunnable.withNumber(5), ModalityState.NON_MODAL) }
      .flushEDT()
      .execute { leaveModal(myApplicationModalDialog) }
      .flushEDT()
      .continueEDT()
      .execute(removeModalityListener)
      .ifExceptions { exception -> TestCase.fail(exception.toString()) }
  }
}
