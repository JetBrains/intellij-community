// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for providing custom data for Compose resources based on the build system.
 *
 * Note: this extension point might be replaced with the WSM entities in the future that a build system can contribute on sync
 * to simplify the implementation.
 */
@ApiStatus.Internal
interface ComposeResourcesDataProvider {
  companion object {
    val EP_NAME: ExtensionPointName<ComposeResourcesDataProvider> =
      ExtensionPointName.create("com.intellij.compose.ide.plugin.resources.dataProvider")

    /**
     * Finds the first provider applicable to [project].
     *
     * The order is an EP declaration order.
     */
    fun findProviderForProject(project: Project): ComposeResourcesDataProvider? =
      EP_NAME.findFirstSafe { it.isApplicable(project) }
  }

  /**
   * Returns `true` when this provider can serve the given [project].
   *
   * Compose resources utilities select the first applicable provider and use only that provider for the project.
   */
  fun isApplicable(project: Project): Boolean

  /**
   * Returns the Compose resources data associated with the given [module].
   *
   * [Module] corresponds to the Gradle source set or Kotlin fragment.
   */
  fun getComposeDataForModule(module: Module): ComposeResourcesData?

  /**
   * Returns the Compose resources data associated with the given resource [file] (e.g., drawable, font, etc.).
   */
  fun getComposeDataForResourceFile(file: PsiFile): ComposeResourcesData?

  /**
   * Returns the Compose resources data associated with the given resource [folder].
   *
   * Should support both nested folders (such as drawable, font, etc.) and resources root.
   */
  fun getComposeDataForResourceFolder(folder: PsiDirectory): ComposeResourcesData?
}