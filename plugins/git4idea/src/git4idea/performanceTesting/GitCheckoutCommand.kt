// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.ide.DataManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.wm.IdeFocusManager
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBrancher
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

/**
 *   Command switches to another branch.
 *   Call from the project dir git checkout command in command line.
 *   Syntax: %gitCheckout <branch>
 *   Example: %gitCheckout master
 */
class GitCheckoutCommand(text: String, line: Int) : AbstractCommand(text, line, true) {
  companion object {
    const val PREFIX = "${CMD_PREFIX}gitCheckout"
    private val LOG = Logger.getInstance(GitCheckoutCommand::class.java)
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    LOG.info("GitCheckoutCommand starts its execution")
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val branchName = extractCommandArgument(PREFIX).replace("\"".toRegex(), "")
    val brancher: GitBrancher = GitBrancher.getInstance(context.project)
    val focusedComponent = IdeFocusManager.findInstance().focusOwner
    val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
    val gitRepository = GitBranchUtil.guessRepositoryForOperation(context.project, dataContext)
    brancher.checkoutNewBranchStartingFrom(branchName, branchName, true, mutableListOf(gitRepository),
                                           Runnable { actionCallback.setDone() })
    return actionCallback.toPromise()
  }

  private fun hardReset(context: PlaybackContext): GitCommandResult {
    val handler = GitLineHandler(
      context.project,
      (context.project.guessProjectDir() ?: throw RuntimeException("Can't find root project dir")),
      GitCommand.RESET
    )
    handler.addParameters("--hard")
    handler.endOptions()
    val result: GitCommandResult = Git.getInstance().runCommand(handler)
    if (!result.success()) {
      throw RuntimeException("Can't reset changes: ${result.errorOutputAsJoinedString}")
    }
    return result
  }

}