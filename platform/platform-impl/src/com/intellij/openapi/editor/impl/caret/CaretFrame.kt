// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.impl.caret.blink.CaretBlinkFrame
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.caret.motion.CaretMotionFrame
import kotlin.time.Duration

@JvmInline
internal value class CaretCacheKey private constructor(private val contentHash: Int) {
  companion object {
    fun of(locations: List<CaretRectangle>): CaretCacheKey {
      var hash = locations.size
      for (location in locations) {
        hash = hash * 31 + location.x.hashCode()
        hash = hash * 31 + location.y.hashCode()
        hash = hash * 31 + location.width.hashCode()
      }
      return CaretCacheKey(hash)
    }
  }
}

internal data class CaretFrame(
  private val motion: CaretMotionFrame,
  private val blink: CaretBlinkFrame,
) {
  val nextDelay: Duration get() = minOf(motion.nextDelay, blink.nextDelay)

  fun applyTo(host: CaretAnimationHost) {
    val changed = motion.locations != null || motion.stale.isNotEmpty() || blink.opacity != null
    if (changed) {
      val presentation = host.presentation
      val previous = host.geometry.currentLocations()

      motion.locations?.let { presentation.showAt(it.toTypedArray()) }
      blink.opacity?.let(presentation::fadeTo)
      warmedLocations(host)?.let { host.cache.prefetch(CaretCacheKey.of(it), it) }

      if (motion.stale.isNotEmpty()) {
        presentation.repaint(motion.stale.toTypedArray())
      }
      if (motion.locations != null) {
        presentation.repaint(previous)
      }
      presentation.repaintCurrent()
    }
  }

  private fun warmedLocations(host: CaretAnimationHost): List<CaretRectangle>? =
    motion.prefetch ?: host.geometry.currentLocations().asList().takeIf { blink.wantsPrefetch && it.isNotEmpty() }

  companion object {
    val IDLE: CaretFrame = CaretFrame(CaretMotionFrame.IDLE, CaretBlinkFrame.DORMANT)
  }
}
