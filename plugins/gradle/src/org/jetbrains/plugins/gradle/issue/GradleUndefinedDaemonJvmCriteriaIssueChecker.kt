// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.util.GradleBundle

/**
 * An [GradleIssueChecker] handling Daemon JVM criteria exception when isn't defined for the project
 */
@Internal
class GradleUndefinedDaemonJvmCriteriaIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = issueData.failure.rootCause
    val message = rootCause.messageOrDescription
    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    if (message != null && false) { // TODO: No known exception since right now is an opt-in feature
      return GradleUndefinedDaemonJvmCriteriaBuildIssue(message, issueData.projectPath)
    }
    return null
  }
}

private class GradleUndefinedDaemonJvmCriteriaBuildIssue(
  causeMessage: @NlsSafe String,
  externalProjectPath: String,
) : ConfigurableGradleBuildIssue() {
  init {
    setTitle(GradleBundle.message("gradle.build.issue.daemon.toolchain.undefined.criteria.title"))
    addDescription(causeMessage)
    addDaemonToolchainCriteriaQuickFix(externalProjectPath)
  }
}