// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.ExecutionInitiator
import com.intellij.concurrency.ExecutionInitiatorElement
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.Alarm
import com.intellij.util.application
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestApplication
@Timeout(30, unit = TimeUnit.SECONDS)
@ExtendWith(ThreadContextPropagationTest.Enabler::class)
class ExecutionInitiatorPropagationTest {

  @Service(Service.Level.APP)
  class ScopeHolder(val cs: CoroutineScope)

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `Swing invokeLater propagates initiator`(): Unit = timeoutRunBlocking {
    propagationTest { SwingUtilities.invokeLater(it) }
  }

  @Test
  fun `application invokeLater propagates initiator`(): Unit = timeoutRunBlocking {
    propagationTest { application.invokeLater(it) }
  }

  @Test
  fun `application invokeAndWait propagates initiator`(): Unit = timeoutRunBlocking {
    propagationTest { application.invokeAndWait(it) }
  }

  @Test
  @Suppress("SSBasedInspection")
  fun `Alarm propagates scope initiator`(): Unit = timeoutRunBlocking {
    // Legacy Alarm captures only ClientId from the ambient thread context, so pass the initiator through its owning scope.
    val attributedScope = CoroutineScope(coroutineContext + ExecutionInitiator.MCP.contextElement)
    propagationTest { Alarm(attributedScope).addRequest(it, 0) }
  }

  @Test
  fun `app executor service propagates initiator`(): Unit = timeoutRunBlocking {
    propagationTest { AppExecutorUtil.getAppExecutorService().submit(it) }
  }

  @Test
  @Suppress("SSBasedInspection")
  fun `MergingUpdateQueue propagates scope initiator`(): Unit = timeoutRunBlocking {
    // The queue's legacy Alarm captures only ClientId from the ambient thread context.
    val attributedScope = CoroutineScope(coroutineContext + ExecutionInitiator.MCP.contextElement)
    val queue = MergingUpdateQueue(
      "ExecutionInitiatorPropagationTest",
      0,
      true,
      null,
      disposable,
      null,
      Alarm.ThreadToUse.POOLED_THREAD,
      attributedScope,
    )
    propagationTest {
      queue.queue(Update.create(this) { it.run() })
    }
  }

  @Test
  fun `executeOnPooledThread propagates initiator`(): Unit = timeoutRunBlocking {
    propagationTest { application.executeOnPooledThread(it) }
  }

  @Test
  fun `non-blocking read action propagates initiator`(): Unit = timeoutRunBlocking {
    propagationTest { runnable ->
      ReadAction.nonBlocking(Callable { runnable.run() }).submit(AppExecutorUtil.getAppExecutorService())
    }
  }

  @Test
  fun `child coroutine propagates initiator`(): Unit = timeoutRunBlocking {
    withContext(ExecutionInitiator.MCP.contextElement) {
      launch(Dispatchers.EDT) {
        assertSame(actual = ExecutionInitiator.currentOrNull(), expected = ExecutionInitiator.MCP)
      }.join()
    }
  }

  @Test
  fun `platform service scope captures ambient initiator automatically`(): Unit = timeoutRunBlocking {
    val serviceScope = service<ScopeHolder>().cs
    assertNull(serviceScope.coroutineContext[ExecutionInitiatorElement])

    val actual = withContext(ExecutionInitiator.MCP.contextElement) {
      serviceScope.async { ExecutionInitiator.currentOrNull() }.await()
    }

    assertSame(actual = actual, expected = ExecutionInitiator.MCP)
    assertNull(serviceScope.async { ExecutionInitiator.currentOrNull() }.await())
  }

  @Test
  fun `explicit launch initiator wins over ambient initiator`(): Unit = timeoutRunBlocking {
    val serviceScope = service<ScopeHolder>().cs

    val actual = withContext(ExecutionInitiator.MCP.contextElement) {
      serviceScope.async(ExecutionInitiator.USER.contextElement) { ExecutionInitiator.currentOrNull() }.await()
    }

    assertSame(actual = actual, expected = ExecutionInitiator.USER)
  }

  @Test
  @Suppress("SSBasedInspection")
  fun `service scope initiator wins over ambient initiator`(): Unit = timeoutRunBlocking {
    val serviceScope = service<ScopeHolder>().cs
    val attributedScope = CoroutineScope(serviceScope.coroutineContext + ExecutionInitiator.USER.contextElement)

    val actual = withContext(ExecutionInitiator.MCP.contextElement) {
      attributedScope.async { ExecutionInitiator.currentOrNull() }.await()
    }

    assertSame(actual = actual, expected = ExecutionInitiator.USER)
  }

  @Test
  @Suppress("SSBasedInspection")
  fun `unrelated coroutine scope requires explicit initiator propagation`(): Unit = timeoutRunBlocking {
    val unrelatedScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job) + Dispatchers.Default)
    try {
      withContext(ExecutionInitiator.MCP.contextElement) {
        val implicitInitiator = unrelatedScope.async { ExecutionInitiator.currentOrNull() }.await()
        val explicitInitiator = unrelatedScope.async(ExecutionInitiator.currentContextElementOrEmpty()) {
          ExecutionInitiator.currentOrNull()
        }.await()

        assertNull(implicitInitiator)
        assertSame(actual = explicitInitiator, expected = ExecutionInitiator.MCP)
      }
    }
    finally {
      unrelatedScope.cancel()
    }
  }

  @Test
  fun `action callbacks preserve external initiator`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ExecutionInitiator.MCP.contextElement) {
      val action = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) = Unit
      }
      val event = AnActionEvent.createEvent(action, DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
      ActionManagerEx.getInstanceEx().performWithActionCallbacks(action, event) {
        assertSame(actual = ExecutionInitiator.currentOrNull(), expected = ExecutionInitiator.MCP)
      }
    }
  }

  @Test
  fun `captured initiator wins over context at execution`(): Unit = timeoutRunBlocking {
    val captured = AtomicReference<Runnable>()
    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "Execution Initiator Propagation Test",
      Executor(captured::set),
      1,
      disposable,
    )
    installThreadContext(ExecutionInitiator.MCP.contextElement) {
      executor.submit {
        assertSame(actual = ExecutionInitiator.currentOrNull(), expected = ExecutionInitiator.MCP)
      }
    }

    installThreadContext(ExecutionInitiator.USER.contextElement) {
      captured.get().run()
    }
  }

  @Test
  fun `context access`(): Unit = timeoutRunBlocking {
    assertNull(ExecutionInitiator.currentOrNull())
    assertSame(EmptyCoroutineContext, ExecutionInitiator.currentContextElementOrEmpty())
    installThreadContext(ExecutionInitiator.USER.contextElement) {
      assertSame(actual = ExecutionInitiator.currentOrNull(), expected = ExecutionInitiator.USER)
      assertSame(actual = ExecutionInitiator.currentContextElementOrEmpty(), expected = ExecutionInitiator.USER.contextElement)
    }
  }

  private suspend fun propagationTest(schedule: (Runnable) -> Unit) {
    val actual = CompletableDeferred<ExecutionInitiator?>()
    installThreadContext(ExecutionInitiator.MCP.contextElement) {
      schedule(Runnable {
        actual.complete(ExecutionInitiator.currentOrNull())
      })
    }
    assertSame(actual = actual.await(), expected = ExecutionInitiator.MCP)
  }
}

private fun ExecutionInitiator.Companion.currentContextElementOrEmpty(): CoroutineContext =
  currentOrNull()?.contextElement ?: EmptyCoroutineContext
