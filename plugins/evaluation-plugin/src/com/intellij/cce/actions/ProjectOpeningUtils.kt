// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.warmup.util.OpenProjectArgs
import java.nio.file.Path

object ProjectOpeningUtils {

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
