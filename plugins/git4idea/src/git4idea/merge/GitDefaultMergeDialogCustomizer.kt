// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author yole
 */
class GitDefaultMergeDialogCustomizer(
  private val project: Project,
  private val gitMergeProvider: GitMergeProvider
) : MergeDialogCustomizer() {
  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String? {
    val branchBeingMerged = gitMergeProvider.resolveMergeBranch(file) ?: return super.getRightPanelTitle(file, revisionNumber)
    return "Changes from branch $branchBeingMerged"
  }
}
