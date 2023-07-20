package com.intellij.cce.metric

class MetricInfo(
  name: String,
  val value: Double,
  val confidenceInterval: Pair<Double, Double>?,
  evaluationType: String,
  val valueType: MetricValueType,
  val showByDefault: Boolean) {

  val name = name.filter { it.isLetterOrDigit() }
  val evaluationType = evaluationType.filter { it.isLetterOrDigit() }
}
