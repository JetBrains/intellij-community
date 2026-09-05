// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.caret.model.CaretClock
import com.intellij.openapi.editor.impl.caret.model.CaretCursorSnapshot
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.view.animation.AnimationClock
import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.TimeSource

//   caretMoved() |> state.updateAndGet { retarget } |> ensureLoop()
//                                                        v
//         loop() |> advanceStep(prefetching = true) |> current.advance(tick) |> apply()
//           ^                                                                    |> editor.prefetchCaretFrames()
//           |                                                                    |> editor.view.repaintCarets()
//           +--- wait for nextDelay, or for a state change, while !step.isIdle
//
//   A settled move has no motion left. It skips the loop:
//   caretMoved() |> state.updateAndGet { retarget } |> advanceNow(tick) |> advanceStep(prefetching = false) |> apply() |> editor.view.repaintCarets()
@Service(Service.Level.APP)
internal class EditorCaretMutatorFactory(private val scope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun createMutator(editor: EditorImpl): EditorCaretMutator = service<EditorCaretMutatorFactory>().create(editor)
  }

  private val frameDispatcher: CoroutineContext = Dispatchers.Default.limitedParallelism(1, "EditorCaretAnimation")

  private fun create(editor: EditorImpl) = EditorCaretMutator(scope.childScope("Caret animation for $editor"), editor, frameDispatcher)
}

internal class EditorCaretMutator internal constructor(
  private val coroutineScope: CoroutineScope,
  private val editor: EditorImpl,
  private val frameContext: CoroutineContext,
) : Disposable {
  private val state = MutableStateFlow(CaretAnimationState.initial())

  private val settings = AtomicReference(editor.caretAnimationSettings())
  private val disposed = AtomicBoolean(false)

  init {
    editor.document.addDocumentListener(object : DocumentListener {
      override fun bulkUpdateStarting(document: Document) {
        val tick = tick(CaretClock.MOVEMENT_FRAME)

        state.update { it.settle(tick) }
        advanceNow(tick)
      }
    }, this)
  }

  /// MARK: editor requests

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun caretMoved() {
    val (placements, isCaretShown) = editor.caretPlacements() to editor.isCaretShown(snapshot())
    val repaintMetrics = editor.view.caretRepaintMetrics
    val tick = tick(CaretClock.MOVEMENT_FRAME)

    val next = state.updateAndGet { it.retarget(placements, tick, isCaretShown, repaintMetrics) }
    when (next.isMotionSettled) {
      true -> advanceNow(tick)
      false -> ensureLoop()
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun caretMovedImmediately() {
    val placements = editor.caretPlacements()
    val repaintMetrics = editor.view.caretRepaintMetrics
    val tick = tick(CaretClock.MOVEMENT_FRAME)

    state.updateAndGet { it.snapTo(placements, tick, repaintMetrics) }
    advanceNow(tick)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun reinitSettings() {
    settings.set(editor.caretAnimationSettings())
    state.updateAndGet { it.restartBlink() }
    ensureLoop()
  }

  /// MARK: animation loop

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun advanceNow(tick: CaretTick) {
    val step = advanceStep(prefetching = false, stopWhenIdle = false) { tick }

    if (!step.isIdle) ensureLoop()
  }

  private fun ensureLoop() {
    if (disposed.get()) return

    if (state.getAndUpdate { it.withRunning(true) }.isRunning) return
    coroutineScope.launch(frameContext) {
      runCatching { loop() }.getOrHandleException { LOG.error("Caret animation failed", it) }
    }
  }

  private suspend fun loop() {
    var isRunning = true
    try {
      while (currentCoroutineContext().isActive) {
        val step = advanceStep(prefetching = true, stopWhenIdle = true) {
          val now = AnimationClock.markAnimationNow()
          tick((now - lastFrameAt).coerceAtLeast(CaretClock.MOVEMENT_FRAME), now)
        }
        isRunning = !step.isIdle
        if (!isRunning) break
        withTimeoutOrNull(step.nextDelay) { state.first { it.version != step.version } }
      }
    }
    finally {
      if (isRunning) state.update { it.withRunning(false) }
    }
  }

  private inline fun advanceStep(
    prefetching: Boolean,
    stopWhenIdle: Boolean,
    tick: CaretAnimationState.() -> CaretTick,
  ): CaretStep {
    while (true) {
      val current = state.value
      val tick = current.tick()
      val (advanced, step) = when {
        disposed.get() || editor.document.isInBulkUpdate -> current.freeze(tick.now)
        else -> current.advance(tick, prefetching)
      }
      val next = if (stopWhenIdle && step.isIdle) advanced.withRunning(false) else advanced
      if (state.compareAndSet(current, next)) {
        propagateStep(current, next, step)
        return step
      }
    }
  }

  /// MARK: rendering

  private fun propagateStep(previousState: CaretAnimationState, nextState: CaretAnimationState, step: CaretStep) {
    if (disposed.get()) return

    step.prefetch?.let { editor.prefetchCaretFrames(it) }

    if (step.moved) repaint(previousState.snapshot)
    if (step.moved || step.opacityChanged) repaint(nextState.snapshot)
  }

  private fun repaint(snapshot: CaretCursorSnapshot) {
    if (!disposed.get()) editor.view.repaintCarets(snapshot.locations, snapshot.repaintMetrics)
  }

  /// MARK: frame timing

  private fun tick(frameDuration: Duration, now: AnimationTimeMark = AnimationClock.markAnimationNow()): CaretTick = CaretTick(
    now = now,
    frameDuration = frameDuration,
    settings = settings.get(),
    elapsedQuietTime = snapshot().startTime?.let { now - it } ?: Duration.INFINITE,
  )

  /// MARK: external state transitions

  fun snapshot(): CaretCursorSnapshot = state.value.snapshot

  fun setEnabled(enabled: Boolean): Boolean {
    val previous = state.getAndUpdate { it.withEnabled(enabled) }.snapshot
    if (previous.isEnabled != enabled) repaint(previous)
    return previous.isEnabled
  }

  fun setVisible(visible: Boolean): Boolean {
    val previous = state.getAndUpdate { it.withShown(visible, AnimationClock.markAnimationNow()) }
    if (previous.snapshot.isShown != visible || visible && previous.snapshot.blinkOpacity != 1.0f) repaint(previous.snapshot)
    ensureLoop()
    return previous.snapshot.isShown
  }

  fun setBlinking(blinking: Boolean) {
    state.updateAndGet { if (blinking) it.startBlink() else it.stopBlink() }
    ensureLoop()
  }

  fun setFullOpacity() {
    state.updateAndGet(CaretAnimationState::setFullOpacity)
  }

  fun setStartTime() {
    state.updateAndGet { it.withStartTime(AnimationClock.markAnimationNow()) }
  }

  /// MARK: disposal

  override fun dispose() {
    disposed.set(true)
    state.updateAndGet { it.withRunning(false) }
    coroutineScope.cancel()
  }
}

private val LOG = logger<EditorCaretMutator>()
