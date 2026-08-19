// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.branch.GitBranchUiHandler.CheckoutInOtherWorktreeDecision
import git4idea.i18n.GitBundle

/**
 * Builds and shows the "branch is already checked out in another worktree" confirmation dialog, shared by
 * every flow that can hit this conflict: checking out a branch ([GitBranchUiHandlerImpl]) and creating a new
 * worktree for a branch (`GitCreateWorkingTreeService`). Those flows run on different threading models
 * (synchronous `invokeAndWait` vs. suspend/`Dispatchers.UiWithModelAccess`), so this stays a plain EDT-bound
 * function rather than living on [GitBranchUiHandler] itself; each caller wraps it in whatever's appropriate
 * for its own context.
 */
internal object GitCheckoutInOtherWorktreeDialogs {

  enum class ButtonSet {
    /** Checkout Anyway / Open Existing Worktree / Cancel. */
    PROCEED_OPEN_EXISTING_OR_CANCEL,

    /** Checkout Anyway / Open Existing Worktree — used before the git command has even been attempted. */
    PROCEED_OR_OPEN_EXISTING,

    /** Checkout Anyway / Cancel — "open existing" was already offered earlier in the same user flow. */
    PROCEED_OR_CANCEL,
  }

  @JvmStatic
  @RequiresEdt
  fun buildAndShow(
    project: Project,
    branchName: String,
    worktreePath: String?,
    proceedButtonText: @NlsContexts.Button String,
    buttonSet: ButtonSet,
  ): CheckoutInOtherWorktreeDecision {
    val title = GitBundle.message("branch.ui.handler.checkout.branch.in.other.worktree.title")
    val message = checkoutInOtherWorktreeMessage(branchName, worktreePath)
    val openExistingText = GitBundle.message("working.tree.dialog.quick.create.open.existing")

    return when (buttonSet) {
      ButtonSet.PROCEED_OPEN_EXISTING_OR_CANCEL -> {
        val exitCode = MessageDialogBuilder.yesNoCancel(title, message)
          .yesText(proceedButtonText)
          .noText(openExistingText)
          .cancelText(CommonBundle.getCancelButtonText())
          .icon(Messages.getQuestionIcon())
          .show(project)
        when (exitCode) {
          Messages.YES -> CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY
          Messages.NO -> CheckoutInOtherWorktreeDecision.OPEN_EXISTING_WORKTREE
          else -> CheckoutInOtherWorktreeDecision.CANCEL
        }
      }
      ButtonSet.PROCEED_OR_OPEN_EXISTING -> {
        val proceed = MessageDialogBuilder.yesNo(title, message)
          .yesText(proceedButtonText)
          .noText(openExistingText)
          .ask(project)
        if (proceed) CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY else CheckoutInOtherWorktreeDecision.OPEN_EXISTING_WORKTREE
      }
      ButtonSet.PROCEED_OR_CANCEL -> {
        val proceed = MessageDialogBuilder.yesNo(title, message)
          .yesText(proceedButtonText)
          .noText(CommonBundle.getCancelButtonText())
          .ask(project)
        if (proceed) CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY else CheckoutInOtherWorktreeDecision.CANCEL
      }
    }
  }

  private fun checkoutInOtherWorktreeMessage(branchName: String, worktreePath: String?): @NlsContexts.DialogMessage String =
    if (worktreePath != null)
      GitBundle.message("branch.ui.handler.checkout.branch.in.other.worktree.message.with.path", branchName, getPresentablePath(worktreePath))
    else
      GitBundle.message("branch.ui.handler.checkout.branch.in.other.worktree.message", branchName)
}
