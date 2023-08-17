// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.wm.IdeFocusManager
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBrancher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
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
  }

  // For the simplified com.intellij.driver call
  @Suppress("UNUSED")
  constructor() : this(text = "", line = 0)

  fun checkout(branchName: String): Boolean {
    val promise: Promise<Any?> = runBlocking(Dispatchers.EDT) {
      checkout(project = ProjectManager.getInstance().openProjects.first(), branchName = branchName)
    }

    runBlocking(Dispatchers.IO) { promise.await() }

    return promise.isSucceeded
  }

  fun checkout(project: Project,
               branchName: String): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()

    try {
      val brancher: GitBrancher = GitBrancher.getInstance(project)
      val focusedComponent = IdeFocusManager.findInstance().focusOwner
      val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
      val gitRepository = GitBranchUtil.guessRepositoryForOperation(project, dataContext)
      brancher.checkoutNewBranchStartingFrom(branchName, branchName, true, mutableListOf(gitRepository),
                                             Runnable { actionCallback.setDone() })
    }
    catch (e: Throwable) {
      actionCallback.reject(e.message)
    }

    return actionCallback.toPromise()
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val branchName = extractCommandArgument(PREFIX).replace("\"".toRegex(), "")
    return checkout(project = context.project, branchName = branchName)
  }

}