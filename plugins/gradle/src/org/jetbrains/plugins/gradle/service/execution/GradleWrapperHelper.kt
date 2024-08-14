// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.hasTargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.util.ExceptionUtil
import com.intellij.util.PlatformUtils
import io.opentelemetry.api.trace.StatusCode
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.GradleConnectorService.Companion.withGradleConnection
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.awt.geom.IllegalPathStateException
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.function.Supplier

object GradleWrapperHelper {

  private val log = logger<GradleWrapperHelper>()

  @JvmStatic
  fun ensureInstalledWrapper(id: ExternalSystemTaskId,
                             projectPath: String,
                             settings: GradleExecutionSettings,
                             listener: ExternalSystemTaskNotificationListener,
                             cancellationToken: CancellationToken) {
    ensureInstalledWrapper(id, projectPath, settings, null, listener, cancellationToken)
  }

  @JvmStatic
  fun ensureInstalledWrapper(id: ExternalSystemTaskId,
                             projectPath: String,
                             settings: GradleExecutionSettings,
                             gradleVersion: GradleVersion?,
                             listener: ExternalSystemTaskNotificationListener,
                             cancellationToken: CancellationToken) {
    if (!settings.distributionType.isWrapped) {
      return
    }
    if (settings.distributionType == DistributionType.DEFAULT_WRAPPED && GradleUtil.findDefaultWrapperPropertiesFile(projectPath) != null) {
      // Fleet cannot resolve Gradle wrappers from places other than the project root
      if (!PlatformUtils.isFleetBackend()) return
    }
    withGradleConnection(projectPath, id, settings, listener, cancellationToken) {
      ensureInstalledWrapper(id, projectPath, settings, gradleVersion, listener, it, cancellationToken)
    }
  }

  private fun ensureInstalledWrapper(id: ExternalSystemTaskId,
                                     projectPath: String,
                                     settings: GradleExecutionSettings,
                                     gradleVersion: GradleVersion?,
                                     listener: ExternalSystemTaskNotificationListener,
                                     connection: ProjectConnection,
                                     cancellationToken: CancellationToken) {
    val ttlInMs = settings.remoteProcessIdleTtlInMs
    val span = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("EnsureInstalledWrapper")
      .startSpan()
    try {
      span.makeCurrent().use {
        settings.remoteProcessIdleTtlInMs = 100

        if (settings.hasTargetEnvironmentConfiguration()) {
          // todo add the support for org.jetbrains.plugins.gradle.settings.DistributionType.WRAPPED
          executeWrapperTask(id, settings, projectPath, listener, connection, cancellationToken)

          val wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(projectPath)
          if (wrapperPropertiesFile != null) {
            settings.wrapperPropertyFile = wrapperPropertiesFile.toString()
          }
        }
        else {
          val propertiesFile = setupWrapperTaskInInitScript(gradleVersion, settings)

          executeWrapperTask(id, settings, projectPath, listener, connection, cancellationToken)

          val wrapperPropertiesFile = propertiesFile.get()
          if (wrapperPropertiesFile != null) {
            settings.wrapperPropertyFile = wrapperPropertiesFile
          }
        }
      }
    }
    catch (e: ProcessCanceledException) {
      span.recordException(e)
      throw e
    }
    catch (e: IOException) {
      log.warn("Can't update wrapper", e)
      span.recordException(e)
    }
    catch (e: Throwable) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR)
      log.warn("Can't update wrapper", e)
      val rootCause = ExceptionUtil.getRootCause(e)
      val externalSystemException = ExternalSystemException(ExceptionUtil.getMessage(rootCause))
      externalSystemException.initCause(e)
      throw externalSystemException
    }
    finally {
      settings.remoteProcessIdleTtlInMs = ttlInMs
      span.end()
      try {
        // if autoimport is active, it should be notified of new files creation as early as possible,
        // to avoid triggering unnecessary re-imports (caused by creation of wrapper)
        VfsUtil.markDirtyAndRefresh(false, true, true, Path.of(projectPath, "gradle").toFile())
      }
      catch (ignore: IllegalPathStateException) {
      }
    }
  }

  private fun executeWrapperTask(
    id: ExternalSystemTaskId,
    settings: GradleExecutionSettings,
    projectPath: String,
    listener: ExternalSystemTaskNotificationListener,
    connection: ProjectConnection,
    cancellationToken: CancellationToken
  ) {
    SystemPropertiesAdjuster.executeAdjusted(projectPath) {
      val launcher: BuildLauncher = GradleExecutionHelper().getBuildLauncher(connection, id, listOf("wrapper"), settings, listener)
      launcher.withCancellationToken(cancellationToken)
      ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
        .spanBuilder("ExecuteWrapperTask")
        .use { launcher.run() }
    }
  }

  @Throws(IOException::class)
  private fun setupWrapperTaskInInitScript(
    gradleVersion: GradleVersion?,
    settings: GradleExecutionSettings
  ): Supplier<String?> {
    val wrapperFilesLocation = FileUtil.createTempDirectory("wrap", "loc")
    val jarFile = File(wrapperFilesLocation, "gradle-wrapper.jar")
    val scriptFile = File(wrapperFilesLocation, "gradlew")
    val fileWithPathToProperties = File(wrapperFilesLocation, "path.tmp")

    val initScriptFile = createWrapperInitScript(gradleVersion, jarFile, scriptFile, fileWithPathToProperties)
    settings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScriptFile.toString())

    return Supplier { FileUtil.loadFileOrNull(fileWithPathToProperties) }
  }
}