// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.ResetPluginsStateResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JPanel

@TestApplication
internal class LegacyPluginUiHostTest {
  @Test
  fun `session operations are forwarded while host is active`() {
    val calls = mutableListOf<String>()
    val cancelJob = CompletableDeferred<Unit>()
    val lifecycle = createLifecycle(calls, cancelJob = cancelJob)
    var appliedWithoutRestart: Boolean? = null

    assertThat(lifecycle.isModified()).isTrue()
    lifecycle.apply(null, Consumer { appliedWithoutRestart = it }, Consumer { throw AssertionError(it) })
    lifecycle.reset(null)
    assertThat(lifecycle.cancel(null)).isSameAs(cancelJob)

    assertThat(appliedWithoutRestart).isTrue()
    assertThat(calls).containsExactly("isModified", "apply", "reset", "cancel")
  }

  @Test
  fun `dispose hands operations to background before detaching UI and is idempotent`() {
    val calls = mutableListOf<String>()
    val lifecycle = createLifecycle(calls, installationMovedToBackground = true)

    assertThat(lifecycle.disposeUi()).isTrue()
    assertThat(lifecycle.disposeUi()).isTrue()

    assertThat(calls).containsExactly(
      "toBackground",
      "detachTopController",
      "detachOperationUi",
      "detachDetails",
      "closeRows",
      "clearCallbacks",
      "cancelUiWork",
    )
    assertThatThrownBy { lifecycle.reset(null) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Legacy plugin UI host is disposed")
  }

  @Test
  fun `top controller bridge stops retaining detached controller`() {
    val calls = mutableListOf<String>()
    val bridge = LegacyPluginTopControllerBridge()
    bridge.attach(RecordingTopController(calls))

    bridge.showProject(true)
    bridge.showProgress(true)
    bridge.setLeftComponent(null)
    bridge.detach()
    bridge.showProgress(false)

    assertThat(calls).containsExactly("project:true", "progress:true", "left")
  }

  @Test
  @Timeout(30)
  fun `operation UI bridge releases parents when detached`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val bridge = PluginOperationUiBridge()
      val handle = bridge.createHandle(JPanel())
      val operationUi = handle.captureContext()

      assertThat(operationUi.getParentComponent()).isNotNull()
      bridge.detach()

      assertThat(operationUi.getParentComponent()).isNull()
    }
  }

  @Test
  @Timeout(30)
  fun `details update keeps captured models after presenter detachment`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val plugin = plugin("Plugin")
      val update = plugin("Plugin update")
      val bridge = PluginOperationUiBridge()
      val handle = bridge.createHandle(JPanel())
      val operation = capturePluginDetailsUpdateOperation(plugin, update, handle, JButton())!!

      bridge.detach()

      assertThat(operation.plugin).isSameAs(plugin)
      assertThat(operation.updateDescriptor).isSameAs(update)
      assertThat(operation.operationUi.getParentComponent()).isNull()
    }
  }

  @Test
  @Timeout(30)
  fun `reset callback returns to the EDT`(): Unit = timeoutRunBlocking {
    val result = ResetPluginsStateResult(emptyMap(), emptyMap())
    val callbackInvoked = AtomicBoolean()

    withContext(Dispatchers.Default) {
      invokeResetSessionCallback(result) {
        ThreadingAssertions.assertEventDispatchThread()
        callbackInvoked.set(true)
      }
    }

    assertThat(callbackInvoked).isTrue()
  }

  @Test
  fun `session close waits for active logical operation`() {
    val sink = LegacyPluginUiHostEventSink()
    val closeCount = AtomicInteger()
    sink.setCloseSessionAction { closeCount.incrementAndGet() }
    val operationId = UUID.randomUUID()

    sink.onEvent(operationStarted(operationId))
    sink.requestSessionClose(installationMovedToBackground = true)
    assertThat(closeCount.get()).isZero()

    sink.onEvent(operationFinished(operationId))
    sink.requestSessionClose(installationMovedToBackground = false)
    assertThat(closeCount.get()).isOne()
  }

  @Test
  @Timeout(30)
  fun `session close waits for submitted operation launcher`(): Unit = timeoutRunBlocking {
    val sink = LegacyPluginUiHostEventSink()
    val sessionClosed = CompletableDeferred<Unit>()
    val finishOperation = CompletableDeferred<Unit>()
    sink.setCloseSessionAction { sessionClosed.complete(Unit) }
    val launcher = PluginOperationLauncher(
      this,
      sink::operationLaunchSubmitted,
      sink::operationLaunchCompleted,
    )

    launcher.launch(Dispatchers.Default) { finishOperation.await() }
    sink.requestSessionClose(installationMovedToBackground = false)

    assertThat(sessionClosed.isCompleted).isFalse()
    finishOperation.complete(Unit)
    sessionClosed.await()
  }

  @Test
  @Timeout(30)
  fun `known background operation closes session after launcher completion`(): Unit = timeoutRunBlocking {
    val sink = LegacyPluginUiHostEventSink()
    val sessionClosed = CompletableDeferred<Unit>()
    val finishOperation = CompletableDeferred<Unit>()
    sink.setCloseSessionAction { sessionClosed.complete(Unit) }
    val launcher = PluginOperationLauncher(
      this,
      sink::operationLaunchSubmitted,
      sink::operationLaunchCompleted,
    )

    launcher.launch(Dispatchers.Default) { finishOperation.await() }
    sink.requestSessionClose(installationMovedToBackground = true)

    assertThat(sessionClosed.isCompleted).isFalse()
    finishOperation.complete(Unit)
    sessionClosed.await()
  }

  @Test
  fun `unknown background operation conservatively preserves session`() {
    val sink = LegacyPluginUiHostEventSink()
    val closeCount = AtomicInteger()
    sink.setCloseSessionAction { closeCount.incrementAndGet() }

    sink.requestSessionClose(installationMovedToBackground = true)
    sink.onEvent(PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.INSTALL, emptySet()))

    assertThat(closeCount.get()).isZero()
  }

  @Test
  @Timeout(30)
  fun `events published before collection remain available`(): Unit = timeoutRunBlocking {
    val sink = LegacyPluginUiHostEventSink()
    val event = PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.INSTALL, emptySet())

    sink.onEvent(event)

    assertThat(sink.events.first()).isEqualTo(event)
  }

  @Test
  @Timeout(30)
  fun `apply bridge reports asynchronous failures`(): Unit = timeoutRunBlocking {
    val failure = IllegalStateException("apply failed")
    val reportedFailure = CompletableDeferred<Throwable>()
    val bridge = PluginApplyCallbackBridge(
      operationScope = this,
      apply = { throw failure },
      callbackContext = { Dispatchers.Unconfined },
    )

    bridge.submit(
      parentComponent = null,
      successCallback = Consumer { throw AssertionError("Apply unexpectedly succeeded") },
      failureCallback = Consumer(reportedFailure::complete),
    )

    assertThat(reportedFailure.await()).isSameAs(failure)
  }

  @Test
  @Timeout(30)
  fun `apply bridge reports cancellation before propagating it`(): Unit = timeoutRunBlocking {
    val operationStarted = CompletableDeferred<Unit>()
    val reportedFailure = CompletableDeferred<Throwable>()
    val bridge = PluginApplyCallbackBridge(
      operationScope = this,
      apply = {
        operationStarted.complete(Unit)
        awaitCancellation()
      },
      callbackContext = { Dispatchers.Unconfined },
    )
    val job = bridge.submit(
      parentComponent = null,
      successCallback = Consumer { throw AssertionError("Apply unexpectedly succeeded") },
      failureCallback = Consumer(reportedFailure::complete),
    )

    operationStarted.await()
    job.cancelAndJoin()

    assertThat(reportedFailure.await()).isInstanceOf(CancellationException::class.java)
  }

  private fun createLifecycle(
    calls: MutableList<String>,
    installationMovedToBackground: Boolean = false,
    cancelJob: Job = CompletableDeferred(Unit),
  ): LegacyPluginUiHostLifecycle {
    return LegacyPluginUiHostLifecycle(
      isModified = {
        calls.add("isModified")
        true
      },
      apply = { _, callback, _ ->
        calls.add("apply")
        callback.accept(true)
      },
      reset = { calls.add("reset") },
      cancel = {
        calls.add("cancel")
        cancelJob
      },
      toBackground = {
        calls.add("toBackground")
        installationMovedToBackground
      },
      detachTopController = { calls.add("detachTopController") },
      detachOperationUi = { calls.add("detachOperationUi") },
      detachDetails = { calls.add("detachDetails") },
      closeRows = { calls.add("closeRows") },
      clearCallbacks = { calls.add("clearCallbacks") },
      cancelUiWork = { calls.add("cancelUiWork") },
    )
  }

  private fun plugin(name: String): PluginUiModel {
    return PluginNodeModelBuilderFactory.createBuilder(PluginId.getId("plugin.id")).setName(name).build()
  }

  private fun operationStarted(operationId: UUID): PluginModelEvent.OperationStarted {
    return PluginModelEvent.OperationStarted(
      sessionId = "session",
      operationId = operationId,
      displayPluginId = PluginId.getId("plugin.id"),
      presentationModel = PluginNodeModelBuilderFactory.createBuilder(PluginId.getId("plugin.id")).setName("Plugin").build(),
      target = PluginSource.LOCAL,
      kind = PluginOperationKind.INSTALL,
    )
  }

  private fun operationFinished(operationId: UUID): PluginModelEvent.OperationFinished {
    return PluginModelEvent.OperationFinished(
      sessionId = "session",
      operationId = operationId,
      displayPluginId = PluginId.getId("plugin.id"),
      target = PluginSource.LOCAL,
      kind = PluginOperationKind.INSTALL,
      result = PluginOperationTerminalResult.SUCCEEDED,
    )
  }

  private class RecordingTopController(private val calls: MutableList<String>) : Configurable.TopComponentController {
    override fun setLeftComponent(component: Component?) {
      calls.add("left")
    }

    override fun showProgress(start: Boolean) {
      calls.add("progress:$start")
    }

    override fun showProject(hasProject: Boolean) {
      calls.add("project:$hasProject")
    }
  }
}
