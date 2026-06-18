// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.gradle

import com.intellij.compose.ide.plugin.resources.ComposeResourcesData
import com.intellij.compose.ide.plugin.resources.ComposeResourcesDataProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.plugins.gradle.settings.GradleSettings

private val log by lazy { logger<GradleComposeResourcesDataProvider>() }

internal class GradleComposeResourcesDataProvider : ComposeResourcesDataProvider {
  override fun isApplicable(project: Project): Boolean {
    return GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()
  }

  override fun getComposeDataForModule(module: Module): ComposeResourcesData? {
    val sourceSetName = module.getSourceSetNameFromComposeResourcesDir()
    val composeResourcesDir = module.composeResourcesDirsByName[sourceSetName] ?: module.composeResourcesDirsByName["commonMain"] ?: run {
      log.warn("No Compose resources directory found for module ${module.name} and source set $sourceSetName.")
      return null
    }
    return composeResourcesDir.toResourcesData(module.project)
  }

  override fun getComposeDataForResourceFile(file: PsiFile): ComposeResourcesData? {
    val resourceDirectory = file.parent ?: return null
    return getComposeDataForResourceFolder(resourceDirectory)
  }

  override fun getComposeDataForResourceFolder(folder: PsiDirectory): ComposeResourcesData? {
    val folderPath = folder.virtualFile.toNioPathOrNull()
    return folder.project
      .getAllComposeResourcesDirs()
      .firstOrNull { folderPath?.startsWith(it.directoryPath) == true }
      ?.toResourcesData(folder.project)
  }

  private fun GradleComposeResourcesDir.toResourcesData(project: Project): ComposeResourcesData? {
    val config = project.service<GradleComposeResourcesManager>().composeResourcesByModulePath[moduleName] ?: return null
    return GradleComposeResourcesData(
      project = project,
      config = config,
      composeResourcesDir = this,
    )
  }

  /**
   * Returns sourceSet name from a module name as expected by the Compose resources model
   *
   * - `projectName.composeApp.commonMain` -> `commonMain`
   * - `projectName.composeApp.iosMain` -> `iosMain`
   *
   * Notable cases:
   * - `projectName.composeApp.desktopMain` -> `desktopMain` (old project layout)
   * - `projectName.desktopApp.main` -> `main` (new project layout)
   *
   * - `projectName.composeApp.main` -> `androidMain` (old project layout)
   * - `projectName.shared.androidMain` -> `androidMain` (new project layout)
   * - `projectName.app.androidApp.main` -> `main` (new project layout)
   */
  private fun Module.getSourceSetNameFromComposeResourcesDir(): String =
    name.substringAfterLast('.').takeUnless { isAndroidModule() && it == "main" } ?: "androidMain"

  /**
   * Retrieves the module name for the Compose resources task of the given module.
   *
   * example:
   * name: `projectName.composeApp.main` -> composeApp
   * name: `projectName.app.shared.commonMain` -> shared
   * */
  private fun Module.getModuleNameForComposeResourcesTask(): String? {
    val nameParts = name.split('.')
    return nameParts.getOrNull(nameParts.lastIndex - 1)
  }

  /** Return a map of all the Compose resources directories present in the given [Module] */
  private val Module.composeResourcesDirsByName: Map<String, GradleComposeResourcesDir>
    get() = getModuleNameForComposeResourcesTask()?.let { moduleName ->
      project.service<GradleComposeResourcesManager>().composeResourcesByModulePath[moduleName]?.directoriesBySourceSetName.orEmpty()
    } ?: emptyMap()

  /**
   * Return a list of all the Compose resources directories present in the given [Project]
   * */
  private fun Project.getAllComposeResourcesDirs(): List<GradleComposeResourcesDir> =
    service<GradleComposeResourcesManager>().composeResourcesByModulePath.flatMap { it.value.directoriesBySourceSetName.values }
}
