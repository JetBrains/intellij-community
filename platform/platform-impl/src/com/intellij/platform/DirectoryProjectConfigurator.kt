// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile

/**
 * Configures various subsystems (facets etc.) when a user opens a directory with code but without `.idea` subdirectory.
 *
 * Example: to support some framework, you need to enable and configure a facet. A user opens a directory with code for the first time.
 * This class scans the code and detects the framework heuristically. It then configures facet without user action.
 */
interface DirectoryProjectConfigurator {
  /**
   * @return if code must be called or EDT or not.
   * If [.configureProject] is slow (heavy computations, network access etc) return "false" here.
   */
  val isEdtRequired: Boolean
    get() = true

  /**
   * @param isProjectCreatedWithWizard if true then new project created with wizard, existing folder opened otherwise
   */
  fun configureProject(project: Project,
                       baseDir: VirtualFile,
                       moduleRef: Ref<Module>,
                       isProjectCreatedWithWizard: Boolean)

  abstract class AsyncDirectoryProjectConfigurator : DirectoryProjectConfigurator {
    final override val isEdtRequired: Boolean
      get() = false

    final override fun configureProject(project: Project,
                                        baseDir: VirtualFile,
                                        moduleRef: Ref<Module>,
                                        isProjectCreatedWithWizard: Boolean) {
      throw IllegalStateException("Call configure instead.")
    }

    abstract suspend fun configure(
      project: Project,
      baseDir: VirtualFile,
      moduleRef: Ref<Module>,
      isProjectCreatedWithWizard: Boolean
    )
  }
}