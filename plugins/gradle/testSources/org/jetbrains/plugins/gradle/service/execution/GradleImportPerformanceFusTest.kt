// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.ExternalEventLogSettings
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.statistics.GradleExecutionPerformanceCollector
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleImportPerformanceFusTest : GradleImportingTestCase() {

  @Test
  @TargetVersions("5.1+")
  fun `test gradle import performance events collected by default`() {
    toggleFus(true)
    assertThat(collectGradleImportPerformanceEvents()).isNotEmpty()
  }

  @Test
  @TargetVersions("5.1+")
  fun `test gradle import performance events not collected if fus disabled by registry key`() {
    toggleFus(true)
    toggleRegistry(false)
    assertThat(collectGradleImportPerformanceEvents()).isEmpty()
  }

  @Test
  @TargetVersions("5.1+")
  fun `test gradle import performance events not collected if fus is off`() {
    toggleFus(false)
    toggleRegistry(true)
    assertThat(collectGradleImportPerformanceEvents()).isEmpty()
  }

  private fun toggleRegistry(enabled: Boolean) = Registry.get("gradle.import.performance.statistics").setValue(enabled, testRootDisposable)

  private fun toggleFus(enabled: Boolean) {
    val settings = object : ExternalEventLogSettings {
      override fun forceLoggingAlwaysEnabled(): Boolean = enabled
      override fun getExtraLogUploadHeaders(): Map<String, String> = emptyMap()
    }
    ExtensionTestUtil.maskExtensions(ExternalEventLogSettings.EP_NAME, listOf(settings), testRootDisposable)
  }

  private fun collectGradleImportPerformanceEvents(): List<LogEvent> = FUCollectorTestCase
    .collectLogEvents(getTestRootDisposable()) {
      importProject(script { it: TestGradleBuildScriptBuilder -> it.withJavaPlugin() })
    }
    .filter { it.group.id == GradleExecutionPerformanceCollector.GROUP.id }
}