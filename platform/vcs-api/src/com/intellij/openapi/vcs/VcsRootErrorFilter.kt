// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface VcsRootErrorFilter {
  fun filterErrors(project: Project, errors: Collection<VcsRootError>): Collection<VcsRootError>

  fun isIgnoredDirectory(project: Project, directory: String): Boolean = false

  companion object {
    @JvmStatic
    fun filter(project: Project, errors: Collection<VcsRootError>): Collection<VcsRootError> {
      var result = errors
      filters().forEach {
        result = it.filterErrors(project, result)
      }
      return result
    }

    @JvmStatic
    fun isIgnored(project: Project, directory: String): Boolean {
      return filters().any { it.isIgnoredDirectory(project, directory) }
    }

    private fun filters(): List<VcsRootErrorFilter> = ExtensionPointName.create<VcsRootErrorFilter>("com.intellij.vcsRootErrorFilter").extensionList
  }
}