// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.PlatformProjectOpenProcessor
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private val LOG = logger<WelcomeScreenProjectProvider>()
private val EP_NAME: ExtensionPointName<WelcomeScreenProjectProvider> = ExtensionPointName("com.intellij.welcomeScreenProjectProvider")

@Internal
fun getWelcomeScreenProjectProvider(): WelcomeScreenProjectProvider? {
  val providers = EP_NAME.extensionList
  if (providers.isEmpty()) {
    return null
  }

  if (providers.size > 1) {
    LOG.warn("Multiple WelcomeScreenProjectProvider extensions")
    return null
  }
  return providers.first()
}

/**
 * Allows identifying projects that act as a welcome screen tab.
 * This is needed for customizing actions context.
 *
 * E.g., if a project is created/opened/cloned from a welcome screen project,
 * we should close the welcome screen project to preserve the welcome screen experience.
 *
 * This customization is intended to be used per-IDE, not per language.
 */
@Internal
abstract class WelcomeScreenProjectProvider {
  companion object {
    fun isWelcomeScreenProject(project: Project): Boolean {
      val extension = getWelcomeScreenProjectProvider() ?: return false
      return extension.doIsWelcomeScreenProject(project)
    }

    fun isEditableWelcomeProject(project: Project): Boolean {
      val extension = getWelcomeScreenProjectProvider() ?: return false
      return extension.doIsWelcomeScreenProject(project) && extension.doIsEditableProject(project)
    }

    fun isForceDisabledFileColors(): Boolean {
      val extension = getWelcomeScreenProjectProvider() ?: return false
      return extension.doIsForceDisabledFileColors()
    }

    fun getCreateNewFileProjectPrefix(): String {
      val extension = getWelcomeScreenProjectProvider() ?: return ""
      return extension.doGetCreateNewFileProjectPrefix()
    }

    fun getWelcomeScreenProjectPath(): Path? {
      return getWelcomeScreenProjectProvider()?.getWelcomeScreenProjectPath()
    }

    fun canOpenFilesFromSystemFileManager(): Boolean {
      return getWelcomeScreenProjectProvider()?.canOpenFilesFromSystemFileManager() ?: false
    }

    suspend fun createOrOpenWelcomeScreenProject(extension: WelcomeScreenProjectProvider): Project {
      val projectPath = extension.getWelcomeScreenProjectPath()

      if (!projectPath.exists(LinkOption.NOFOLLOW_LINKS)) {
        projectPath.createDirectories()
      }
      TrustedProjects.setProjectTrusted(projectPath, true)
      serviceAsync<WindowsDefenderChecker>().markProjectPath(projectPath, /*skip =*/ true)

      val project = extension.doCreateOrOpenWelcomeScreenProject(projectPath)
      LOG.info("Opened the welcome screen project at $projectPath")
      LOG.debug("Project: ", project)

      val recentProjectsManager = serviceAsync<RecentProjectsManager>() as RecentProjectsManagerBase
      recentProjectsManager.setProjectHidden(project, extension.doIsHiddenInRecentProjects())
      TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT.set(project, true)

      return project
    }
  }

  /**
   * Return true if the welcome screen project can open non-project files from the file manager (Explorer, Finder).
   */
  abstract fun canOpenFilesFromSystemFileManager(): Boolean

  protected open fun getWelcomeScreenProjectPath(): Path {
    return ProjectUtil.getProjectPath(getWelcomeScreenProjectName()).absolute()
  }

  protected abstract fun getWelcomeScreenProjectName(): String

  protected abstract fun doIsWelcomeScreenProject(project: Project): Boolean

  /**
   * Return true if your project is not only a welcome screen, but also a real project where the user can create, store and edit files.
   * Junie and other features might be disabled for non-editable welcome screen projects.
   * See MTRH-1423
   */
  protected open fun doIsEditableProject(project: Project): Boolean {
    return false
  }

  protected abstract fun doIsForceDisabledFileColors(): Boolean

  protected abstract fun doGetCreateNewFileProjectPrefix(): String

  protected open suspend fun doCreateOrOpenWelcomeScreenProject(path: Path): Project {
    return PlatformProjectOpenProcessor.openProjectAsync(path)
           ?: throw IllegalStateException("Cannot open project at $path (not expected that user can cancel welcome-project loading)")
  }

  protected open fun doIsHiddenInRecentProjects(): Boolean = true
}
