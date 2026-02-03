// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui

import com.intellij.execution.TestStateStorage
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMStacktraceParser
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbService.Companion.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.Date

internal object SMTestRunnerTestStateWriter {
  @RequiresBackgroundThread
  @JvmStatic
  fun writeState(project: Project, proxy: SMTestProxy, url: String, configuration: RunConfiguration?, consoleProperties: TestConsoleProperties) {
    val configurationName: String? = configuration?.getName()
    val isConfigurationDumbAware = configuration != null && configuration.getType().isDumbAware
    val isSMTestLocatorDumbAware = isDumbAware(proxy.locator)
    if (isConfigurationDumbAware && isSMTestLocatorDumbAware) {
      runBlockingMaybeCancellable {
        writeTestState(project, consoleProperties, proxy, url, configurationName)
      }
    }
    else {
      if (isConfigurationDumbAware /*&& !isSMTestLocatorDumbAware*/) {
        thisLogger().warn("Configuration " + configuration.getType() +
                          " is dumb aware, but it's test locator " + proxy.locator + " is not. " +
                          "It leads to an hanging update task on finishing a test case in dumb mode.")
      }

      runBlockingMaybeCancellable {
        writeTestStateSmart(project, consoleProperties, proxy, url, configurationName)
      }
    }
  }

  private suspend fun writeTestState(project: Project, consoleProperties: TestConsoleProperties, proxy: SMTestProxy, url: String, configurationName: String?) {
    val info = readAction {
      if (project.isDisposed) return@readAction null

      getStackTraceParser(consoleProperties, proxy, url, project)
    } ?: return

    TestStateStorage.getInstance(project).writeState(url, TestStateStorage.Record(proxy.magnitude, Date(),
                                                                                  (configurationName?.hashCode() ?: 0).toLong(),
                                                                                  info.failedLine, info.failedMethodName,
                                                                                  info.errorMessage, info.topLocationLine))
  }

  private suspend fun writeTestStateSmart(project: Project, consoleProperties: TestConsoleProperties, proxy: SMTestProxy, url: String, configurationName: String?) {
    val info = smartReadAction(project) {
      if (project.isDisposed) return@smartReadAction null

      getStackTraceParser(consoleProperties, proxy, url, project)
    } ?: return

    TestStateStorage.getInstance(project).writeState(url, TestStateStorage.Record(proxy.magnitude, Date(),
                                                                                  (configurationName?.hashCode() ?: 0).toLong(),
                                                                                  info.failedLine, info.failedMethodName,
                                                                                  info.errorMessage, info.topLocationLine))
  }

  @RequiresReadLock
  private fun getStackTraceParser(consoleProperties: TestConsoleProperties, proxy: SMTestProxy, url: String, project: Project): TestStackTraceParser {
    if (consoleProperties is SMStacktraceParser) {
      return (consoleProperties as SMStacktraceParser).getTestStackTraceParser(url, proxy, project)
    }
    else {
      return TestStackTraceParser(url, proxy.stacktrace, proxy.errorMessage, proxy.locator, project)
    }
  }
}
