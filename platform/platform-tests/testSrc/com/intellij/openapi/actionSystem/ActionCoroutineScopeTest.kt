// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class ActionCoroutineScopeTest {

  @OptIn(ExperimentalStdlibApi::class)
  @Test
  fun `coroutineScope is available in actionPerformed`() = timeoutRunBlocking {
    val gotScope = AtomicBoolean(false)
    val jobRan = CompletableDeferred<Boolean>()
    val scopeDispatcher = AtomicReference<CoroutineDispatcher>()

    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        val scope = e.coroutineScope
        gotScope.set(true)
        scopeDispatcher.set(scope.coroutineContext[CoroutineDispatcher])
        scope.launch {
          jobRan.complete(true)
        }
      }
    }

    val event = com.intellij.testFramework.TestActionEvent.createTestEvent(action)
    val result = withContext(Dispatchers.EDT) {
      ActionUtil.performAction(action, event)
    }

    Assertions.assertTrue(result is AnActionResult.Performed, "Action should be performed")
    Assertions.assertTrue(gotScope.get(), "Coroutine scope should be available inside actionPerformed")
    // Wait for the launched coroutine to run
    Assertions.assertTrue(jobRan.awaitWithTimeout(5, TimeUnit.SECONDS) == true,
                          "Coroutine launched from action scope should run")
    Assertions.assertTrue(scopeDispatcher.get() == Dispatchers.Default, "Coroutine scope dispatcher should be Dispatchers.Default")
  }

  @Test
  fun `coroutineScope outside actionPerformed throws`() {
    val event = com.intellij.testFramework.TestActionEvent.createTestEvent()
    try {
      @Suppress("UNUSED_VARIABLE")
      val scope = event.coroutineScope
      Assertions.fail<Nothing>("Expected IllegalArgumentException when accessing coroutineScope outside actionPerformed")
    }
    catch (e: IllegalStateException) {
      Assertions.assertTrue(e.message?.contains("Coroutine scope is supported only for `actionPerformed`") == true)
    }
  }

  @Test
  fun `action context element available in coroutine`() {
    timeoutRunBlocking {
      val ctxElementDeferred = CompletableDeferred<com.intellij.openapi.actionSystem.ex.ActionContextElement>()

      val actionId = "TestActionId"
      val action = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
          // emulate registered action id via template text just for diagnostics
          e.coroutineScope.launch {
            val element = ActionUtil.getActionThreadContext()
            if (element != null) {
              ctxElementDeferred.complete(element)
            }
            else {
              ctxElementDeferred.completeExceptionally(AssertionError("ActionContextElement must be present in action coroutine"))
            }
          }
        }
      }

      // Use a presentation with actionId-like name to check that element carries id and place
      action.templatePresentation.text = actionId
      val event = com.intellij.testFramework.TestActionEvent.createTestEvent(action)

      val result = withContext(Dispatchers.EDT) {
        ActionUtil.performAction(action, event)
      }
      Assertions.assertTrue(result is AnActionResult.Performed, "Action should be performed")

      val element = ctxElementDeferred.awaitWithTimeout(5, TimeUnit.SECONDS)
      Assertions.assertTrue(element != null, "ActionContextElement must be available in coroutine")
      val notNull = element!!
      // The ActionManagerImpl uses action id if available, otherwise class name; in tests via TestActionEvent place is ""
      Assertions.assertEquals("", notNull.place)
      // No input event is provided by TestActionEvent, so id is expected to be -1
      Assertions.assertTrue(notNull.inputEventId == -1)
    }
  }

  @Test
  fun `action context element propagates across dispatchers`() {
    timeoutRunBlocking {
      val ok = CompletableDeferred<Boolean>()
      val action = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
          e.coroutineScope.launch {
            // Switch to a background dispatcher and check that context is still present
            withContext(Dispatchers.Default) {
              val element = ActionUtil.getActionThreadContext()
              ok.complete(element != null)
            }
          }
        }
      }

      val event = com.intellij.testFramework.TestActionEvent.createTestEvent(action)
      val result = withContext(Dispatchers.EDT) {
        ActionUtil.performAction(action, event)
      }
      Assertions.assertTrue(result is AnActionResult.Performed)
      Assertions.assertTrue(ok.awaitWithTimeout(5, TimeUnit.SECONDS) == true,
                            "ActionContextElement should propagate across dispatcher switches")
    }
  }

  @Test
  fun `action coroutineScope is alive completed after work finishes`() = timeoutRunBlocking {
    val capturedScope = CompletableDeferred<kotlinx.coroutines.CoroutineScope>()
    val jobDone = CompletableDeferred<Unit>()

    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        val scope = e.coroutineScope
        // expose scope to the test
        capturedScope.complete(scope)
        // launch a short task inside the scope
        scope.launch {
          jobDone.complete(Unit)
        }
      }
    }

    val event = com.intellij.testFramework.TestActionEvent.createTestEvent(action)
    val result = withContext(Dispatchers.EDT) {
      ActionUtil.performAction(action, event)
    }
    Assertions.assertTrue(result is AnActionResult.Performed)

    // Ensure the launched job really finished
    Assertions.assertTrue(jobDone.awaitWithTimeout(5, TimeUnit.SECONDS) != null,
                          "Job launched in action coroutine scope should complete")

    val scope = capturedScope.awaitWithTimeout(5, TimeUnit.SECONDS)
    Assertions.assertTrue(scope != null, "Scope should be captured from actionPerformed")
    val job = scope!!.coroutineContext[Job]
    Assertions.assertTrue(job != null, "Scope should have a Job in its context")
    // After all computations are completed, the scope must still be alive
    Assertions.assertTrue(job!!.isActive, "Action coroutine scope should be active after work completes")
  }

  @Test
  fun `coroutineScope is not available inside update`() {
    val attempted = AtomicBoolean(false)
    val action = object : AnAction() {
      override fun update(e: AnActionEvent) {
        try {
          attempted.set(true)
          @Suppress("UNUSED_VARIABLE")
          val s = e.coroutineScope
          Assertions.fail<Nothing>("coroutineScope must not be available during update phase")
        }
        catch (e1: IllegalStateException) {
          Assertions.assertTrue(e1.message?.contains("only for `actionPerformed`") == true)
        }
      }

      override fun actionPerformed(e: AnActionEvent) {}
    }

    val event = com.intellij.testFramework.TestActionEvent.createTestEvent(action)
    val result = ActionUtil.updateAction(action, event)
    Assertions.assertTrue(result is AnActionResult.Performed)
    Assertions.assertTrue(attempted.get(), "update() should have been invoked")
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> CompletableDeferred<T>.awaitWithTimeout(timeout: Long, unit: TimeUnit): T? {
  val deadline = System.nanoTime() + unit.toNanos(timeout)
  while (!isCompleted && System.nanoTime() < deadline) {
    // allow EDT to process events, since tests run on EDT
    try {
      Thread.sleep(10)
    }
    catch (_: InterruptedException) {
      // ignore
    }
  }
  return if (isCompleted) getCompleted() else null
}
