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
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.MathUtil.clamp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.geom.Point2D
import kotlin.math.*

private data class CaretUpdate(
  val finalPos: Point2D,
  val finalLogicalPosition: LogicalPosition,
  val width: Float,
  val caret: Caret,
  val isRtl: Boolean,
)

private data class AnimationState(val startPos: Point2D, val startLogicalPosition: LogicalPosition?, val update: CaretUpdate)

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
      val pos2: Point2D =
        editor.visualPositionToPoint2D(VisualPosition(caretPosition.line, max(0, caretPosition.column + (if (isRtl) -1 else 1)), isRtl))

      var width = abs(pos2.x - pos1.x).toFloat()
      if (!isRtl && editor.inlayModel.hasInlineElementAt(caretPosition)) {
        width = min(width, ceil(editor.view.plainSpaceWidth.toDouble()).toFloat())
      }

      CaretUpdate(pos1, caret.logicalPosition, width, caret, isRtl)
    }
  }

  /**
   * Set the cursor position immediately without animation. This does not go through the
   * coroutine-based logic which can delay the cursor position update. This is required for
   * the ImmediatePainterTest to work.
   */
  fun setCursorPositionImmediately(editor: EditorImpl) {
    val animationStates = calculateUpdates(editor)
    for (state in animationStates) {
      editor.lastPosMap[state.caret] = state.finalPos to state.finalLogicalPosition
    }
    editor.myCaretCursor.setPositions(animationStates.map { state ->
      EditorImpl.CaretRectangle(state.finalPos, state.width, state.caret, state.isRtl)
    }.toTypedArray())
  }

  // Replaying 128 requests is probably way too much, actually 2 should be enough. It shouldn't break
  // anything, though, since most of the time this would not contain more than 2 elements,
  // one for the main editor and one for the lite editor that can sometimes be opened on top
  private val setPositionRequests = MutableSharedFlow<EditorImpl>(replay = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      setPositionRequests.collect { editor ->
        if (!editor.isDisposed) {
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
    val animationDuration = Registry.intValue("editor.smooth.caret.duration")

    cursor.blinkOpacity = 1.0f
    cursor.startTime = System.currentTimeMillis() + animationDuration

    val refreshRate = clamp(
      editor.component.graphicsConfiguration?.device?.displayMode?.refreshRate ?: 120,
      60, 360)

    val step = MILLIS_SECOND / (2 * refreshRate)

    val animationStates = calculateUpdates(editor).map {
      val (lastPos, lastVisualPosition) = editor.lastPosMap.getOrPut(it.caret) {
        it.finalPos to it.finalLogicalPosition
      }

      AnimationState(lastPos, lastVisualPosition, it)
    }

    val easing = CaretEasing.fromSettings(editor.settings)
    val startTime = System.currentTimeMillis()
    while (true) {
      val now = System.currentTimeMillis()
      val elapsed = now - startTime

      val t = min(1.0 * elapsed / animationDuration, 1.0)

      var allDone = true

      val interpolatedRects = animationStates.map { state ->
        val sameLogicalPosition = state.startLogicalPosition == state.update.finalLogicalPosition
        val isInAnimation = !sameLogicalPosition && t < 1

        val update = state.update
        val (startPos, finalPos) = Pair(state.startPos, update.finalPos)

        if (isInAnimation) allDone = false

        val ease = easing.apply(t)
        val x = startPos.x + (finalPos.x - startPos.x) * ease
        val y = startPos.y + (finalPos.y - startPos.y) * ease

        val interpolated = if (isInAnimation) Point2D.Double(x, y) else finalPos
        editor.lastPosMap[update.caret] = Pair(
          interpolated,
          state.update.finalLogicalPosition.takeUnless { isInAnimation }
        )
        EditorImpl.CaretRectangle(interpolated, update.width, update.caret, update.isRtl)
      }.toTypedArray()

      cursor.repaint()
      cursor.setPositions(interpolatedRects)
      cursor.repaint()

      if (allDone) {
        break
      }

      delay(step.toLong())
    }
  }
}

private val LOG = logger<EditorCaretMoveService>()

private enum class CaretEasingType {
  Ninja,
  Parametric,
  Ease;
}

private class CaretEasing(val type: CaretEasingType) {
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
        t * ((((-5.4 * t + 17.6) * t - 20.6) * t + 9.0) * t + 0.4)
      }
    }
  }

  companion object {
    fun fromSettings(settings: EditorSettings): CaretEasing {
      val type = when (settings.caretEasing) {
        EditorSettings.CaretEasing.NINJA -> CaretEasingType.Ninja
        EditorSettings.CaretEasing.EASE -> CaretEasingType.Ease
      }
      return CaretEasing(type)
    }
  }
}
