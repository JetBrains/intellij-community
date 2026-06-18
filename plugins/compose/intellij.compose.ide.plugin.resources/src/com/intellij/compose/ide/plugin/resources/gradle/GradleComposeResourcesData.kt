// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.gradle

import com.intellij.compose.ide.plugin.resources.ComposeResourcesData
import com.intellij.compose.ide.plugin.resources.psi.asUnderscoredIdentifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.nio.file.Path

private const val RESOURCES_ACCESSORS_SUFFIX = "ResourceAccessors"
private const val RESOURCES_COLLECTORS_SUFFIX = "ResourceCollectors"

internal class GradleComposeResourcesData(
  private val project: Project,
  private val config: GradleComposeResources,
  private val composeResourcesDir: GradleComposeResourcesDir,
) : ComposeResourcesData {
  override val isPublicResClass: Boolean get() = config.isPublicResClass
  override val nameOfResClass: String get() = config.nameOfResClass
  override val packageOfResClass: String get() = getResourcePackageName()
  override val directoryPath: Path get() = composeResourcesDir.directoryPath
  override val commonResourcesPath: Path? get() = config.directoriesBySourceSetName["commonMain"]?.directoryPath
  override val isCustomDirectory: Boolean get() = composeResourcesDir.isCustom
  override val accessorsQualifier: String get() = composeResourcesDir.sourceSetName

  @get:RequiresReadLock
  override val accessorsDirectory: VirtualFile?
    // android's ResourceAccessors source root isn't created by default -- in that case we temporarily store them in the ResourceCollectors one
    get() = findResourcesDir(RESOURCES_ACCESSORS_SUFFIX) ?: findResourcesDir(RESOURCES_COLLECTORS_SUFFIX)

  private fun getResourcePackageName(): String = config.packageOfResClass.ifEmpty {
    val groupName = composeResourcesDir.projectGroupName.ifEmpty { project.name }.lowercase().asUnderscoredIdentifier()
    val moduleName = config.moduleName.lowercase().asUnderscoredIdentifier()
    val id = if (groupName.isNotEmpty()) "$groupName.$moduleName" else moduleName
    "$id.generated.resources"
  }

  private fun findResourcesDir(suffix: String): VirtualFile? {
    var foundFile: VirtualFile? = null
    FilenameIndex.processFilesByName(
      /* name = */ composeResourcesDir.sourceSetName + suffix,
      /* caseSensitively = */ true,
      /* scope = */ GlobalSearchScope.allScope(project)
    ) {
      if (it.path.contains(config.moduleName)) {
        foundFile = it
        false
      }
      else {
        true
      }
    }
    return foundFile
  }
}