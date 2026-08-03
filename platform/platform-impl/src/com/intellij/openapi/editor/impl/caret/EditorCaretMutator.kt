// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.caret.blink.CaretBlinkMachine
import com.intellij.openapi.editor.impl.caret.model.CaretClock
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.caret.model.TICK_MS
import com.intellij.openapi.editor.impl.caret.motion.CaretMotionMachine
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

@Service(Service.Level.APP)
internal class EditorCaretMutatorFactory(private val scope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): EditorCaretMutatorFactory = service()

    @JvmStatic
    fun createMutator(host: CaretAnimationHost, debugName: String): EditorCaretMutator =
      getInstance().doCreateMutator(host, debugName)
  }

  private fun doCreateMutator(host: CaretAnimationHost, debugName: String) =
    EditorCaretMutator(scope.childScope("Caret animation for $debugName"), host)
}

internal class EditorCaretMutator internal constructor(
  private val coroutineScope: CoroutineScope,
  private val host: CaretAnimationHost,
) : Disposable {
  private val motion = CaretMotionMachine()
  private val blink = CaretBlinkMachine()
  private val wakeUps = Channel<Unit>(Channel.CONFLATED)

  private var settings = host.conditions.settings()
  private var lastFrameAt = CaretClock.monotonicMillis()
  private var loop: Job? = null

  @RequiresEdt
  fun caretMoved() {
    val tick = tick(TICK_MS.toDouble())
    motion.retarget(host.geometry.placements(), tick)
    if (motion.isSettled) showNow(tick) else wakeUp()
  }

  @RequiresEdt
  fun caretMovedImmediately() {
    val tick = tick(TICK_MS.toDouble())
    motion.snapTo(host.geometry.placements(), tick)
    showNow(tick)
  }

  @RequiresEdt
  fun bulkUpdateStarting() {
    val tick = tick(TICK_MS.toDouble())
    motion.settle(tick)
    showNow(tick)
  }

  fun setBlinking(visible: Boolean) = onEdt {
    if (visible) blink.start() else blink.stop()
    wakeUp()
  }

  fun reinitSettings() = onEdt {
    settings = host.conditions.settings()
    blink.restart()
    wakeUp()
  }

  override fun dispose() {
    coroutineScope.cancel()
  }

  @RequiresEdt
  private fun showNow(tick: CaretTick) {
    lastFrameAt = tick.now
    val frame = nextFrame(tick, prefetching = false)
    frame.applyTo(host)
    if (frame.nextDelay != Duration.INFINITE) ensureLoop()
  }

  private fun wakeUp() {
    ensureLoop()
    wakeUps.trySend(Unit)
  }

  private fun ensureLoop() {
    loop = loop?.takeIf { it.isActive } ?: launchLoop()
  }

  private fun launchLoop(): Job =
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      runCatching { run() }.getOrHandleException { LOG.error("Caret animation failed", it) }
    }

  private suspend fun run() {
    while (currentCoroutineContext().isActive) {
      val now = CaretClock.monotonicMillis()
      val frameMs = (now - lastFrameAt).coerceAtLeast(TICK_MS.toLong()).toDouble()
      lastFrameAt = now

      val frame = nextFrame(tick(frameMs), prefetching = true)
      frame.applyTo(host)

      if (frame.nextDelay == Duration.INFINITE) break
      withTimeoutOrNull(frame.nextDelay) { wakeUps.receive() }
    }
  }

  private fun nextFrame(tick: CaretTick, prefetching: Boolean): CaretFrame = when {
    host.conditions.isFrozen() -> CaretFrame.IDLE
    else -> CaretFrame(motion.advance(tick, prefetching), blink.advance(tick, prefetching))
  }

  private fun tick(frameMs: Double): CaretTick = CaretTick(
    now = CaretClock.monotonicMillis(),
    frameMs = frameMs,
    settings = settings,
    isCaretShown = host.conditions.isCaretShown(),
    quietMs = host.conditions.millisSinceActivity(),
  )

  private inline fun onEdt(crossinline block: () -> Unit) {
    if (EDT.isCurrentThreadEdt()) {
      block()
    } else {
      coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
        block()
      }
    }
  }
}

private val LOG = logger<EditorCaretMutator>()
