// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesConfigurator
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.function.Consumer

/**
 * An [GradleIssueChecker] handling Daemon JVM criteria exceptions like:
 * "Value 'invalid version' given for toolchainVersion is an invalid Java version"
 */
@Internal
class GradleInvalidDaemonJvmCriteriaIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = issueData.failure.rootCause
    if (rootCause.text.endsWith("toolchainVersion is an invalid Java version")) {
      return GradleInvalidDaemonJvmCriteriaBuildIssue(rootCause)
    }
    return null
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>,
  ): Boolean {
    return parentEventId == ":${DaemonJvmPropertiesConfigurator.TASK_NAME}"
  }
}

private class GradleInvalidDaemonJvmCriteriaBuildIssue(
  failure: GradleIssueFailure,
) : ConfigurableGradleBuildIssue() {
  init {
    setTitle(GradleBundle.message("gradle.build.issue.daemon.toolchain.invalid.criteria.title"))
    addDescription(failure.messageOrDescription ?: title)
    addOpenDaemonJvmSettingsQuickFix()
  }
}