// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vcs

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.vcs.VcsBundle
import org.jetbrains.annotations.Nls

internal sealed class FailureDescription(val configName: String) {
  abstract val message: @Nls String

  class FailedToStart(configName: String, val configuration: RunnerAndConfigurationSettings?) : FailureDescription(configName) {
    override val message
      get() = VcsBundle.message("before.commit.run.configuration.failed.to.start", configName)
  }
  class TestsFailed(configName: String, val historyFileName: String, val testsResultText: String) : FailureDescription(configName) {
    override val message
      get() = VcsBundle.message("before.commit.run.configuration.tests.failed", configName, testsResultText)
  }
  class ProcessNonZeroExitCode(configName: String, val configuration: RunnerAndConfigurationSettings, val exitCode: Int) : FailureDescription(configName) {
    override val message
      get() = VcsBundle.message("before.commit.run.configuration.failed", configName, exitCode)
  }
}