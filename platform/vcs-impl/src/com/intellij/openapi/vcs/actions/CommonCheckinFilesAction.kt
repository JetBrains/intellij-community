// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.commit.CheckinActionUtil
import com.intellij.openapi.vcs.actions.commit.CommonCheckinFilesAction
import com.intellij.openapi.vcs.changes.LocalChangeList
import kotlin.streams.asSequence

private fun VcsContext.getRoots(): Sequence<FilePath> = selectedFilePathsStream.asSequence()

@Deprecated("Use [com.intellij.openapi.vcs.actions.commit.CheckinActionUtil] instead")
open class CommonCheckinFilesAction : AbstractCommonCheckinAction() {
  override fun getActionName(dataContext: VcsContext): String {
    val project = dataContext.project!!
    val roots = dataContext.getRoots().take(2).toList()
    return CommonCheckinFilesAction.getActionName(project, roots)
  }

  override fun getInitiallySelectedChangeList(context: VcsContext, project: Project): LocalChangeList {
    return CheckinActionUtil.getInitiallySelectedChangeListFor(project, getRoots(context).asList())
  }

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean =
    dataContext.getRoots().any { isApplicableRoot(it, dataContext) }

  protected open fun isApplicableRoot(path: FilePath, dataContext: VcsContext): Boolean {
    return CommonCheckinFilesAction.isActionEnabled(dataContext.project!!, path)
  }

  override fun getRoots(dataContext: VcsContext): Array<FilePath> = dataContext.selectedFilePaths

  override fun isForceUpdateCommitStateFromContext(): Boolean = true
}
