package com.intellij.cce.evaluation

import com.intellij.cce.interpreter.InterpretFilter
import kotlin.random.Random

class RandomInterpretFilter(
  private val sessionProbability: Double,
  private val sessionSeed: Long?) : InterpretFilter {

  private val random = if (sessionSeed != null) Random(sessionSeed) else Random.Default

  override fun shouldCompleteToken(): Boolean = random.nextFloat() < sessionProbability
}