// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.properties

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.Companion.getLatestMinorGradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.Companion.isGradleDeprecatedByIdea
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionChecker
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext

internal class OutdatedGradleVersionChecker: GradleExecutionChecker {
  override fun checkExecution(
    context: GradleExecutionContext,
    buildEnvironment: BuildEnvironment,
  ) {
    val currentVersion = GradleVersion.version(buildEnvironment.gradle.gradleVersion)
    if (isGradleDeprecatedByIdea(currentVersion)) return
    if (isLatestMinorVersionInspectionDisabled(context)) return
    if (isMinorGradleVersionOutdated(currentVersion)) return

    val issue = OutdatedGradleVersionIssue(context, currentVersion)
    issue.addOpenInspectionSettingsQuickFix(context, "LatestMinorVersion")

    context.listener.onStatusChange(
      ExternalSystemBuildEvent(
        context.taskId,
        BuildIssueEventImpl(context.taskId, issue, MessageEvent.Kind.INFO)
      )
    )
  }

  private fun isLatestMinorVersionInspectionDisabled(context: GradleExecutionContext): Boolean {
    val inspectionKey = HighlightDisplayKey.find("LatestMinorVersion")
    if (inspectionKey == null) return true
    val projectProfileManager = InspectionProjectProfileManager.getInstance(context.project)
    val inspectionProfile = projectProfileManager.getCurrentProfile()
    return !inspectionProfile.isToolEnabled(inspectionKey)
  }

  private fun isMinorGradleVersionOutdated(currentVersion: GradleVersion): Boolean {
    val latestVersion = getLatestMinorGradleVersion(currentVersion.majorVersion)
    return currentVersion >= latestVersion
  }
}
