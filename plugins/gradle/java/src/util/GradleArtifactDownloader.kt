// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.execution.wsl.WslPath.Companion.parseWindowsUncPath
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.GradleJavaCoroutineScopeService.Companion.gradleCoroutineScope
import org.jetbrains.plugins.gradle.service.execution.loadDownloadArtifactInitScript
import org.jetbrains.plugins.gradle.service.execution.loadLegacyDownloadArtifactInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.service.task.LazyVersionSpecificInitScript
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.readText

object GradleArtifactDownloader {

  private val GRADLE_5_6 = GradleVersion.version("5.6")
  private const val INIT_SCRIPT_FILE_PREFIX = "ijArtifactDownloader"

  /**
   * Download a Jar file with specified artifact coordinates by using the Gradle task executed on a specific Gradle module.
   * @param project associated project.
   * @param executionName execution name.
   * @param artifactNotation artifact coordinates in standard artifact format like `group:artifactId:version:classifier:anything:else`.
   * @param externalProjectPath path to the directory with the Gradle module that should be used to execute the task.
   */
  @JvmStatic
  fun downloadArtifact(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    externalProjectPath: String,
  ): CompletableFuture<Path> {
    return project.gradleCoroutineScope.async {
      downloadArtifactImpl(project, executionName, artifactNotation, externalProjectPath)
    }.asCompletableFuture()
  }

  private suspend fun downloadArtifactImpl(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    externalProjectPath: String,
  ): Path {
    val taskName = "ijDownloadArtifact" + UUID.randomUUID().toString().substring(0, 12)
    val settings = ExternalSystemTaskExecutionSettings().also {
      it.executionName = executionName
      it.externalProjectPath = externalProjectPath
      it.taskNames = listOf(taskName)
      it.vmOptions = GradleSettings.getInstance(project).gradleVmOptions
      it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
    val eelDescriptor = project.getEelDescriptor()
    val eel = eelDescriptor.upgrade()
    val absoluteTaskOutputFileEelPath = createTaskOutputFile(eel)
    val taskOutputFile = absoluteTaskOutputFileEelPath.asNioPath()
    try {
      val userData = prepareUserData(
        artifactNotation,
        taskName,
        absoluteTaskOutputFileEelPath.asCanonicalResolvedPath(eel),
        externalProjectPath
      )
      val future = CompletableFuture<Nothing?>()
      ExternalSystemUtil.runTask(
        TaskExecutionSpec.create()
          .withProject(project)
          .withSystemId(GradleConstants.SYSTEM_ID)
          .withSettings(settings)
          .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
          .withUserData(userData)
          .withResultListener(future)
          .withActivateToolWindowBeforeRun(false)
          .withActivateToolWindowOnFailure(false)
          .build()
      )
      future.await()
      val downloadedArtifactPath = taskOutputFile.readText().asResolvedAbsolutePath(eel)
      if (!isValidJar(downloadedArtifactPath)) {
        throw IllegalStateException("Incorrect file header: $downloadedArtifactPath. Unable to process downloaded file as a JAR file")
      }
      return downloadedArtifactPath
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      GradleDependencySourceDownloaderErrorHandler.handle(project, externalProjectPath, artifactNotation, e)
      throw IllegalStateException("Unable to download artifact.", e)
    }
    finally {
      FileUtil.delete(taskOutputFile)
    }
  }

  private fun prepareUserData(artifactNotation: String, taskName: String, taskOutputPath: Path, externalProjectPath: String)
    : UserDataHolderBase {
    val projectPath = externalProjectPath.asSystemDependentGradleProjectPath()
    val legacyInitScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadLegacyDownloadArtifactInitScript(artifactNotation, taskName, taskOutputPath, projectPath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 > it }
    )
    val initScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadDownloadArtifactInitScript(artifactNotation, taskName, taskOutputPath, projectPath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 <= it }
    )
    return UserDataHolderBase().apply {
      putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY, listOf(legacyInitScript, initScript))
    }
  }

  private fun String.asSystemDependentGradleProjectPath(): String {
    val wslPath = parseWindowsUncPath(this)
    val pathToNormalize = wslPath?.linuxPath ?: this
    return Path.of(pathToNormalize).toCanonicalPath()
  }

  private suspend fun createTaskOutputFile(eel: EelApi): EelPath {
    return eel.fs.createTemporaryFile()
      .prefix("ijDownloadArtifactOut")
      .getOrThrow { IllegalStateException("Unable to create the task output file: ${it.message}") }
  }

  private suspend fun EelPath.asCanonicalResolvedPath(eel: EelApi): Path {
    // if the execution is taking place on a local machine, we could use the path as is
    if (eel is LocalEelApi) {
      return this.asNioPath()
    }
    // in the case of executing on a WSL, we have to operate both local and relative WSL paths to be able to use the path inside the
    // Gradle task
    val resolvedPath = eel.fs.canonicalize(this)
      .getOrThrow { java.lang.IllegalStateException("Unable to canonicalize the task output file path: ${it.message}") }

    // We have to use `toString()` to prevent the path from being converted into an absolute one:
    //  - resolvedPath.asNioPath() - nio.Path: `\\wsl$\something\something`;
    //  - Path.of(resolvedPath.toString()) - nio.Path: `/something/something`;
    return Path.of(resolvedPath.toString())
  }

  private fun String.asResolvedAbsolutePath(eel: EelApi): Path {
    val eelPath = eel.fs.getPath(this)
    return eelPath.asNioPath()
  }

  private fun TaskExecutionSpecBuilder.withResultListener(future: CompletableFuture<Nothing?>) = withListener(
    object : ExternalSystemTaskNotificationListener {
      override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        future.complete(null)
      }

      override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
        future.cancel(true)
      }

      override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        future.completeExceptionally(exception)
      }
    }
  )
}