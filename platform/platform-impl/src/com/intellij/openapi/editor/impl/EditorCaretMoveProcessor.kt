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
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.geom.Point2D
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

private data class CaretUpdate(
  val finalPos: Point2D,
  val finalLogicalPosition: LogicalPosition,
  val width: Float,
  val caret: Caret,
  val isRtl: Boolean,
)

private data class AnimationState(val startPos: Point2D, val startLogicalPosition: LogicalPosition?, val update: CaretUpdate)

@Service(Service.Level.APP)
internal class EditorCaretMoveProcessorFactory(private val scope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): EditorCaretMoveProcessorFactory = service()

    @JvmStatic
    fun createProcessor(editor: EditorImpl): EditorCaretMoveProcessor =
      getInstance().doCreateProcessor(editor)
  }

  private fun doCreateProcessor(editor: EditorImpl) =
    EditorCaretMoveProcessor(scope.childScope("Caret Move Scope for $editor"), editor)
}

private val TICK_MS = 4.milliseconds

internal class EditorCaretMoveProcessor(private val coroutineScope: CoroutineScope, private val editor: EditorImpl) {
  private val lastPosMap = mutableMapOf<Caret, Pair<Point2D, LogicalPosition?>>()
  private val cursor = editor.myCaretCursor
  private var animationScope: CoroutineScope? = null

  private val setPositionRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      setPositionRequests.collectLatest { _ ->
        coroutineScope {
          if (!editor.isDisposed) {
            animationScope = this
            runCatching {
              processRequest()
            }.getOrHandleException { e ->
              LOG.error("An exception occurred while setting caret positions", e)
            }
          }
        }
      }
    }
  }

  fun clear()  {
    runCatching { coroutineScope.cancel() }
  }

  fun setCursorPosition() {
    check(setPositionRequests.tryEmit(Unit))
  }

  private fun previousPositionRectangles(updates: List<CaretUpdate>) = updates.mapNotNull {
    val (pos, _) = lastPosMap[it.caret] ?: return@mapNotNull null

    EditorImpl.CaretRectangle(pos, it.width, it.caret, it.isRtl)
  }.toTypedArray()

  private suspend fun processRequest() {
    val animationDuration = Registry.intValue("editor.smooth.caret.duration")

    cursor.blinkOpacity = 1.0f
    cursor.startTime = System.currentTimeMillis() + animationDuration

    val updates = calculateUpdates()
    val animationStates = updates.map {
      val (lastPos, lastVisualPosition) = lastPosMap.getOrPut(it.caret) {
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

      val oldRects = previousPositionRectangles(updates)
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
        lastPosMap[update.caret] = Pair(
          interpolated,
          state.update.finalLogicalPosition.takeUnless { isInAnimation }
        )
        EditorImpl.CaretRectangle(interpolated, update.width, update.caret, update.isRtl)
      }.toTypedArray()

      cursor.setPositions(interpolatedRects)
      cursor.repaint(oldRects)
      cursor.repaint()

      if (allDone) {
        break
      }

      delay(TICK_MS)
    }
  }

  private fun calculateUpdates() = editor.caretModel.allCarets.map { caret ->
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

  /**
   * Set the cursor position immediately without animation. This does not go through the
   * coroutine-based logic which can delay the cursor position update. This is required for
   * the ImmediatePainterTest to work.
   */
  fun setCursorPositionImmediately() {
    animationScope?.cancel()
    animationScope = null

    val animationStates = calculateUpdates()
    val oldRects = previousPositionRectangles(animationStates)
    for (state in animationStates) {
      lastPosMap[state.caret] = state.finalPos to state.finalLogicalPosition
    }
    cursor.setPositions(animationStates.map { state ->
      EditorImpl.CaretRectangle(state.finalPos, state.width, state.caret, state.isRtl)
    }.toTypedArray())
    cursor.repaint(oldRects)
  }
}

private val LOG = logger<EditorCaretMoveProcessor>()

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
