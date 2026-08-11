// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.util.NlsSafe
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesConfigurator
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.function.Consumer

/**
 * An [GradleIssueChecker] handling Daemon JVM criteria exceptions like:
 * "Unable to download toolchain matching the requirements ({languageVersion=1.1, vendor=any vendor, implementation=vendor-specific})
 * from '<URL>', due to: No defined toolchain download url for MAC_OS on aarch64 architecture."
 */
@Internal
class GradleToolchainDownloadingErrorIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = issueData.failure.rootCause
    val message = rootCause.messageOrDescription
    if (message?.startsWith("Unable to download toolchain matching the requirements") == true) {
      return GradleToolchainDownloadingErrorBuildIssue(message, issueData.projectPath)
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

private class GradleToolchainDownloadingErrorBuildIssue(
  causeMessage: @NlsSafe String,
  externalProjectPath: String,
) : ConfigurableGradleBuildIssue() {
  init {
    setTitle(GradleBundle.message("gradle.build.issue.daemon.toolchain.download.error.title"))
    addDescription(causeMessage)
    addDownloadToolchainQuickFix(externalProjectPath)
    addOpenDaemonJvmSettingsQuickFix()
  }
}