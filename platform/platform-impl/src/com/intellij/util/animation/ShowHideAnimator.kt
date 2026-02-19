// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.animation

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.DoubleConsumer
import kotlin.math.roundToInt

@ApiStatus.Internal
open class ShowHideAnimator(easing: Easing, private val consumer: DoubleConsumer) {
  private val animator = JBAnimator()
  private val atomicVisible = AtomicBoolean()
  private val statefulEasing = easing.stateful()

  val disposable: Disposable get() = animator

  constructor(consumer: DoubleConsumer) : this(Easing.LINEAR, consumer)

  /**
   * @param onCompletion Is called on animation completion or at the end of the call if no animation is needed
   */
  fun setVisible(visible: Boolean, onCompletion: (() -> Unit)? = null, updateVisibility: () -> Unit) {
    if (visible != atomicVisible.getAndSet(visible)) {
      val value = statefulEasing.value
      when {
        !visible && value > 0.0 -> {
          val animation = createHidingAnimation(value, updateVisibility)
          if (onCompletion != null) {
            animation.runWhenExpired(onCompletion)
          }
          animator.animate(animation)
        }
        visible && value < 1.0 -> {
          val animation = createShowingAnimation(value, updateVisibility)
          if (onCompletion != null) {
            animation.runWhenExpired(onCompletion)
          }
          animator.animate(animation)
        }
        else -> {
          animator.stop()
          updateVisibility()
          onCompletion?.invoke()
        }
      }
    }
    else {
      onCompletion?.invoke()
    }
  }

  fun setVisibleImmediately(visible: Boolean) {
    animator.stop()
    if (visible != atomicVisible.getAndSet(visible)) {
      consumer.accept(statefulEasing.calc(if (visible) 1.0 else 0.0))
    }
  }

  var showingDelay = Registry.intValue("ide.animation.showing.delay", 0)
  var showingDuration = Registry.intValue("ide.animation.showing.duration", 130)
  var hidingDelay = Registry.intValue("ide.animation.hiding.delay", 140)
  var hidingDuration = Registry.intValue("ide.animation.hiding.duration", 150)

  private fun createShowingAnimation(value: Double, updateVisibility: () -> Unit) = Animation(consumer).apply {
    if (value > 0.0) {
      duration = (showingDuration * (1 - value)).roundToInt()
      easing = statefulEasing.coerceIn(value, 1.0)
    }
    else {
      delay = showingDelay
      duration = showingDuration
      easing = statefulEasing
    }
  }.runWhenScheduled {
    if (atomicVisible.get()) { // Most likely not needed, just for consistency with hide.
      // In the worst case, we just avoid minor flickering here.
      updateVisibility()
    }
  }

  private fun createHidingAnimation(value: Double, updateVisibility: () -> Unit) = Animation(consumer).apply {
    if (value < 1.0) {
      duration = (hidingDuration * value).roundToInt()
      easing = statefulEasing.coerceIn(0.0, value).reverse()
    }
    else {
      delay = hidingDelay
      duration = hidingDuration
      easing = statefulEasing.reverse()
    }
  }.runWhenExpiredOrCancelled {
    if (!atomicVisible.get()) { // If the animation is canceled and the component was already made visible, we do NOT want to hide it again!
      updateVisibility()
    }
  }
}
