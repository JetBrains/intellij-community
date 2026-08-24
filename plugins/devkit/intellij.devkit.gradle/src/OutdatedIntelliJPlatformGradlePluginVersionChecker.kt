// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.devkit.gradle.tooling.IntelliJPlatformAuxiliaryArtifactProvider
import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.connection.GradleConnectorService
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionChecker
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContextImpl
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.service.execution.createMainInitScript
import org.jetbrains.plugins.gradle.service.execution.createTargetPathMapperInitScript
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

internal class OutdatedIntelliJPlatformGradlePluginVersionChecker : GradleExecutionChecker {

  override fun checkExecution(
    context: GradleExecutionContext,
    buildEnvironment: BuildEnvironment,
  ) {
    val gradleModel = getGradleModel(context, buildEnvironment) ?: return

    val currentVersion = GradleVersion.version(gradleModel.currentPluginVersion)
    val latestVersion = GradleVersion.version(gradleModel.latestPluginVersion)

    // TODO: if (isLatestMinorVersionInspectionDisabled(context)) return
    if (currentVersion >= latestVersion) return

    val issue = OutdatedIntelliJPlatformGradlePluginVersionIssue(context.projectPath, currentVersion, latestVersion)

    val taskId = context.taskId
    context.listener.onStatusChange(
      ExternalSystemBuildEvent(
        taskId,
        BuildIssueEventImpl(taskId, issue, MessageEvent.Kind.INFO)
      )
    )
  }

  private fun getGradleModel(
    context: GradleExecutionContext,
    buildEnvironment: BuildEnvironment,
  ): IntelliJPlatformGradleModel? {
    val settings = GradleExecutionSettings(context.settings).apply {
      addInitScript(createTargetPathMapperInitScript())
      addInitScript(
        GradleVersion.version(buildEnvironment.gradle.gradleVersion),
        createMainInitScript(
          isBuildSrcProject = false,
          toolingExtensionClasses = setOf(IntelliJPlatformAuxiliaryArtifactProvider::class.java),
        ),
      )
    }
    val modelContext = GradleExecutionContextImpl(
      context as GradleExecutionContextImpl,
      context.projectPath,
      settings,
    ).apply {
      this.buildEnvironment = buildEnvironment
    }

    return try {
      GradleConnectorService.getInstance(context.project).withGradleConnection(
        context.projectPath,
        context.taskId,
        settings,
        context.listener,
        context.cancellationToken,
      ) { connection ->
        val modelBuilder = connection.model(IntelliJPlatformGradleModel::class.java)
        GradleExecutionHelper.prepareForExecution(modelBuilder, modelContext)
        modelBuilder.get()
      }
    }
    catch (exception: Exception) {
      rethrowControlFlowException(exception)
      LOG.warn("Failed to obtain the IntelliJ Platform Gradle model", exception)
      null
    }
  }

  companion object {
    private val LOG = Logger.getInstance(OutdatedIntelliJPlatformGradlePluginVersionChecker::class.java)
  }
}
