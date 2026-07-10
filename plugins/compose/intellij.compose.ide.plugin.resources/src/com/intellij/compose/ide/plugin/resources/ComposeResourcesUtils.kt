// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.gradle.GradleComposeResourcesDir
import com.intellij.compose.ide.plugin.resources.gradle.GradleComposeResourcesManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findVirtualFileOrDirectory
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

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
 * Retrieves the directory for Compose resources specific to a module's source set.
 * If the directory for the determined source set name does not exist, it defaults to 'commonMain'.
 *
 * @return the VirtualFile representing the directory for Compose resources, or null if not found
 */
internal fun Module.getComposeResourcesDir(): VirtualFile? {
  val composeData = ComposeResourcesDataProvider.findProviderForProject(project)
                      ?.getComposeDataForModule(this) ?: return null
  return composeData.directoryPath.findVirtualFileOrDirectory() ?: composeData.commonResourcesPath?.findVirtualFileOrDirectory()
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

/** Return the nameOfResClass for the given [Module] */
private fun Module.getNameOfResClass(): String? =
  ComposeResourcesDataProvider.findProviderForProject(project)
    ?.getComposeDataForModule(this)
    ?.nameOfResClass

/** Return the packageOfResClass for the given [Module] */
private fun Module.getPackageOfResClass(): String? =
  ComposeResourcesDataProvider.findProviderForProject(project)
    ?.getComposeDataForModule(this)
    ?.packageOfResClass

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
    val expectedFqn = FqName(packageOfResClass).child(Name.identifier(nameOfResClass))

    val resolvedFqn = resReference.mainReference.resolve()?.kotlinFqName

    return resolvedFqn == expectedFqn
  }

/** Return a list of all the Compose resources directories present in the given [Project] */
internal fun Project.getAllComposeResourcesDirs(): List<GradleComposeResourcesDir> =
  service<GradleComposeResourcesManager>().composeResourcesByModulePath.flatMap { it.value.directoriesBySourceSetName.values }
