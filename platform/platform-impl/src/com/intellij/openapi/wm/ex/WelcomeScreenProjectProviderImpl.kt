// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.configureToOpenDotIdeaOrCreateNewIfNotExists
import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private val LOG = logger<WelcomeScreenProjectSupportImpl>()

internal class WelcomeScreenProjectSupportImpl : WelcomeScreenProjectSupport {
  private fun surveySupport() {
    val properties = PropertiesComponent.getInstance()
    if (properties.getLong("projectless.survey.time", 0) == 0L) {
      properties.setValue("projectless.survey.time", System.currentTimeMillis().toString())
    }
  }

  override suspend fun createOrOpenWelcomeScreenProject(
    extension: WelcomeScreenProjectProvider,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project {
    surveySupport()

    val simpleProject = extension.createSimpleProject(projectToClose, forceOpenInNewFrame)
    if (simpleProject != null) {
      return simpleProject
    }

    val projectPath = extension.getWelcomeScreenProjectPathForInternalUsage()

    if (!projectPath.exists(LinkOption.NOFOLLOW_LINKS)) {
      try {
        projectPath.createDirectories()
      }
      catch (_: IOException) {
      }
    }

    serviceAsync<WindowsDefenderChecker>().markProjectPath(projectPath, /*skip =*/ true)

    val project = extension.doCreateOrOpenWelcomeScreenProjectForInternalUsage(projectPath, projectToClose, forceOpenInNewFrame)
    LOG.info("Opened the welcome screen project at $projectPath")
    LOG.debug("Project: ", project)

    val recentProjectsManager = serviceAsync<RecentProjectsManager>() as RecentProjectsManagerBase
    recentProjectsManager.setProjectHidden(project, true)
    TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT.set(project, true)

    return project
  }

  override suspend fun openProject(path: Path, name: String, forceOpenInNewFrame: Boolean): Project {
    val options = OpenProjectTask {
      configureToOpenDotIdeaOrCreateNewIfNotExists(path, null)
      projectName = name
      this.forceOpenInNewFrame = forceOpenInNewFrame
    }
    return PlatformProjectOpenProcessor.openProjectAsync(path, options)
           ?: throw IllegalStateException("Cannot open project at $path (not expected that user can cancel welcome-project loading)")
  }
}
