// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import git4idea.index.isStagingAreaAvailable

class GitIndexActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    val project = context.getData(CommonDataKeys.PROJECT)
    if (project == null || !isStagingAreaAvailable(project)) return emptyList()

    return actions.filterIsInstance<GitResetAction>()
  }
}