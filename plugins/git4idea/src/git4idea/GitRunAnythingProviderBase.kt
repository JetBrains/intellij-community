// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.ide.actions.runAnything.RunAnythingUtil
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import git4idea.branch.GitBranchUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

abstract class GitRunAnythingProviderBase : RunAnythingProviderBase<String>() {

  abstract override fun getHelpCommand(): String

  override fun execute(dataContext: DataContext, value: String) {
    val project = RunAnythingUtil.fetchProject(dataContext)

    val split = value.split(" ")
    val command = getGitCommandInstance(split[1])
    val params = split.subList(2, split.size)
    val all = split[0] == "gitall"

    object: Task.Backgroundable(project, value, true) {
      override fun run(indicator: ProgressIndicator) {
        doExecute(all, project, command, params)
      }
    }.queue()
  }

  private fun doExecute(all: Boolean, project: Project, command: GitCommand, params: List<String>) {
    if (all) {
      GitRepositoryManager.getInstance(project).repositories.forEach {
        runCommand(project, it, command, params)
      }
    }
    else {
      val repository = GitBranchUtil.getCurrentRepository(project)!!
      runCommand(project, repository, command, params)
    }
  }

  private fun runCommand(project: Project, repository: GitRepository, command: GitCommand, params: List<String>) {
    val result = Git.getInstance().runCommand {
      val handler = GitLineHandler(project, repository.root, command)
      handler.addParameters(params)
      handler.setSilent(false)
      handler.setStderrSuppressed(false)
      handler.setStdoutSuppressed(false)
      handler
    }
    repository.update()

    val notification: Notification
    if (result.success()) {
      notification = Notification(VcsNotifier.STANDARD_NOTIFICATION.displayId, "", "git ${command.name()} succeeded",
                                  NotificationType.INFORMATION)
    }
    else {
      notification = Notification(VcsNotifier.STANDARD_NOTIFICATION.displayId, "", "git ${command.name()} failed", NotificationType.ERROR)
    }

    val action = NotificationAction.createSimple("View Output") {
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS).activate {
        val contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).contentManager!!
        val console = contentManager.findContent(VcsBundle.message("vcs.console.toolwindow.display.name"))!!
        contentManager.setSelectedContent(console, true, true)
      }
    }
    notification.addAction(action)
    VcsNotifier.getInstance(project).notify(notification)
  }

  private fun getGitCommandInstance(commandName: String): GitCommand {
    val fieldName = commandName.toUpperCase().replace('-', '_')
    return GitCommand::class.java.getDeclaredField(fieldName).get(null) as GitCommand
  }


  override fun getHelpCommandPlaceholder(): String {
    return "$helpCommand <command> <parameters>"
  }

  override fun getCommand(value: String): String {
    return value
  }

  override fun getValues(dataContext: DataContext, pattern: String): Collection<String> {
    if (!pattern.startsWith(helpCommand)) {
      return emptyList()
    }

    val options = getAllOptions().filter { it.startsWith(pattern) }
    return options + pattern // even if we don't provide an option in completion, we still can execute it via the common Git runner
  }

  private fun getAllOptions() : List<String> {
    val resetCommand = "reset"
    val resetOptions = listOf("--hard", "--keep", "--soft", "--mixed")
    val resetParams = "origin/master"

    val res = mutableListOf<String>()
    for (option in resetOptions) {
      res.add("git $resetCommand $option $resetParams")
      res.add("gitall $resetCommand $option $resetParams")
    }
    return res
  }

  override fun getCompletionGroupTitle(): String {
    return "Git Commands"
  }

}

