// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.caret.model.CaretAnimationSettings
import com.intellij.openapi.editor.impl.caret.model.CaretCursor
import com.intellij.openapi.editor.impl.caret.model.CaretEasing
import com.intellij.openapi.editor.impl.caret.model.CaretFrameInterval
import com.intellij.openapi.editor.impl.caret.model.CaretTick
import com.intellij.openapi.editor.impl.view.animation.AnimationClock
import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.DrawUtil.isSimplifiedUI
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlin.time.Duration.Companion.milliseconds

//   caretMoved() |> state.updateAndGet { retarget } |> ensureLoop()
//                                                        v
//         loop() |> advanceStep(prefetching = true) |> current.advance(tick) |> propagateStep()
//           ^                                                                    |> editor.prefetchCaretFrames()
//           |                                                                    |> editor.view.repaintCarets()
//           +--- wait for nextDelay, or for a state change, while !step.isIdle
//
//   A settled move has no motion left. It skips the loop:
//   caretMoved() |> state.updateAndGet { retarget } |> advanceNow(tick) |> advanceStep(prefetching = false)
//                                                                       |> propagateStep() |> repaintCarets()
internal class EditorCaretMutator internal constructor(
  private val editor: EditorImpl,
) : Disposable {
  private val coroutineScope: CoroutineScope = editor.coroutineScope
  private val state = MutableStateFlow(CaretAnimationState.initial())
  private val settings = AtomicReference(editor.caretAnimationSettings())
  private val updateCursor = AtomicBoolean(false)
  private val mouseIsInDrag = AtomicBoolean(false)
  private val gainedFocus = AtomicBoolean(false)
  private val disposed = AtomicBoolean(false)

  init {
    editor.document.addDocumentListener(BulkUpdateListener(), this)
  }

  /**
   * Adopts the settings and the caret measurements of the editor. The view has to reinit its own settings first,
   * because it measures the caret from a font cache that only its own reinit drops.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun reinitSettings() {
    settings.set(editor.caretAnimationSettings())
    val repaintMetrics = editor.view.caretRepaintMetrics
    state.update {
      it.restartBlink().withRepaintMetrics(repaintMetrics)
    }
    ensureLoop()
  }

  fun caretCursor(): CaretCursor {
    return state.value.caretCursor()
  }

  fun setEnabled(enabled: Boolean): Boolean {
    val previous: CaretCursor = state.getAndUpdate {
      it.withEnabled(enabled)
    }.caretCursor()
    if (previous.isEnabled() != enabled) {
      repaint(previous)
    }
    return previous.isEnabled()
  }

  fun setVisible(visible: Boolean): Boolean {
    if (visible) {
      gainedFocus.set(true)
    }
    val previousState = state.getAndUpdate {
      it.withShown(visible, AnimationClock.markAnimationNow())
    }
    val previous = previousState.caretCursor()
    val visibilityChanged = previous.isShown() != visible
    val becomesFullyOpaque = visible && !previous.isFullyOpaque()
    if (visibilityChanged || becomesFullyOpaque) {
      repaint(previous)
    }
    ensureLoop()
    return previous.isShown()
  }

  fun setBlinking(blinking: Boolean) {
    state.updateAndGet {
      if (blinking) {
        it.startBlink()
      } else {
        it.stopBlink()
      }
    }
    ensureLoop()
  }

  fun setMouseIsInDrag(value: Boolean) {
    mouseIsInDrag.set(value)
  }

  fun updateCaretCursor() {
    updateCursor.set(true)
    var repaintNeeded = false
    val newState = state.updateAndGet {
      if (it.caretCursor().isShown()) {
        // marks the caret as active now, which holds the blink off for one quiet period
        it.withActivityAt(AnimationClock.markAnimationNow())
      } else {
        // shows the caret at full opacity, without restarting the quiet period
        repaintNeeded = true
        it.showFullyOpaque()
      }
    }
    if (repaintNeeded) {
      repaint(newState.caretCursor())
    }
  }

  override fun dispose() {
    disposed.set(true)
    state.update {
      it.withRunning(false)
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun caretMoved() {
    if (!editor.isPurePaintingMode && updateCursor.getAndSet(false)) {
      if (shouldSetCursorPositionImmediately()) {
        caretMovedImmediately()
      } else {
        caretMovedAnimated()
      }
    }
  }

  private fun caretMovedAnimated() {
    val placements = editor.caretPlacements()
    val isCaretShown = editor.isCaretShown(caretCursor())
    val repaintMetrics = editor.view.caretRepaintMetrics
    val tick = tick(CaretFrameInterval.MOVEMENT)
    val next = state.updateAndGet {
      it.retarget(placements, tick, isCaretShown, repaintMetrics)
    }
    if (next.isMotionSettled()) {
      advanceNow(tick)
    } else {
      ensureLoop()
    }
  }

  private fun caretMovedImmediately() {
    val placements = editor.caretPlacements()
    val repaintMetrics = editor.view.caretRepaintMetrics
    val tick = tick(CaretFrameInterval.MOVEMENT)
    state.update {
      it.snapTo(placements, tick, repaintMetrics)
    }
    advanceNow(tick)
  }

  /**
   * Produces one step on the calling thread, so that a settled move appears without waiting for the loop.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun advanceNow(tick: CaretTick) {
    val step = advanceStep(prefetching = false, stopWhenIdle = false) {
      tick
    }
    if (!step.isIdle) {
      ensureLoop()
    }
  }

  private fun ensureLoop() {
    if (disposed.get()) {
      return
    }
    val wasRunning = state.getAndUpdate {
      it.withRunning(true)
    }.isRunning()
    if (wasRunning) {
      return
    }
    coroutineScope.launch(DISPATCHER) {
      runCatching {
        loop()
      }.getOrHandleException {
        LOG.error("Caret animation failed", it)
      }
    }
  }

  private suspend fun loop() {
    var isRunning = true
    try {
      while (currentCoroutineContext().isActive) {
        val step = advanceStep(prefetching = true, stopWhenIdle = true) {
          loopTick()
        }
        isRunning = !step.isIdle
        if (!isRunning) {
          break
        }
        // Wake up when the next frame is due, or as soon as somebody else changes the state.
        withTimeoutOrNull(step.nextDelay) {
          state.first {
            it.version() != step.version
          }
        }
      }
    }
    finally {
      if (isRunning) {
        state.update {
          it.withRunning(false)
        }
      }
    }
  }

  /**
   * Advances the state by one step and publishes the result. Retries until its own write wins, so that a concurrent
   * editor request is never lost.
   */
  private inline fun advanceStep(
    prefetching: Boolean,
    stopWhenIdle: Boolean,
    computeTick: CaretAnimationState.() -> CaretTick,
  ): CaretStep {
    while (true) {
      val current = state.value
      val tick = current.computeTick()
      val isFrozen = disposed.get() || editor.document.isInBulkUpdate
      val (advanced: CaretAnimationState, step: CaretStep) = if (isFrozen) {
        current.freeze(tick.now)
      } else {
        current.advance(tick, prefetching)
      }
      val shouldStopLoop = stopWhenIdle && step.isIdle
      val next: CaretAnimationState = if (shouldStopLoop) {
        advanced.withRunning(false)
      } else {
        advanced
      }
      if (state.compareAndSet(current, next)) {
        propagateStep(current, next, step)
        return step
      }
    }
  }

  private fun propagateStep(
    previousState: CaretAnimationState,
    nextState: CaretAnimationState,
    step: CaretStep,
  ) {
    if (disposed.get()) {
      return
    }
    val prefetch = step.prefetch
    if (prefetch != null) {
      editor.prefetchCaretFrames(prefetch)
    }
    // Erasing the previous locations also erases the carets that were removed, because the caretCursor still holds them.
    if (step.moved) {
      repaint(previousState.caretCursor())
    }
    val needsRedraw = step.moved || step.opacityChanged
    if (needsRedraw) {
      repaint(nextState.caretCursor())
    }
  }

  private fun repaint(caretCursor: CaretCursor) {
    if (disposed.get()) {
      return
    }
    editor.view.repaintCarets(caretCursor)
  }

  /**
   * The tick of a loop frame, whose duration is however long the previous frame actually took.
   */
  private fun CaretAnimationState.loopTick(): CaretTick {
    val now = AnimationClock.markAnimationNow()
    return tick(frameDurationAt(now), now)
  }

  private fun tick(
    frameDuration: Duration,
    now: AnimationTimeMark = AnimationClock.markAnimationNow(),
  ): CaretTick {
    return CaretTick(
      now = now,
      frameDuration = frameDuration,
      settings = settings.get(),
      elapsedQuietTime = caretCursor().quietTimeAt(now),
    )
  }

  private fun shouldSetCursorPositionImmediately(): Boolean {
    return gainedFocus.getAndSet(false) ||
           mouseIsInDrag.get() ||
           !editor.settings.isSmoothCaretMovement() ||
           shouldDisableAnimations()
  }

  private fun EditorImpl.caretAnimationSettings(): CaretAnimationSettings {
    val configuredBlinkPeriod = settings.caretBlinkPeriod.milliseconds
    val blinkPeriod = configuredBlinkPeriod.coerceAtLeast(MIN_BLINK_PERIOD)
    val blinksSmoothly = !shouldDisableAnimations() && settings.isSmoothCaretBlinking
    val configuredMoveDuration = Registry.intValue("editor.smooth.caret.duration", 120)
    val moveDurationMs = configuredMoveDuration.coerceAtLeast(1)
    return CaretAnimationSettings(
      blinkPeriod = blinkPeriod,
      isBlinking = settings.isBlinkCaret,
      blinksSmoothly = blinksSmoothly,
      easing = caretEasing(),
      moveDuration = moveDurationMs.milliseconds,
    )
  }

  private fun EditorImpl.caretEasing(): CaretEasing {
    return when (settings.caretEasing) {
      EditorSettings.CaretEasing.SNAPPY, null -> CaretEasing.SNAPPY
      EditorSettings.CaretEasing.GLIDING -> CaretEasing.GLIDING
    }
  }

  private fun shouldDisableAnimations(): Boolean {
    return isSimplifiedUI()
  }

  private inner class BulkUpdateListener : DocumentListener {
    override fun bulkUpdateStarting(document: Document) {
      val tick = tick(CaretFrameInterval.MOVEMENT)
      state.update {
        it.settle(tick)
      }
      advanceNow(tick)
    }
  }

  companion object {
    private val LOG = logger<EditorCaretMutator>()
    private val DISPATCHER: CoroutineContext = Dispatchers.Default.limitedParallelism(1, "EditorCaretMutator")

    /**
     * A blink faster than this is a strobe rather than a caret, so the configured period is floored here.
     */
    private val MIN_BLINK_PERIOD = 10.milliseconds
  }
}
