// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.tests.benchmark.service.syncAction.impl

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchFailure
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.jetbrains.plugins.gradle.service.syncAction.impl.GradleSyncFailureHandler
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.CsvSource

@TestApplication
@PerformanceUnitTest
class GradleSyncFailureHandlerPerformanceTest {

  @PerformanceUnitTest
  @Nested
  @ParameterizedClass
  @CsvSource("1000, 20, 200")
  inner class CreateIssueFailure(
    private val failureCount: Int,
    private val causeCount: Int,
    private val stackTraceCount: Int,
  ) {

    private lateinit var handler: GradleSyncFailureHandler

    private val failures = buildList {
      repeat(failureCount) { index ->
        var cause = GradleModelFetchFailure("root cause message", stackTrace("root cause", stackTraceCount), emptyList())
        repeat(causeCount) { level ->
          cause = GradleModelFetchFailure("cause message $level", stackTrace("cause $level", stackTraceCount), listOf(cause))
        }
        add(GradleModelFetchFailure("failure message $index", stackTrace("failure $index", stackTraceCount), listOf(cause)))
      }
    }

    private fun stackTrace(message: String, lineCount: Int): String = buildString {
      appendLine("java.lang.RuntimeException: $message")
      repeat(lineCount) { line ->
        appendLine(" at org.example.gradle.GeneratedClass$line.generatedMethod$line(GeneratedClass$line.java:$line)")
      }
    }

    fun setup() {
      handler = GradleSyncFailureHandler()
    }

    fun attempt() {
      for (failure in failures) {
        handler.createIssueFailure(failure)
      }
    }

    @Test
    fun test() {
      Benchmark.newBenchmark("$failureCount x $causeCount x $stackTraceCount", ::attempt)
        .setup(::setup)
        .runAsStressTest()
        .start()
    }
  }
}
