// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.diagnostic

/** Counterpart of com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy */
enum class MetricsAggregation {
  EARLIEST,

  /** Usually used to collect gauges */
  LATEST,

  MINIMUM,
  MAXIMUM,
  SUM;
}