// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.psi.getResourcePackageName
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import java.nio.file.Path

internal const val COMPOSE_RESOURCES_DIR: String = "composeResources"
internal const val STRINGS_XML_FILENAME: String = "strings.xml"
internal const val VALUES_DIRNAME: String = "values"

internal const val ANDROID_RESOURCE_REFERENCE = "org.jetbrains.android.dom.converters.AndroidResourceReference"

internal val ALL_STRING_TAGS = setOf("string", "string-array", "plurals", "item", "resources")
internal val VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES = setOf("drawable", "font", "values")

internal val String.isValidInnerComposeResourcesDirName: Boolean
  get() =
    VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES.any { this.startsWith(it, ignoreCase = true) }

internal fun String.isValidInnerComposeResourcesDirNameFor(dirNames: Set<String>): Boolean =
  (dirNames intersect VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES).any { this.startsWith(it, ignoreCase = true) }

internal val String.withoutExtension: String get() = substringBeforeLast(".")

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
  return getComposeResourcesDirFor(sourceSetName) ?: getComposeResourcesDirFor("commonMain") ?: run {
    log.warn("No Compose resources directory found for module $name and source set $sourceSetName.")
    null
  }
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
  if (!parentName.isValidInnerComposeResourcesDirNameFor(validInnerComposeResourcesDirNames)) return false
  val composeResourcesDir = this.parent?.parent?.virtualFile?.toNioPathOrNull() ?: return false
  return this.project.getAllComposeResourcesDirs().any { it.directoryPath == composeResourcesDir }
}

/**
 * Returns the [ResourceType] of the given file if it belongs to a Compose resources directory structure or `null` otherwise.
 *
 * This function calls [isComposeResourcesFile] and, if the file is a valid Compose resources file,
 * it resolves its [ResourceType] from the file's path via [ResourceType.fromPath].
 *
 * @return the [ResourceType] associated with this file, or `null` if the file is not a Compose resources file
 */
internal fun PsiFile.getComposeResourceType(
  validInnerComposeResourcesDirNames: Set<String> = VALID_INNER_COMPOSE_RESOURCES_DIR_NAMES,
): ResourceType? {
  if (!isComposeResourcesFile(validInnerComposeResourcesDirNames)) return null
  val filePath = this.virtualFile?.toNioPathOrNull() ?: return null
  return ResourceType.fromPath(filePath)
}

/**
 * Return a list of all the Compose resources directories present in the given [Project]
 * */
internal fun Project.getAllComposeResourcesDirs(): List<ComposeResourcesDir> =
  service<ComposeResourcesManager>().composeResourcesByModulePath.flatMap { it.value.directoriesBySourceSetName.values }

/** Return ComposeResourceDir associated with a given resource @param [path] or null if none is found */
internal fun Project.findComposeResourcesDirFor(path: Path): ComposeResourcesDir? = service<ComposeResourcesManager>().findComposeResourcesDirFor(path)


/** Return a map of all the Compose resources directories present in the given [Module] */
internal val Module.composeResourcesDirsByName: Map<String, ComposeResourcesDir>
  get() = getModuleNameForComposeResourcesTask()?.let { moduleName ->
    project.service<ComposeResourcesManager>().composeResourcesByModulePath[moduleName]?.directoriesBySourceSetName.orEmpty()
  } ?: emptyMap()

/** Return the nameOfResClass for the given [Module] */
private fun Module.getNameOfResClass(): String? =
  getModuleNameForComposeResourcesTask()?.let { moduleName ->
    project.service<ComposeResourcesManager>().composeResourcesByModulePath[moduleName]?.nameOfResClass
  }

/** Return the packageOfResClass for the given [Module] */
private fun Module.getPackageOfResClass(): String? =
  getModuleNameForComposeResourcesTask()?.let { moduleName ->
    project.service<ComposeResourcesManager>().composeResourcesByModulePath[moduleName]?.packageOfResClass
  }

private fun Module.getResourcePackageName(packageOfResClass: String): String? {
  val sourceSetName = getSourceSetNameFromComposeResourcesDir()
  val composeResourcesDir = composeResourcesDirsByName[sourceSetName]
                            ?: composeResourcesDirsByName["commonMain"]
                            ?: return null
  return composeResourcesDir.getResourcePackageName(project, packageOfResClass)
}

internal val KtDotQualifiedExpression.isComposeResClass: Boolean
  get() {
    val module = module ?: return false

    val resDotAccessorWithoutType = receiverExpression
    val resReference = (resDotAccessorWithoutType as? KtNameReferenceExpression)
                       ?: (resDotAccessorWithoutType as? KtDotQualifiedExpression)?.selectorExpression as? KtNameReferenceExpression
                       ?: return false

    val nameOfResClass = module.getNameOfResClass() ?: return false
    if (resReference.getReferencedName() != nameOfResClass) return false
    val packageOfResClass = module.getPackageOfResClass() ?: return false
    val resourcePackageName = module.getResourcePackageName(packageOfResClass) ?: return false
    val expectedFqn = FqName(resourcePackageName).child(Name.identifier(nameOfResClass))

    val resolvedFqn = resReference.mainReference.resolve()?.kotlinFqName

    return resolvedFqn == expectedFqn
  }

internal data class ComposeResourcesDir(
  val moduleName: String,
  val sourceSetName: String,
  val directoryPath: Path,
  val projectGroupName: String,
  val isCustom: Boolean = false,
)

internal data class ComposeResources(
  val moduleName: String,
  val directoriesBySourceSetName: Map<String, ComposeResourcesDir>,
  val isPublicResClass: Boolean,
  val nameOfResClass: String,
  val packageOfResClass: String
)

private val log by lazy { fileLogger() }
