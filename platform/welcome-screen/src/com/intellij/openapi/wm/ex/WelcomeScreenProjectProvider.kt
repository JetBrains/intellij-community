// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.ide.GeneralLocalSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.absolute

private val LOG = logger<WelcomeScreenProjectProvider>()
private val EP_NAME: ExtensionPointName<WelcomeScreenProjectProvider> = ExtensionPointName("com.intellij.welcomeScreenProjectProvider")
private const val PROJECTS_DIR = "projects"
private const val PROPERTY_PROJECT_PATH = "%s.project.path"

@Volatile
private var cachedProjectsBasePath: String? = null

@Internal
fun getWelcomeScreenProjectProvider(): WelcomeScreenProjectProvider? {
  val providers = EP_NAME.extensionsIfPointIsRegistered
  if (providers.isEmpty()) {
    return null
  }

  if (providers.size > 1) {
    LOG.warn("Multiple WelcomeScreenProjectProvider extensions")
    return null
  }
  return providers.first()
}

@Internal
interface WelcomeScreenProjectSupport {
  suspend fun createOrOpenWelcomeScreenProject(extension: WelcomeScreenProjectProvider): Project

  suspend fun openProject(path: Path): Project
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

    @Suppress("unused")
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

    @Suppress("unused")
    fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean {
      return getWelcomeScreenProjectProvider()?.canOpenFilesFromSystemFileManager(filePath) ?: false
    }

    suspend fun createOrOpenWelcomeScreenProject(extension: WelcomeScreenProjectProvider): Project {
      return serviceAsync<WelcomeScreenProjectSupport>().createOrOpenWelcomeScreenProject(extension)
    }
  }

  /**
   * Return true if the welcome screen project can open [filePath] from the file manager (Explorer, Finder) or command line.
   */
  abstract fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean

  protected open fun getWelcomeScreenProjectPath(): Path {
    return Path.of(getProjectsBasePath(), getWelcomeScreenProjectName()).absolute()
  }

  @Internal
  fun getWelcomeScreenProjectPathForInternalUsage(): Path {
    return getWelcomeScreenProjectPath()
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
    return serviceAsync<WelcomeScreenProjectSupport>().openProject(path)
  }

  @Internal
  suspend fun doCreateOrOpenWelcomeScreenProjectForInternalUsage(path: Path): Project {
    return doCreateOrOpenWelcomeScreenProject(path)
  }

  protected open fun doIsHiddenInRecentProjects(): Boolean = true

  @Internal
  fun isHiddenInRecentProjectsForInternalUsage(): Boolean = doIsHiddenInRecentProjects()
}

@Suppress("DuplicatedCode")
private fun getProjectsBasePath(): String {
  val application = ApplicationManager.getApplication()
  val fromSettings = if (application == null || application.isHeadlessEnvironment) null else GeneralLocalSettings.getInstance().defaultProjectDirectory
  if (!fromSettings.isNullOrEmpty()) {
    return PathManager.getAbsolutePath(fromSettings)
  }

  if (cachedProjectsBasePath == null) {
    val productName = ApplicationNamesInfo.getInstance().productName.lowercase()
    val propertyName = String.format(PROPERTY_PROJECT_PATH, productName)
    val propertyValue = System.getProperty(propertyName)
    cachedProjectsBasePath = if (propertyValue != null) {
      PathManager.getAbsolutePath(StringUtil.unquoteString(propertyValue, '"'))
    }
    else {
      projectsDirDefault
    }
  }
  return cachedProjectsBasePath!!
}

private val projectsDirDefault: String
  get() = if (PlatformUtils.isDataGrip() || PlatformUtils.isDataSpell()) getUserHomeProjectDir() else Path.of(PathManager.getConfigPath(), PROJECTS_DIR).toString()

private fun getUserHomeProjectDir(): String {
  val appNamesInfo = ApplicationNamesInfo.getInstance()
  val productName = if (PlatformUtils.isCLion() || PlatformUtils.isAppCode() || PlatformUtils.isDataGrip() || PlatformUtils.isMPS()) {
    appNamesInfo.productName
  }
  else {
    appNamesInfo.lowercaseProductName
  }
  return Path.of(System.getProperty("user.home"), productName + "Projects").toString()
}
