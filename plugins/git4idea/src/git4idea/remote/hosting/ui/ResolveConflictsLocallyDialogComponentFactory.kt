// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.openapi.ui.Messages
import git4idea.DialogManager
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
object ResolveConflictsLocallyDialogComponentFactory {
  private fun ResolveConflictsMethod.toMessage(): @Nls String = when (this) {
    ResolveConflictsMethod.REBASE -> GitBundle.message("rebasing.merge.commits.button.rebase")
    ResolveConflictsMethod.MERGE -> GitBundle.message("rebasing.merge.commits.button.merge")
  }

  /**
   * Shows a dialog to choose between rebasing or merging a branch with conflicts known on the remote.
   */
  fun showBranchUpdateDialog(headRefName: String, baseRefName: String): ResolveConflictsMethod? {
    val exitCode = DialogManager.showMessage(
      GitBundle.message("dialog.message.update.branch", headRefName, baseRefName),
      GitBundle.message("dialog.title.update.branch", headRefName),
      ResolveConflictsMethod.entries.map { it.toMessage() }.toTypedArray() + GitBundle.message("rebasing.merge.commits.button.cancel"),
      ResolveConflictsMethod.entries.indexOf(ResolveConflictsMethod.REBASE),
      ResolveConflictsMethod.entries.indexOf(ResolveConflictsMethod.REBASE),
      Messages.getQuestionIcon(), null
    )
    return ResolveConflictsMethod.entries.getOrNull(exitCode)
  }
}