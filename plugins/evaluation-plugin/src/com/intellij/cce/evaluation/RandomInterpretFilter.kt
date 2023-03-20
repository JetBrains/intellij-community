package com.intellij.cce.evaluation

import com.intellij.cce.interpreter.InterpretFilter
import kotlin.random.Random

class RandomInterpretFilter(
  private val completeTokenProbability: Double,
  private val completeTokenSeed: Long?) : InterpretFilter {

  private val random = if (completeTokenSeed != null) Random(completeTokenSeed) else Random.Default

  override fun shouldCompleteToken(): Boolean = random.nextFloat() < completeTokenProbability
}