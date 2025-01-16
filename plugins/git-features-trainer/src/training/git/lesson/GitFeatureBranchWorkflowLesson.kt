// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.CommonBundle
import com.intellij.dvcs.push.ui.PushLog
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.idea.ActionsBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.ui.popup.PopupFactoryImpl
import git4idea.GitLocalBranch
import git4idea.actions.ref.GitCheckoutAction
import git4idea.actions.branch.GitCheckoutWithRebaseAction
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchPopupActions
import org.jetbrains.annotations.Nls
import training.dsl.*
import training.git.GitFeaturesTrainerIcons
import training.git.GitLessonsBundle
import training.git.GitLessonsUtil.clickTreeRow
import training.git.GitLessonsUtil.highlightLatestCommitsFromBranch
import training.git.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import training.git.GitLessonsUtil.highlightToolWindowStripe
import training.git.GitLessonsUtil.resetGitLogWindow
import training.git.GitLessonsUtil.restoreByUiAndBackgroundTask
import training.git.GitLessonsUtil.showWarningIfGitWindowClosed
import training.git.GitLessonsUtil.triggerOnCheckout
import training.git.GitLessonsUtil.triggerOnNotification
import training.git.GitProjectUtil
import java.io.File
import javax.swing.JButton
import javax.swing.JDialog

class GitFeatureBranchWorkflowLesson : GitLesson("Git.BasicWorkflow", GitLessonsBundle.message("git.feature.branch.lesson.name")) {
  override val sampleFilePath = "git/simple_cat.yml"
  private val remoteName = "origin"
  override val branchName = "feature"
  private val main = "main"

  private val fileToCommitName = "sphinx_cat.yml"
  private val committerName = "Johnny Catsville"
  private val committerEmail = "johnny.catsville@meow.com"
  private val commitMessage = "Add new fact about sphinx's behaviour"

  private val fileAddition = """
    |
    |    - steal:
    |        condition: food was left unattended
    |        action:
    |          - steal a piece of food and hide""".trimMargin()

  private lateinit var repository: GitRepository

