// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.registry.Registry

internal object GitWorkingTreesUtil {
  internal const val TOOLWINDOW_TAB_ID: String = "Working Trees"

  fun isWorkingTreesFeatureEnabled(): Boolean {
    return Registry.`is`("git.enable.working.trees.feature", false)
  }
}