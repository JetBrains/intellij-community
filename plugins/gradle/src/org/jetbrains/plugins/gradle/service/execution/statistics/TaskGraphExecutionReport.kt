// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.statistics

data class TaskGraphExecutionReport(
  val durationMs: Long,
  val executed: Int,
  val count: Int,
  val upToDateCount: Int,
  val fromCacheCount: Int
)