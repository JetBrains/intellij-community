// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesConfigurator
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer

@Internal
class GradleDaemonJvmCriteriaIssueChecker : GradleIssueChecker {

  private val checkers = listOf(
    GradleToolchainDownloadedMismatchCriteriaIssueChecker(),
    GradleUndefinedDaemonJvmCriteriaIssueChecker(),
    GradleInvalidDaemonJvmCriteriaIssueChecker(),
    GradleToolchainDownloadingErrorIssueChecker(),
    GradleToolchainInstalledNotFoundMatchingCriteriaIssueChecker(),
  )

  override fun check(issueData: GradleIssueData): BuildIssue? {
    return checkers.firstNotNullOfOrNull { it.check(issueData) }
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>,
  ): Boolean = parentEventId == ":${DaemonJvmPropertiesConfigurator.TASK_NAME}"
}
