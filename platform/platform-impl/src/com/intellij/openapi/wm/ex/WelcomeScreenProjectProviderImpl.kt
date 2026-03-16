// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.PlatformProjectOpenProcessor
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private val LOG = logger<WelcomeScreenProjectSupportImpl>()

internal class WelcomeScreenProjectSupportImpl : WelcomeScreenProjectSupport {
  override suspend fun createOrOpenWelcomeScreenProject(extension: WelcomeScreenProjectProvider): Project {
    val projectPath = extension.getWelcomeScreenProjectPathForInternalUsage()

    if (!projectPath.exists(LinkOption.NOFOLLOW_LINKS)) {
      projectPath.createDirectories()
    }
    TrustedProjects.setProjectTrusted(projectPath, true)
    serviceAsync<WindowsDefenderChecker>().markProjectPath(projectPath, /*skip =*/ true)

    val project = extension.doCreateOrOpenWelcomeScreenProjectForInternalUsage(projectPath)
    LOG.info("Opened the welcome screen project at $projectPath")
    LOG.debug("Project: ", project)

    val recentProjectsManager = serviceAsync<RecentProjectsManager>() as RecentProjectsManagerBase
    recentProjectsManager.setProjectHidden(project, extension.isHiddenInRecentProjectsForInternalUsage())
    TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT.set(project, true)

    return project
  }

  override suspend fun openProject(path: Path): Project {
    return PlatformProjectOpenProcessor.openProjectAsync(path)
           ?: throw IllegalStateException("Cannot open project at $path (not expected that user can cancel welcome-project loading)")
  }
}
