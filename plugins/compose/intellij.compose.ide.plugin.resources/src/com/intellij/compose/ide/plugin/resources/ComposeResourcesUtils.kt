// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile

internal const val COMPOSE_RESOURCES_DIR: String = "composeResources"
internal const val STRINGS_XML_FILENAME: String = "strings.xml"
internal const val VALUES_DIRNAME: String = "values"

internal const val ANDROID_RESOURCE_REFERENCE = "org.jetbrains.android.dom.converters.AndroidResourceReference"

private val VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES = setOf("drawable", "font", "values")

internal val String.isValidInnerComposeResourcesDirName: Boolean
  get() =
    VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES.any { this.startsWith(it, ignoreCase = true) }

internal fun String.isValidInnerComposeResourcesDirNameFor(dirNames: Set<String>): Boolean =
  (dirNames intersect VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES).any { this.startsWith(it, ignoreCase = true) }

internal val String.withoutExtension: String get() = substringBeforeLast(".")

/**
 * Retrieves the directory for Compose resources specific to a module's source set.
 * If the directory for the determined source set name does not exist, it defaults to 'commonMain'.
 *
 * @return the VirtualFile representing the directory for Compose resources, or null if not found
 */
internal fun Module.getComposeResourcesDir(): VirtualFile? {
  val composeData = ComposeResourcesDataProvider.findProviderForProject(project)
                      ?.getComposeDataForModule(this) ?: return null
  val fileManager = VirtualFileManager.getInstance()
  return fileManager.findFileByNioPath(composeData.directoryPath) ?: composeData.commonResourcesPath?.let(fileManager::findFileByNioPath)
}

/**
 * Determines if the given file belongs to a Compose resources directory structure.
 *
 * This function checks whether the file's parent directory name is a valid inner Compose resources directory name
 * and whether the grandparent directory is a registered Compose resources directory.
 *
 * @return `true` if the file is part of a Compose resources directory, `false` otherwise
 */
internal fun PsiFile.isComposeResourcesFile(
  validInnerComposeResourcesDirNames: Set<String> = VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES,
): Boolean {
  val parentName = this.parent?.name ?: return false
  return parentName.isValidInnerComposeResourcesDirNameFor(validInnerComposeResourcesDirNames) &&
         ComposeResourcesDataProvider.findProviderForProject(project)?.getComposeDataForResourceFile(this) != null
}