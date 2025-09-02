// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.warmup.util.OpenProjectArgs
import com.intellij.warmup.util.importOrOpenProjectAsync
import java.nio.file.FileSystems
import java.nio.file.Path

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

  fun open(projectPath: String): Project {
    println("Open and load project $projectPath. Operation may take a few minutes.")
    @Suppress("DEPRECATION")
    val project = runUnderModalProgressIfIsEdt {
      importOrOpenProjectAsync(OpenProjectArgsData(FileSystems.getDefault().getPath(projectPath)))
    }
    println("Project loaded!")

    return project
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
