package com.intellij.cce.actions

import kotlin.random.Random

class UserEmulator private constructor(private val settings: Settings) {
  companion object {
    private val defaultSettings = Settings(
      listOf(0.0, 0.8, 0.2),
      listOf(
        listOf(0.5, 0.5, 0.6, 0.6, 0.7),
        listOf(0.2, 0.2, 0.3),
        listOf(0.15, 0.2, 0.3),
        listOf(0.15, 0.15, 0.2),
        listOf(0.15, 0.15, 0.2),
        listOf(0.1)
      )
    )

    fun create(settings: Settings?): UserEmulator =
      if (settings != null) UserEmulator(settings) else UserEmulator(defaultSettings)
  }

  data class Settings(
    val firstPrefixLen: List<Double>,
    val selectElement: List<List<Double>>
  )

  fun firstPrefixLen(): Int = selectWithProbability(settings.firstPrefixLen)

  fun selectElement(position: Int, order: Int): Boolean = isSuccess(settings.selectElement.getAt(position).getAt(order))

  private fun isSuccess(p: Double): Boolean = Random.Default.nextDouble() < p

  private fun selectWithProbability(probs: List<Double>): Int {
    val point = Random.Default.nextDouble()
    var cur = 0.0
    for ((i, p) in probs.withIndex()) {
      cur += p
      if (point < cur) return i
    }
    return probs.lastIndex
  }

  private fun <T> List<T>.getAt(i: Int): T = if (this.size <= i) this.last() else this[i]
}
