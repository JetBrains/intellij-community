// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.PlatformProjectOpenProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@Service(Service.Level.APP)
private class WelcomeProjectScopeHolder(val coroutineScope: CoroutineScope)

/**
 * Allows identifying projects that act as a welcome screen tab.
 * This is needed for customizing actions context.
 *
 * E.g., if a project is created/opened/cloned from a welcome screen project,
 * we should close the welcome screen project to preserve the welcome screen experience.
 *
 * This customization is intended to be used per-IDE, not per language.
 */
@ApiStatus.Internal
abstract class WelcomeScreenProjectProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenProjectProvider> = ExtensionPointName("com.intellij.welcomeScreenProjectProvider")

    private fun getSingleExtension(): WelcomeScreenProjectProvider? {
      val providers = EP_NAME.extensionList
      if (providers.isEmpty()) return null
      if (providers.size > 1) {
        thisLogger().warn("Multiple WelcomeScreenProjectProvider extensions")
        return null
      }
      return providers.first()
    }

    @JvmStatic
    fun isWelcomeScreenProject(project: Project): Boolean {
      val extension = getSingleExtension() ?: return false
      return extension.doIsWelcomeScreenProject(project)
    }

    @JvmStatic
    fun isForceDisabledFileColors(): Boolean {
      val extension = getSingleExtension() ?: return false
      return extension.doIsForceDisabledFileColors()
    }

    @JvmStatic
    fun getCreateNewFileProjectPrefix(): String {
      val extension = getSingleExtension() ?: return ""
      return extension.doGetCreateNewFileProjectPrefix()
    }

    fun getWelcomeScreenProjectPath(): Path? {
      return getSingleExtension()?.getWelcomeScreenProjectPath()
    }

    @JvmStatic
    suspend fun createOrOpenWelcomeScreenProject(): Project? {
      val extension = getSingleExtension() ?: return null
      val projectPath = extension.getWelcomeScreenProjectPath()

      if (!projectPath.exists(LinkOption.NOFOLLOW_LINKS)) {
        projectPath.createDirectories()
      }
      TrustedProjects.setProjectTrusted(projectPath, true)
      service<WindowsDefenderChecker>().markProjectPath(projectPath, /*skip =*/ true)

      val project = extension.doCreateOrOpenWelcomeScreenProject(projectPath) ?: return null
      LOG.info("Opened the welcome screen project at $projectPath")
      LOG.debug("Project: ", project)

      val recentProjectsManager = RecentProjectsManagerBase.getInstanceEx()
      recentProjectsManager.setProjectHidden(project, true)
      TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT.set(project, true)

      return project
    }

    @JvmStatic
    fun createOrOpenWelcomeScreenProjectAsync() {
      service<WelcomeProjectScopeHolder>().coroutineScope.launch {
         createOrOpenWelcomeScreenProject()
      }
    }

    private val LOG = logger<WelcomeScreenProjectProvider>()
  }

  protected open fun getWelcomeScreenProjectPath(): Path {
    return ProjectUtil.getProjectPath(getWelcomeScreenProjectName()).absolute()
  }

  protected abstract fun getWelcomeScreenProjectName(): String

  protected abstract fun doIsWelcomeScreenProject(project: Project): Boolean

  protected abstract fun doIsForceDisabledFileColors(): Boolean

  protected abstract fun doGetCreateNewFileProjectPrefix(): String

  protected open suspend fun doCreateOrOpenWelcomeScreenProject(path: Path): Project? {
    return PlatformProjectOpenProcessor.getInstance().openProjectAndFile(path, tempProject = false)
  }
}
