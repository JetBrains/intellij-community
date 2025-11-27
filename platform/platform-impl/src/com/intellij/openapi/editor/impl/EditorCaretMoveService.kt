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
import com.intellij.util.MathUtil.clamp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.geom.Point2D
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

private data class CaretUpdate(val finalPos: Point2D, val width: Float, val caret: Caret, val isRtl: Boolean)
private data class AnimationState(val startPos: Point2D, val update: CaretUpdate)

@Service(Service.Level.APP)
internal class EditorCaretMoveService(coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): EditorCaretMoveService = service()

    const val BASE_SPEED: Float = 1200f
    const val ACCEL_FACTOR: Float = 0.66f
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
    val animationStates = calculateUpdates(editor)
    for (state in animationStates) {
      editor.lastPosMap[state.caret] = state.finalPos
    }
    editor.myCaretCursor.setPositions(animationStates.map { state ->
      EditorImpl.CaretRectangle(state.finalPos, state.width, state.caret, state.isRtl)
    }.toTypedArray())
  }

  // Replaying 128 requests is probably way too much, actually 2 should be enough. It shouldn't break
  // anything though, since most of the time this would not contain more than 2 elements
  // one for the main editor and one for the lite editor that can sometimes be opened on top
  private val setPositionRequests = MutableSharedFlow<EditorImpl?>(replay = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      // The best option here would be to use `collectLatest` for each editor, but since service returns
      // a singleton, we will use collect until a better solution is found. This is unfortunate, since in selection
      // using `collectLatest` achieves a better and smoother feel
      setPositionRequests.collect { editor ->
        if (editor != null) {
          runCatching {
            processRequest(editor)
          }.getOrHandleException { e ->
            LOG.error("An exception occurred while setting caret positions", e)
          }
        }
      }
    }
  }

  fun setCursorPosition(editor: EditorImpl) {
    check(setPositionRequests.tryEmit(editor))
  }

  private suspend fun processRequest(editor: EditorImpl) {
    val refreshRate = clamp(
      editor.component.graphicsConfiguration?.device?.displayMode?.refreshRate ?: 120,
      60, 360)

    val step = MILLIS_SECOND / (2 * refreshRate)

    val animationStates = calculateUpdates(editor).map {
      val lastPos = editor.lastPosMap.getOrPut(it.caret) { it.finalPos }
      AnimationState(lastPos, it)
    }

    val cursor = editor.myCaretCursor

    val stateDurations = animationStates.associateWith { state ->
      val dx = state.update.finalPos.x - state.startPos.x
      val dy = state.update.finalPos.y - state.startPos.y
      val distance = sqrt(dx * dx + dy * dy).toFloat()

      val effectiveSpeed = (BASE_SPEED * (1.0 + ACCEL_FACTOR * ln1p(distance / 5.0))).toFloat()

      val duration = clamp(distance / effectiveSpeed * MILLIS_SECOND, 40f, 80f)
      duration
    }

    val startTime = System.currentTimeMillis()
    while (true) {
      val now = System.currentTimeMillis()
      val elapsed = now - startTime
      var allDone = true

      val interpolatedRects = animationStates.map { state ->
        val update = state.update
        val (startPos, finalPos) = Pair(state.startPos, update.finalPos)

        val duration = stateDurations[state]!!
        val t = min(elapsed / duration, 1f)
        if (t < 1f) allDone = false

        val x = startPos.x + (finalPos.x - startPos.x) * t
        val y = startPos.y + (finalPos.y - startPos.y) * t

        val interpolated = Point2D.Double(if (t >= 1) finalPos.x else x, if (t >= 1) finalPos.y else y)
        editor.lastPosMap[update.caret] = interpolated
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
