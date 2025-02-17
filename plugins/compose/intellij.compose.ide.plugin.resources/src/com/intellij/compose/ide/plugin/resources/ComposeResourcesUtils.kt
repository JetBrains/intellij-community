// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import java.nio.file.Path

const val COMPOSE_RESOURCES_DIR: String = "composeResources"
const val STRINGS_XML_FILENAME: String = "strings.xml"
const val VALUES_DIRNAME: String = "values"

/** Retrieves the module name for the Compose resources task of the given module. */
internal fun Module.getModuleNameForComposeResourcesTask(): String? =
  if (buildSystemType == BuildSystemType.AmperGradle) "" else ExternalSystemApiUtil.getExternalProjectId(this)?.getModuleName()

/**
 * Retrieves the directory for Compose resources for the specified source set name in the module.
 *
 * @param sourceSetName the name of the source set
 * @return the VirtualFile representing the directory for Compose resources, or null if not found
 */
internal fun Module.getComposeResourcesDirFor(sourceSetName: String): VirtualFile? =
  composeResourcesDirsByName[sourceSetName]?.let { VirtualFileManager.getInstance().findFileByNioPath(it.directoryPath) }

/**
 * Retrieves the directory for Compose resources specific to a module's source set.
 * If the directory for the determined source set name does not exist, it defaults to 'commonMain'.
 *
 * @return the VirtualFile representing the directory for Compose resources, or null if not found
 */
internal fun Module.getComposeResourcesDir(): VirtualFile? {
  val sourceSetName = getSourceSetNameFromComposeResourcesDir()
  return getComposeResourcesDirFor(sourceSetName) ?: getComposeResourcesDirFor("commonMain")
}

/**
 * Return a list of all the Compose resources directories present in the given [Project]
 * */
internal fun Project.getAllComposeResourcesDirs(): List<ComposeResourcesDir> =
  service<ComposeResourcesManager>().composeResourcesByModulePath.flatMap { it.value.directoriesBySourceSetName.values }


/** Return a map of all the Compose resources directories present in the given [Module] */
internal val Module.composeResourcesDirsByName: Map<String, ComposeResourcesDir>
  get() = getModuleNameForComposeResourcesTask()?.let { moduleName ->
    project.service<ComposeResourcesManager>().composeResourcesByModulePath[moduleName]?.directoriesBySourceSetName.orEmpty()
  } ?: emptyMap()

internal data class ComposeResourcesDir(val moduleName: String, val sourceSetName: String, val directoryPath: Path, val isCustom: Boolean = false)

internal data class ComposeResources(val moduleName: String, val directoriesBySourceSetName: Map<String, ComposeResourcesDir>)