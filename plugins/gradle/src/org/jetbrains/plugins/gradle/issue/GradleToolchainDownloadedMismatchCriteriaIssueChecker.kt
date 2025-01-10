// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesConfigurator
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.function.Consumer

/**
 * An [GradleIssueChecker] handling Daemon JVM criteria exceptions like:
 * "Toolchain provisioned from '<URL>' doesn't satisfy the specification: {languageVersion=11, vendor=any, implementation=vendor-specific}"
 */
class GradleToolchainDownloadedMismatchCriteriaIssueChecker : GradleIssueChecker {

    override fun check(issueData: GradleIssueData): BuildIssue? {
        val rootCause = getRootCauseAndLocation(issueData.error).first
        if (rootCause.message?.startsWith("Toolchain provisioned from") == true) {
            return GradleToolchainDownloadedMismatchCriteriaBuildIssue(rootCause, issueData.projectPath)
        }
        return null
    }

    override fun consumeBuildOutputFailureMessage(
        message: String,
        failureCause: String,
        stacktrace: String?,
        location: FilePosition?,
        parentEventId: Any,
        messageConsumer: Consumer<in BuildEvent>
    ): Boolean {
        return parentEventId == ":${DaemonJvmPropertiesConfigurator.TASK_NAME}"
    }
}

private class GradleToolchainDownloadedMismatchCriteriaBuildIssue(
    cause: Throwable,
    externalProjectPath: String
) : ConfigurableGradleBuildIssue() {
    init {
        setTitle(GradleBundle.message("gradle.build.issue.daemon.toolchain.downloaded.mismatch.criteria.title"))
        addDescription(cause.message ?: title)
        addRecreateToolchainDownloadUrlsQuickFix(externalProjectPath)
        addOpenDaemonJvmSettingsQuickFix()
    }
}