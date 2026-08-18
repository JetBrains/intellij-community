// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.awt.Frame
import javax.swing.JDialog
import javax.swing.JLabel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests of [DebouncedUpdates] that need a real AWT window: [ModalityState.stateForComponent] resolves the state
 * of the component's window, so it always reports [ModalityState.nonModal] in a headless JVM.
 */
@TestApplication
@SkipInHeadlessEnvironment
class DebouncedUpdatesModalityTest {

  @Test
  fun `test withComponentModality resolves the modality state at dispatch time`() {
    timeoutRunBlocking(timeout = 1.minutes) {
      val component = JLabel()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val executed = CompletableDeferred<Unit>()
      // A modality entity is held by a weak reference, so keep a strong reference to the dialog here.
      var dialog: JDialog? = null

      try {
        // The queue is built before the component gets a window, exactly like a queue created in a constructor:
        // at this moment ModalityState.stateForComponent(component) is nonModal().
        val queue = DebouncedUpdates.forScope<Int>(scope, "test-modality", 50.milliseconds)
          .withContext(Dispatchers.EDT)
          .withComponentModality(component)
          .runLatest {
            executed.complete(Unit)
          }

        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          val modalDialog = JDialog(null as Frame?, true)
          dialog = modalDialog
          modalDialog.contentPane.add(component)
          LaterInvocator.enterModal(modalDialog)
        }

        queue.queue(1)

        // An upfront nonModal() snapshot would keep the action waiting until the dialog is left.
        withTimeoutOrNull(10.seconds) { executed.await() }
        ?: fail("The action must run in the modality state the component has when the item is dispatched")
      }
      finally {
        scope.cancel()
        val modalDialog = dialog
        if (modalDialog != null) {
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            LaterInvocator.leaveModal(modalDialog)
            modalDialog.dispose()
          }
        }
      }
    }
  }
}
