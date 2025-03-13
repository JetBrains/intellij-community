// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.warmup.util.OpenProjectArgs
import java.nio.file.Path
import java.nio.file.Paths

object ProjectOpeningUtils {

  fun trivialProject(): Project {
    val tempDirectory = Paths.get(FileUtilRt.getTempDirectory()).resolve(ProjectImpl.LIGHT_PROJECT_NAME + ProjectFileType.DOT_DEFAULT_EXTENSION)
    val task = OpenProjectTask()
    return ProjectManagerEx.getInstanceEx().newProject(tempDirectory, task)!!
  }

  fun closeProject(project: Project) {
    LOG.info("Closing project $project...")
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    ApplicationManager.getApplication().invokeAndWait {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project)
    }
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
