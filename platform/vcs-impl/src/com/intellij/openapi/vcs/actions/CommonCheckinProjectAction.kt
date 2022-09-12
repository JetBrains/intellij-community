// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.commit.getAllCommittableRoots
import com.intellij.openapi.vcs.actions.commit.isCommonCommitActionHidden

@Deprecated("Use [com.intellij.openapi.vcs.actions.commit.CheckinActionUtil] instead")
open class CommonCheckinProjectAction : AbstractCommonCheckinAction() {
  override fun update(e: AnActionEvent) {
    if (e.isCommonCommitActionHidden()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

  override fun getRoots(dataContext: VcsContext): Array<FilePath> =
    getAllCommittableRoots(dataContext.project!!).toTypedArray()

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean = true
}
