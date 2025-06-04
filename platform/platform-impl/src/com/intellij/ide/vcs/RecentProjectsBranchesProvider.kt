// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.vcs

import com.intellij.openapi.extensions.ExtensionPointName

interface RecentProjectsBranchesProvider {
  fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String?

  companion object {
    val EP_NAME: ExtensionPointName<RecentProjectsBranchesProvider> = ExtensionPointName.create("com.intellij.recentProjectsBranchesProvider")

    fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String? {
      EP_NAME.extensionList.forEach { provider ->
        val branch = provider.getCurrentBranch(projectPath, nameIsDistinct)
        if (branch != null) return branch
      }

      return null
    }
  }
}