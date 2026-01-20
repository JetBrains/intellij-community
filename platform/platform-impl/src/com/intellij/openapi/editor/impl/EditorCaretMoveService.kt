// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.MathUtil.clamp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.geom.Point2D
import kotlin.math.*

private data class CaretUpdate(val finalPos: Point2D, val width: Float, val caret: Caret, val isRtl: Boolean)
private data class AnimationState(val startPos: Point2D, val update: CaretUpdate)

@Service(Service.Level.APP)
internal class EditorCaretMoveService(coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): EditorCaretMoveService = service()

    const val MILLIS_SECOND = 1000

    private fun calculateUpdates(editor: EditorImpl) = editor.caretModel.allCarets.map { caret ->
      val isRtl = caret.isAtRtlLocation()
      val caretPosition = caret.visualPosition
      val pos1: Point2D = editor.visualPositionToPoint2D(caretPosition.leanRight(!isRtl))
      val pos2: Point2D = editor.visualPositionToPoint2D(VisualPosition(caretPosition.line, max(0, caretPosition.column + (if (isRtl) -1 else 1)), isRtl))

      var width = abs(pos2.x - pos1.x).toFloat()
      if (!isRtl && editor.inlayModel.hasInlineElementAt(caretPosition)) {
        width = min(width, ceil(editor.view.plainSpaceWidth.toDouble()).toFloat())
      }

      CaretUpdate(pos1, width, caret, isRtl)
    }
  }

  /**
   * Set the cursor position immediately without animation. This does not go through the
   * coroutine-based logic which can delay the cursor position update. This is required for
   * the ImmediatePainterTest to work.
   */
  fun setCursorPositionImmediately(editor: EditorImpl) {
    editor.pauseBlinking()

    val animationStates = calculateUpdates(editor)
    for (state in animationStates) {
      editor.lastPosMap[state.caret] = state.finalPos
    }
    editor.myCaretCursor.setPositions(animationStates.map { state ->
      EditorImpl.CaretRectangle(state.finalPos, state.width, state.caret, state.isRtl, 1.0f)
    }.toTypedArray())

    editor.resumeBlinking()
  }

  // Replaying 128 requests is probably way too much, actually 2 should be enough. It shouldn't break
  // anything, though, since most of the time this would not contain more than 2 elements,
  // one for the main editor and one for the lite editor that can sometimes be opened on top
  private val setPositionRequests = MutableSharedFlow<EditorImpl?>(replay = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      setPositionRequests.collect { editor ->
        if (editor != null && !editor.isDisposed) {
          editor.caretAnimationJob?.cancel()
          editor.caretAnimationJob = launch {
            runCatching {
              processRequest(editor)
            }.getOrHandleException { e ->
              LOG.error("An exception occurred while setting caret positions", e)
            }
          }
        }
      }
    }
  }

  fun setCursorPosition(editor: EditorImpl) {
    check(setPositionRequests.tryEmit(editor))
  }

  private suspend fun processRequest(editor: EditorImpl) {
    val cursor = editor.myCaretCursor

    editor.pauseBlinking()
    cursor.blinkOpacity = 1.0f

    val refreshRate = clamp(
      editor.component.graphicsConfiguration?.device?.displayMode?.refreshRate ?: 120,
      60, 360)

    val step = MILLIS_SECOND / (2 * refreshRate)

    val animationStates = calculateUpdates(editor).map {
      val lastPos = editor.lastPosMap.getOrPut(it.caret) { it.finalPos }
      AnimationState(lastPos, it)
    }

    val animationDuration = Registry.doubleValue("editor.smooth.caret.duration")
    val enableHiding = Registry.`is`("editor.smooth.caret.hide.animation")
    val stateDurations = animationStates.associateWith { state ->
      val dx = state.update.finalPos.x - state.startPos.x
      val dy = state.update.finalPos.y - state.startPos.y

      enableHiding && dx * dx + dy * dy >= 400
    }

    val startingAnimationElapsed = editor.caretAnimationElapsed
    val easing = CaretEasing.fromRegistry(startingAnimationElapsed)
    val startTime = System.currentTimeMillis()
    while (true) {
      val now = System.currentTimeMillis()
      val elapsed = now - startTime

      val t = min(elapsed / animationDuration, 1.0)
      editor.caretAnimationElapsed += t * (1.0 - startingAnimationElapsed)

      var allDone = true

      val interpolatedRects = animationStates.map { state ->
        val update = state.update
        val (startPos, finalPos) = Pair(state.startPos, update.finalPos)

        val shouldBlink = stateDurations[state]!!
        if (t < 1) allDone = false

        val ease = easing.apply(t)
        val x = startPos.x + (finalPos.x - startPos.x) * ease
        val y = startPos.y + (finalPos.y - startPos.y) * ease
        val opacity = when {
          !shouldBlink || t >= 1 -> 1.0
          ease < 0.2 -> 1.0 - (ease * 5)
          ease > 0.8 && ease < 1.0 -> (ease - 0.8) * 5
          else -> 0.0
        }

        val interpolated = Point2D.Double(if (t >= 1) finalPos.x else x, if (t >= 1) finalPos.y else y)
        editor.lastPosMap[update.caret] = interpolated
        EditorImpl.CaretRectangle(interpolated, update.width, update.caret, update.isRtl, opacity.toFloat())
      }.toTypedArray()

      cursor.repaint()
      cursor.setPositions(interpolatedRects)
      cursor.repaint()

      if (allDone) {
        break
      }

      delay(step.toLong())
    }

    editor.caretAnimationElapsed = 0.0
    editor.resumeBlinking()
  }
}

private val LOG = logger<EditorCaretMoveService>()

private enum class CaretEasingType {
  Ninja,
  Parametric,
  Ease;
}

private class CaretEasing(val type: CaretEasingType, val adjustP: Double) {

  fun apply(t: Double): Double {
    return when (this.type) {
      CaretEasingType.Ninja -> {
        val u = cbrt(t)
        3 * u - 3 * u.pow(2) + t
      }
      CaretEasingType.Parametric -> {
        // Parametric ease-out: f(t) = 1 - (1 - t^a)^b
        // where a = 1/(k×1.5+0.2), b = k×1.5+0.2, k ∈ [1.1, 1.85]
        // Note: k = 1.85 approximates the Ninja curve behavior
        val k = Registry.doubleValue("editor.smooth.caret.curve.parametric.factor", 1.85)
        val a = 1.0 / (k * 1.5 + 0.2)
        val b = k * 1.5 + 0.2
        1.0 - (1.0 - t.pow(a)).pow(b)
      }
      CaretEasingType.Ease -> {
        // Horner form of rounded Hermite + α, β approx of cubic-bezier(0.25,0.1,0.25,1.0); monotone on [0,1], max dev ≈ 0.0176.
        val f = { t: Double -> t * ((((-5.4 * t + 17.6) * t - 20.6) * t + 9.0) * t + 0.4) }

        ((f(adjustP + (1 - adjustP) * t) - f(adjustP)) / (f(1.0) - f(adjustP)))
      }
    }
  }

  companion object {
    fun fromRegistry(elapsed: Double): CaretEasing {
      val type = Registry.get("editor.smooth.caret.curve").selectedOption?.let { CaretEasingType.valueOf(it) } ?: CaretEasingType.Ninja

      return CaretEasing(type, elapsed)
    }
  }
}
