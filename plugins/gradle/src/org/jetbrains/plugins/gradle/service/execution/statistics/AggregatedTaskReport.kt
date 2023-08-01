// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.statistics

data class AggregatedTaskReport(
  val name: String,
  val plugin: String,
  var count: Int = 0,
  var sumDurationMs: Long = 0,
  var upToDateCount: Int = 0,
  var fromCacheCount: Int = 0,
  var upToDateDuration: Long = 0,
  var fromCacheDuration: Long = 0,
  var failed: Int = 0
)
