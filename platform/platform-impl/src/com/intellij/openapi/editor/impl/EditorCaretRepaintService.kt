// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.MathUtil.clamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.APP)
internal class EditorCaretRepaintService(coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): EditorCaretRepaintService = service()

    private const val MILLIS_SECOND = 1000
  }

  var editor: EditorImpl?
    get() = editorFlow.value
    set(value) {
      editorFlow.value = value
    }

  var isBlinking: Boolean
    get() = isBlinkingRef.get()
    set(value) {
      isBlinkingRef.set(value)
    }

  var blinkPeriod: Long
    get() = blinkPeriodRef.get()
    set(value) {
      blinkPeriodRef.set(value.coerceAtLeast(10L))
    }

  private val isBlinkingRef = AtomicBoolean(true)
  private val blinkPeriodRef = AtomicLong(500L)

  private val editorFlow = MutableStateFlow<EditorImpl?>(null)
  private val actionRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      editorFlow.combine(actionRequests, ) { editor, _ -> editor }.collectLatest { editor ->
        if (editor != null) {
          runCatching {
            blink(editor)
          }.getOrHandleException { e ->
            LOG.error("An exception occurred while blinking the active caret", e)
          }
        }
      }
    }
    restart()
  }

  fun restart() {
    check(actionRequests.tryEmit(Unit))
  }

  private suspend fun blink(editor: EditorImpl) {
    if (editor.settings.isSmoothCaretBlinking) {
      blinkSmooth(editor)
    } else {
      blinkNormal(editor)
    }
  }

  private suspend fun blinkNormal(editor: EditorImpl) {
    while (true) {
      delay(blinkPeriod)
      val cursor = editor.myCaretCursor
      cursor.blinkOpacity = 1.0f

      val time = System.currentTimeMillis() - cursor.startTime
      if (time > blinkPeriod) {
        var toRepaint = true
        if (isBlinking) {
          cursor.isActive = !cursor.isActive
        }
        else {
          toRepaint = !cursor.isActive
          cursor.isActive = true
        }
        if (toRepaint) {
          cursor.repaint()
        }
      }
    }
  }

  private suspend fun blinkSmooth(editor: EditorImpl) {
    val refreshRate = clamp(
      editor.component.graphicsConfiguration?.device?.displayMode?.refreshRate ?: 120,
      60, 360)

    val frameDuration = MILLIS_SECOND / (2 * refreshRate)

    val visualBlinkPeriod = 1.2 * blinkPeriod

    var phaseStart = System.currentTimeMillis()
    val phaseDuration = visualBlinkPeriod / 2.0
    val holdDuration = visualBlinkPeriod - phaseDuration

    var fadingOut = true

    while (true) {
      delay(frameDuration.toLong())

      val cursor = editor.myCaretCursor

      val now = System.currentTimeMillis()
      if (!isBlinking || now - cursor.startTime < blinkPeriod) {
        cursor.setFullOpacity()
        cursor.repaint()
        phaseStart = now
        fadingOut = true
        continue
      }

      val elapsed = now - phaseStart
      val opacity: Double = when {
        elapsed < phaseDuration -> {
          val t = (elapsed / phaseDuration).coerceIn(0.0, 1.0)
          if (fadingOut) 1.0 - easeInOutCubic(t) else easeOutQuint(t)
        }
        elapsed < phaseDuration + holdDuration -> {
          if (fadingOut) 0.0 else 1.0
        }
        else -> {
          fadingOut = !fadingOut
          phaseStart = now
          if (fadingOut) 1.0 else 0.0
        }
      }

      cursor.isActive = opacity >= 1e-2
      cursor.blinkOpacity = opacity.toFloat()
      cursor.repaint()
    }
  }
}

private val LOG = logger<EditorCaretRepaintService>()

private fun easeOutQuint(t: Double): Double {
  val inv = 1 - t
  return 1 - inv * inv * inv * inv * inv
}

private fun easeInOutCubic(t: Double): Double =
  if (t < 0.5) 4 * t * t * t
  else 1 - (-2 * t + 2).let { it * it * it } / 2
