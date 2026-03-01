// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.cce.project.ProjectSyncInvoker
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.backend.observation.Observation
import com.intellij.warmup.util.OpenProjectArgs
import com.intellij.warmup.util.importOrOpenProjectAsync
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object ProjectOpeningUtils {

  fun trivialProject(): Project {
    val emptyDir = FileUtilRt.createTempDirectory("trivial", null).toPath()
    return runUnderModalProgressIfIsEdt {
      importOrOpenProjectAsync(OpenProjectArgsData(emptyDir))
    }
  }

  fun closeProject(project: Project) {
    LOG.info("Closing project $project...")
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    ApplicationManager.getApplication().invokeAndWait {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project)
    }
  }

  fun open(
    projectPath: String,
    timeoutForOneAttempt: Duration = 20.minutes,
    projectSyncInvoker: ProjectSyncInvoker? = null,
  ): Project {
    println("Open and load project $projectPath. Operation may take a few minutes.")
    return tryToOpenProject(
      OpenProjectArgsData(FileSystems.getDefault().getPath(projectPath)),
      timeoutForOneAttempt,
      projectSyncInvoker
    )
  }

  suspend fun awaitProject(project: Project) {
    LOG.info("Await project configuration")
    Observation.awaitConfiguration(project)
    LOG.info("Awaiting configuration completed")
  }

  private fun tryToOpenProject(
    openProjectArgsData: OpenProjectArgsData,
    timeoutForOneAttempt: Duration,
    projectSyncInvoker: ProjectSyncInvoker?,
  ): Project {
    repeat(3) { iteration ->
      if (iteration > 0) {
        println("Retry to open project. attempt ${iteration + 1}")
      }
      @Suppress("DEPRECATION")
      val project = runUnderModalProgressIfIsEdt {
        try {
          withTimeout(timeoutForOneAttempt) {
            val project = importOrOpenProjectAsync(openProjectArgsData)
            if (projectSyncInvoker != null) {
              projectSyncInvoker.syncProject(project)
              awaitProject(project)
            } else {
              LOG.info("Project sync is skipped")
            }
            project
          }
        } catch (_: TimeoutCancellationException) {
          null
        }
      }
      if (project != null) {
        println("Project loaded!")
        return project
      }
    }
    error("Failed to open project ${openProjectArgsData.projectDir}")
  }
}

data class OpenProjectArgsData(
  override val projectDir: Path,
  override val convertProject: Boolean = true,
  override val configureProject: Boolean = true,
  override val disabledConfigurators: Set<String> = emptySet(),
  override val pathToConfigurationFile: Path? = null,
) : OpenProjectArgs

private val LOG = logger<ProjectOpeningUtils>()
