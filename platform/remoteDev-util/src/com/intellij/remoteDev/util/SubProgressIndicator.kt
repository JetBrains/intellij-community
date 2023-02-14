package com.intellij.remoteDev.util

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min

@ApiStatus.Experimental
abstract class SubProgressIndicatorBase(parent: ProgressIndicator) : DelegatingProgressIndicator(parent) {
  override fun start() = Unit
  override fun stop() = Unit
}

@ApiStatus.Experimental
class SubProgressIndicator(parent: ProgressIndicator, private val parentFraction: Double) : SubProgressIndicatorBase(parent) {

  private val parentBaseFraction = parent.fraction
  private var subFraction = 0.0

  override fun getFraction(): Double {
    return subFraction
  }

  override fun setFraction(fraction: Double) {
    subFraction = fraction

    val actualFraction = min(parentBaseFraction + subFraction * parentFraction, 1.0)
    super.setFraction(actualFraction)
  }
}

@ApiStatus.Experimental
fun ProgressIndicator.createSubProgress(fraction: Double): ProgressIndicator {
  assert(fraction > 0 && fraction <= 1) { "Fraction should be in interval (0;1]" }
  return SubProgressIndicator(this, fraction)
}