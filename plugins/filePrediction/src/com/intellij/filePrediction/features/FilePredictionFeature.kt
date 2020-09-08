// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.FilePredictionEventFieldEncoder

sealed class FilePredictionFeature {
  companion object {
    @JvmStatic
    fun binary(value: Boolean): FilePredictionFeature = if (value) BinaryValue.TRUE else BinaryValue.FALSE

    @JvmStatic
    fun numerical(value: Int): FilePredictionFeature = NumericalValue(value)

    @JvmStatic
    fun numerical(value: Double): FilePredictionFeature = DoubleValue(value)

    @JvmStatic
    fun fileType(value: String): FilePredictionFeature = FileTypeValue(value)
  }

  abstract val value: Any

  abstract fun appendTo(result: StringBuilder)

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

    override fun appendTo(result: StringBuilder) {
      result.append(FilePredictionEventFieldEncoder.encodeBool(value))
    }
  }

  private class NumericalValue(override val value: Int) : FilePredictionFeature() {
    override fun appendTo(result: StringBuilder) {
      result.append(value)
    }
  }

  private class DoubleValue(override val value: Double) : FilePredictionFeature() {
    override fun appendTo(result: StringBuilder) {
      result.append(FilePredictionEventFieldEncoder.encodeDouble(value))
    }
  }

  private class FileTypeValue(override val value: String) : FilePredictionFeature() {
    override fun appendTo(result: StringBuilder) {
      result.append(FilePredictionEventFieldEncoder.encodeFileType(value))
    }
  }
}