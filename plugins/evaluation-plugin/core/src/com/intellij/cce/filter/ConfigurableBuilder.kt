package com.intellij.cce.filter

interface ConfigurableBuilder<T> {
  fun build(filterId: String): EvaluationFilterConfiguration.Configurable<T>
}