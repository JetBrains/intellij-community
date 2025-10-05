// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.hasTargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.diagnostic.telemetry.helpers.use
import org.gradle.tooling.CancellationToken
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File
import java.util.function.Supplier

@ApiStatus.Internal
object GradleWrapperHelper {

  private val TELEMETRY = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

  @JvmStatic
  @Deprecated("Use [ensureInstalledWrapper] function with [GradleExecutionContext]")
  fun ensureInstalledWrapper(
    id: ExternalSystemTaskId,
    projectPath: String,
    settings: GradleExecutionSettings,
    listener: ExternalSystemTaskNotificationListener,
    cancellationToken: CancellationToken,
  ) {
    ensureInstalledWrapper(id, projectPath, settings, null, listener, cancellationToken)
  }

  @JvmStatic
  @Deprecated("Use [ensureInstalledWrapper] function with [GradleExecutionContext]")
  fun ensureInstalledWrapper(
    id: ExternalSystemTaskId,
    projectPath: String,
    settings: GradleExecutionSettings,
    gradleVersion: GradleVersion?,
    listener: ExternalSystemTaskNotificationListener,
    cancellationToken: CancellationToken,
  ) {
    ensureInstalledWrapper(GradleExecutionContextImpl(projectPath, id, settings, listener, cancellationToken), gradleVersion)
  }

  @JvmStatic
  @JvmOverloads
  fun ensureInstalledWrapper(context: GradleExecutionContext, gradleVersion: GradleVersion? = null) {

    val settings = context.settings
    val projectPath = GradleUtil.determineRootProject(context.projectPath)

    if (!settings.distributionType.isWrapped) {
      return
    }
    val hasWrapperProperties = GradleUtil.findDefaultWrapperPropertiesFile(projectPath) != null
    if (settings.distributionType == DistributionType.DEFAULT_WRAPPED && hasWrapperProperties) {
      return
    }

    val wrapperContext = GradleExecutionContextImpl(
      context, projectPath,
      GradleExecutionSettings(settings).also {
        it.tasks = listOf("wrapper")
        it.remoteProcessIdleTtlInMs = 100
        it.putUserData(GradleExecutionHelper.AUTO_JAVA_HOME, true)
      }
    )

    GradleExecutionHelper.execute(wrapperContext) { connection ->
      TELEMETRY.spanBuilder("EnsureInstalledWrapper").use {
        val propertiesFile = setupWrapperTaskInInitScript(wrapperContext, gradleVersion)

        val launcher = connection.newBuild()
        GradleExecutionHelper.prepareForExecution(launcher, wrapperContext)
        TELEMETRY.spanBuilder("ExecuteWrapperTask").use {
          launcher.run()
        }

        // if autoimport is active, it should be notified of new files creation as early as possible,
        // to avoid triggering unnecessary re-imports (caused by creation of wrapper)
        VfsUtil.markDirtyAndRefresh(false, true, true, File(projectPath, "gradle"))

        settings.wrapperPropertyFile = propertiesFile.get()
      }
    }
  }

  private fun setupWrapperTaskInInitScript(context: GradleExecutionContext, gradleVersion: GradleVersion?): Supplier<String?> {
    if (context.settings.hasTargetEnvironmentConfiguration()) {
      // todo add the support for org.jetbrains.plugins.gradle.settings.DistributionType.WRAPPED
      return Supplier {
        GradleUtil.findDefaultWrapperPropertiesFile(context.projectPath)?.toCanonicalPath()
      }
    }

    val wrapperFilesLocation = FileUtil.createTempDirectory("wrap", "loc")
    val jarFile = File(wrapperFilesLocation, "gradle-wrapper.jar")
    val scriptFile = File(wrapperFilesLocation, "gradlew")
    val fileWithPathToProperties = File(wrapperFilesLocation, "path.tmp")

    val initScriptFile = createWrapperInitScript(gradleVersion, jarFile, scriptFile, fileWithPathToProperties)
    context.settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptFile.toString())

    return Supplier {
      FileUtil.loadFileOrNull(fileWithPathToProperties)
    }
  }
}