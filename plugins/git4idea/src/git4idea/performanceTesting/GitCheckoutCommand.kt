// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.ide.DataManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.wm.IdeFocusManager
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBrancher
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class GitCheckoutCommand(text: String, line: Int) : AbstractCommand(text, line, true) {
  companion object {
    const val PREFIX = "${CMD_PREFIX}gitCheckout"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val branchName = extractCommandArgument(PREFIX).replace("\"".toRegex(), "")
    val brancher: GitBrancher = GitBrancher.getInstance(context.project)
    val focusedComponent = IdeFocusManager.findInstance().focusOwner
    val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
    val gitRepository = GitBranchUtil.guessRepositoryForOperation(context.project, dataContext)
    brancher.checkoutNewBranchStartingFrom(branchName, branchName, true, mutableListOf(gitRepository), Runnable { actionCallback.setDone() })
    return actionCallback.toPromise();
  }
}