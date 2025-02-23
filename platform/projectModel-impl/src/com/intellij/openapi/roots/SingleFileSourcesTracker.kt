// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Inside IntelliJ platform, a source root is usually referred to as a source directory containing source code.
 * This assumption may break in the case when individual source code files are registered as source roots (single file sources)
 * and can cause issues when resolving references, etc.
 *
 * This interface provides a means to recognize single file sources to facilitate such scenarios.
 *
 * The true implementation for this interface is covered from intellij-bsp plugin. Therefore, do not implement this interface.
 */
interface SingleFileSourcesTracker {
  fun isSingleFileSource(file: VirtualFile): Boolean

  /**
   * Checks if the source directory is in the module.
   *
   * In the context of single file source, a source directory is defined as the parent directory that contains the single file source.
   */
  fun isSourceDirectoryInModule(dir: VirtualFile, module: Module): Boolean

  /**
   * Checks if the file is a valid single file source and returns its corresponding source directory.
   *
   * In the context of single file source, a source directory is defined as the parent directory that contains the single file source.
   */
  fun getSourceDirectoryIfExists(file: VirtualFile): VirtualFile?

  /**
   * Returns the package prefix from the corresponding [SourceRootEntity] for the valid single file source
   */
  fun getPackageNameForSingleFileSource(file: VirtualFile): String?

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SingleFileSourcesTracker = project.service()
  }
}

/**
 * This is a dummy implementation for the [SingleFileSourcesTracker] that makes sure no changes in behaviour are introduced to the platform
 */
internal class DefaultSingleFileSourcesTracker : SingleFileSourcesTracker {
  override fun isSingleFileSource(file: VirtualFile): Boolean = false
  override fun isSourceDirectoryInModule(dir: VirtualFile, module: Module): Boolean = false
  override fun getSourceDirectoryIfExists(file: VirtualFile): VirtualFile? = null
  override fun getPackageNameForSingleFileSource(file: VirtualFile): String? = null
}
