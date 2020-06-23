// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import kotlin.math.round

sealed class FilePredictionFeature {
  companion object {
    @JvmStatic
    fun binary(value: Boolean): FilePredictionFeature = if (value) BinaryValue.TRUE else BinaryValue.FALSE

    @JvmStatic
    fun numerical(value: Int): FilePredictionFeature = NumericalValue(value)

    @JvmStatic
    fun numerical(value: Double): FilePredictionFeature = DoubleValue(value)

    @JvmStatic
    fun categorical(value: String): FilePredictionFeature = CategoricalValue(value)
  }

  abstract val value: Any

  abstract fun addToEventData(key: String, data: FeatureUsageData)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FilePredictionFeature

    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int = value.hashCode()

  override fun toString(): String = value.toString()

  private class BinaryValue private constructor(override val value: Boolean) : FilePredictionFeature() {
    companion object {
      val TRUE = BinaryValue(true)
      val FALSE = BinaryValue(false)
    }

    override fun addToEventData(key: String, data: FeatureUsageData) {
      data.addData(key, value)
    }
  }

  private class NumericalValue(override val value: Int) : FilePredictionFeature() {
    override fun addToEventData(key: String, data: FeatureUsageData) {
      data.addData(key, value)
    }
  }

  private class DoubleValue(override val value: Double) : FilePredictionFeature() {
    override fun addToEventData(key: String, data: FeatureUsageData) {
      data.addData(key, process(value))
    }

    private fun process(value: Double): Double {
      if (!value.isFinite()) return -1.0
      return round(value * 100000) / 100000
    }
  }

  private class CategoricalValue(override val value: String) : FilePredictionFeature() {
    override fun addToEventData(key: String, data: FeatureUsageData) {
      data.addData(key, value)
    }
  }
}