  private val illustration1 by lazy { GitFeaturesTrainerIcons.GitFeatureBranchIllustration01 }
  private val illustration2 by lazy { GitFeaturesTrainerIcons.GitFeatureBranchIllustration02 }
  private val illustration3 by lazy { GitFeaturesTrainerIcons.GitFeatureBranchIllustration03 }

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 60)

  override val lessonContent: LessonContext.() -> Unit = {
    highlightToolWindowStripe(ToolWindowId.VCS)

    task("ActivateVersionControlToolWindow") {
      val gitWindowName = GitBundle.message("git4idea.vcs.name")
      text(GitLessonsBundle.message("git.feature.branch.introduction.1", strong(branchName),
                                    strong(main), action(it), strong(gitWindowName)))
      text(GitLessonsBundle.message("git.open.tool.window.balloon", strong(gitWindowName)),
           LearningBalloonConfig(Balloon.Position.atRight, width = 0))
      illustration(illustration1)
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
      test { actions(it) }
    }

    resetGitLogWindow()

    prepareRuntimeTask {
      repository = GitRepositoryManager.getInstance(project).repositories.first()
    }

    task {
      highlightLatestCommitsFromBranch(branchName, sequenceLength = 2)
    }

    task {
      val introText = GitLessonsBundle.message("git.feature.branch.introduction.2", strong(main))
      val gotItText = GitLessonsBundle.message("git.feature.branch.introduction.got.it", strong(branchName))
      val checkText = GitLessonsBundle.message("git.feature.branch.introduction.check", strong(main))
      text("$introText $checkText")
      gotItStep(Balloon.Position.above, width = 0, "$gotItText $checkText", duplicateMessage = false)
      showWarningIfGitWindowClosed()
    }

    task {
      triggerAndBorderHighlight().component { ui: ToolbarComboButton -> ui.text == branchName }
    }

    lateinit var firstShowBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      firstShowBranchesTaskId = taskId
      val vcsWidgetName = GitBundle.message("action.main.toolbar.git.Branches.text")
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.1", strong(main), action(it), strong(vcsWidgetName)))
      openVcsWidgetOnClick(vcsWidgetName, main)
    }

    task {
      val checkoutItemText = GitBundle.message("branches.checkout")
      text(GitLessonsBundle.message("git.feature.branch.checkout.branch", strong(main), strong(checkoutItemText)))
      triggerAndBorderHighlight { clearPreviousHighlights = false }.listItem { item ->
        (item as? PopupFactoryImpl.ActionItem)?.action is GitCheckoutAction
      }
      triggerOnCheckout { newBranch -> newBranch == main }
      restoreByUiAndBackgroundTask(GitBundle.message("branch.checking.out.process", main),
                                   delayMillis = 2 * defaultRestoreDelay, restoreId = firstShowBranchesTaskId)
      test {
        ideFrame {
          clickTreeRow { item -> (item as? GitLocalBranch)?.name == main }
          jList(checkoutItemText).clickItem(checkoutItemText)
        }
      }
    }

    prepareRuntimeTask {
      val showSettingsOption = ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE)
      showSettingsOption.value = true  // needed to show update project dialog
    }

    task("Vcs.UpdateProject") {
      val updateProjectDialogTitle = VcsBundle.message("action.display.name.update.scope", VcsBundle.message("update.project.scope.name"))
      text(GitLessonsBundle.message("git.feature.branch.open.update.dialog", strong(main), action(it),
                                    strong(VcsBundle.message("action.display.name.update.scope",
                                                             VcsBundle.message("update.project.scope.name")))))
      triggerUI().component { ui: JDialog ->
        ui.title?.contains(updateProjectDialogTitle) == true
      }
      showWarningIfGitWindowClosed()
      test { actions(it) }
    }

    task {
      text(GitLessonsBundle.message("git.feature.branch.confirm.update", strong(CommonBundle.getOkButtonText())))
      triggerAndBorderHighlight().component { ui: JButton ->
        ui.text == CommonBundle.getOkButtonText()
      }
      highlightSubsequentCommitsInGitLog { commit ->
        commit.fullMessage == commitMessage
      }
      restoreByUiAndBackgroundTask(ActionsBundle.actionText("Vcs.UpdateProject").dropMnemonic(), delayMillis = defaultRestoreDelay)
      test(waitEditorToBeReady = false) {
        ideFrame { button(CommonBundle.getOkButtonText()).click() }
      }
    }

    task("Git.Branches") {
      text(GitLessonsBundle.message("git.feature.branch.new.commits.explanation", strong(main)))
      illustration(illustration2)
      gotItStep(Balloon.Position.above, width = 0,
                GitLessonsBundle.message("git.feature.branch.new.commits.got.it", strong(main)),
                duplicateMessage = false)
    }

    task {
      triggerAndBorderHighlight().component { ui: ToolbarComboButton -> ui.text == main }
    }

    lateinit var secondShowBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      secondShowBranchesTaskId = taskId
      val vcsWidgetName = GitBundle.message("action.main.toolbar.git.Branches.text")
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.2", strong(main),
                                    strong(GitBundle.message("rebase.git.operation.name")),
                                    action(it), strong(vcsWidgetName)))
      illustration(illustration3)
      openVcsWidgetOnClick(vcsWidgetName, branchName)
    }

    task {
      val repositories = GitRepositoryManager.getInstance(project).repositories
      val checkoutAndRebaseText = GitBundle.message("branches.checkout.and.rebase.onto.branch",
                                                    GitBranchPopupActions.getCurrentBranchTruncatedPresentation(project, repositories))
      text(GitLessonsBundle.message("git.feature.branch.checkout.and.rebase", strong(branchName), strong(checkoutAndRebaseText)))
      triggerAndBorderHighlight { clearPreviousHighlights = false }.listItem { item ->
        (item as? PopupFactoryImpl.ActionItem)?.action is GitCheckoutWithRebaseAction
      }
      triggerOnNotification { notification -> notification.title == GitBundle.message("rebase.notification.successful.title") }
      restoreByUiAndBackgroundTask(GitBundle.message("branch.rebasing.process", branchName),
                                   delayMillis = defaultRestoreDelay, secondShowBranchesTaskId)
      test {
        ideFrame {
          clickTreeRow { item -> (item as? GitLocalBranch)?.name == branchName }
          jList(checkoutAndRebaseText).clickItem(checkoutAndRebaseText)
        }
      }
    }

    task("Vcs.Push") {
      text(GitLessonsBundle.message("git.feature.branch.open.push.dialog", strong(branchName),
                                    action(it), strong(DvcsBundle.message("action.push").dropMnemonic())))
      triggerUI().component { _: PushLog -> true }
      test { actions(it) }
    }

    val forcePushText = DvcsBundle.message("action.force.push").dropMnemonic()
    task {
      text(GitLessonsBundle.message("git.feature.branch.choose.force.push",
                                    strong(branchName), strong(forcePushText), strong(DvcsBundle.message("action.push").dropMnemonic())))
      triggerAndBorderHighlight().component { _: BasicOptionButtonUI.ArrowButton -> true }
      val forcePushDialogTitle = DvcsBundle.message("force.push.dialog.title")
      triggerUI().component { ui: JDialog ->
        ui.title?.contains(forcePushDialogTitle) == true
      }
      restoreByUi()
      test(waitEditorToBeReady = false) {
        ideFrame {
          button { _: BasicOptionButtonUI.ArrowButton -> true }.click()
          jList(forcePushText).clickItem(forcePushText)
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.feature.branch.confirm.force.push", strong(forcePushText)))
      text(GitLessonsBundle.message("git.feature.branch.force.push.tip", strong(forcePushText)))
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreByUiAndBackgroundTask(DvcsBundle.message("push.process.pushing"), delayMillis = defaultRestoreDelay)
      test(waitEditorToBeReady = false) {
        ideFrame { button(forcePushText).click() }
      }
    }
  }

  private fun TaskContext.openVcsWidgetOnClick(vcsWidgetName: @Nls String, branchName: String) {
    text(GitLessonsBundle.message("git.click.to.open", strong(vcsWidgetName)),
         LearningBalloonConfig(Balloon.Position.below, width = 0))
    triggerAndBorderHighlight().treeItem { _, path ->
      (path.lastPathComponent as? GitLocalBranch)?.name == branchName
    }
    test {
      val widget = previous.ui ?: error("Not found VCS widget")
      ideFrame { jComponent(widget).click() }
    }
  }

  override fun prepare(project: Project) {
    super.prepare(project)
    val remoteProjectRoot = GitProjectUtil.createRemoteProject(remoteName, project)
    modifyRemoteProject(remoteProjectRoot)
  }

  private fun modifyRemoteProject(remoteProjectRoot: File) {
    val files = mutableListOf<File>()
    FileUtil.processFilesRecursively(remoteProjectRoot, files::add)
    val fileToCommit = files.find { it.name == fileToCommitName }
                       ?: error("Failed to find file $fileToCommitName to modify in $remoteProjectRoot")
    gitChange(remoteProjectRoot, "user.name", committerName)
    gitChange(remoteProjectRoot, "user.email", committerEmail)
    createOneFileCommit(remoteProjectRoot, fileToCommit)
  }

  private fun gitChange(root: File, param: String, value: String) {
    val handler = GitLineHandler(null, root, GitCommand.CONFIG)
    handler.addParameters(param, value)
    runGitCommandSynchronously(handler)
  }

  private fun createOneFileCommit(root: File, fileToCommit: File) {
    fileToCommit.appendText(fileAddition)
    val handler = GitLineHandler(null, root, GitCommand.COMMIT)
    handler.addParameters("-a")
    handler.addParameters("-m", commitMessage)
    handler.endOptions()
    runGitCommandSynchronously(handler)
  }

  private fun runGitCommandSynchronously(handler: GitLineHandler) {
    val task = { Git.getInstance().runCommand(handler).throwOnError() }
    ProgressManager.getInstance().runProcessWithProgressSynchronously(task, "", false, null)
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(GitLessonsBundle.message("git.feature.branch.help.link"),
         LessonUtil.getHelpLink("manage-branches.html")),
  )
}