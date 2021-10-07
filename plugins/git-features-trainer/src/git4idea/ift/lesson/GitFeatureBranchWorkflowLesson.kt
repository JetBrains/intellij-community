// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.CommonBundle
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.push.ui.PushLog
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.EngravedLabel
import com.intellij.ui.components.BasicOptionButtonUI
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.ift.GitLessonsBundle
import git4idea.ift.GitLessonsUtil.checkoutBranch
import git4idea.ift.GitLessonsUtil.highlightLatestCommitsFromBranch
import git4idea.ift.GitLessonsUtil.openPushDialogText
import git4idea.ift.GitLessonsUtil.openUpdateDialogText
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import git4idea.ift.GitProjectUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchPopupActions
import training.dsl.*
import java.io.File
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JList

class GitFeatureBranchWorkflowLesson : GitLesson("Git.BasicWorkflow", GitLessonsBundle.message("git.feature.branch.lesson.name")) {
  override val existedFile = "git/simple_cat.yml"
  private val remoteName = "origin"
  private val branchName = "feature"
  private val main = "main"

  private val firstFileName = "sphinx_cat.yml"
  private val secondFileName = "puss_in_boots.yml"
  private val committerName = "Johnny Catsville"
  private val committerEmail = "johnny.catsville@meow.com"
  private val firstCommitMessage = "Add new fact about sphinx's behaviour"
  private val secondCommitMessage = "Add fact about Puss in boots"

  private val firstFileAddition = """
    |
    |    - steal:
    |        condition: food was left unattended
    |        action:
    |          - steal a piece of food and hide""".trimMargin()

  private val secondFileAddition = """
    |
    |    - care_for_weapon:
    |        condition: favourite sword become blunt
    |        actions:
    |          - sharpen the sword using the stone""".trimMargin()

  private lateinit var repository: GitRepository

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      repository = GitRepositoryManager.getInstance(project).repositories.first()
    }

    checkoutBranch(branchName)

    task("ActivateVersionControlToolWindow") {
      text(GitLessonsBundle.message("git.feature.branch.introduction.1", strong(branchName), strong(main), action(it)))
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
    }

    resetGitLogWindow()

    task {
      text(GitLessonsBundle.message("git.feature.branch.introduction.2", strong(main)))
      highlightLatestCommitsFromBranch(branchName, sequenceLength = 2)
      proceedLink()
    }

    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: TextPanel.WithIconAndArrows -> ui.text == branchName }
    }

    lateinit var firstShowBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      firstShowBranchesTaskId = taskId
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.1", strong(main), action(it)))
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.balloon"), LearningBalloonConfig(Balloon.Position.above, 0))
      triggerOnBranchesPopupShown()
    }

    task {
      triggerByListItemAndHighlight { item -> item.toString() == main }
    }

    task {
      lateinit var curBranchName: String
      before {
        curBranchName = repository.currentBranchName ?: error("Not found information about active branch")
      }
      val checkoutItemText = GitBundle.message("branches.checkout")
      text(GitLessonsBundle.message("git.feature.branch.checkout.branch", strong(main), strong(checkoutItemText)))
      highlightListItemAndRehighlight { item -> item.toString() == checkoutItemText }
      stateCheck { repository.currentBranchName == main }
      restoreState(firstShowBranchesTaskId, delayMillis = defaultRestoreDelay) {
        val newBranchName = repository.currentBranchName
        previous.ui?.isShowing != true || (newBranchName != curBranchName && newBranchName != main)
      }
    }

    prepareRuntimeTask {
      val showSettingsOption = ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE)
      showSettingsOption.value = true  // needed to show update project dialog
    }

    task("Vcs.UpdateProject") {
      val updateProjectDialogTitle = VcsBundle.message("action.display.name.update.scope", VcsBundle.message("update.project.scope.name"))
      openUpdateDialogText(GitLessonsBundle.message("git.feature.branch.open.update.dialog", strong(main)))
      triggerByUiComponentAndHighlight(false, false) { ui: JDialog ->
        ui.title?.contains(updateProjectDialogTitle) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.feature.branch.confirm.update", strong(CommonBundle.getOkButtonText())))
      triggerByUiComponentAndHighlight { ui: JButton ->
        ui.text == CommonBundle.getOkButtonText()
      }
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreState(delayMillis = defaultRestoreDelay) {
        previous.ui?.isShowing != true && !ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning
      }
    }

    task("Git.Branches") {
      text(GitLessonsBundle.message("git.feature.branch.new.commits.explanation", strong(main)))
      highlightLatestCommitsFromBranch("$remoteName/$main", sequenceLength = 2)
      proceedLink()
    }

    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: TextPanel.WithIconAndArrows -> ui.text == main }
    }

    lateinit var secondShowBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      secondShowBranchesTaskId = taskId
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.2", strong(branchName), strong(main), action(it)))
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.balloon"), LearningBalloonConfig(Balloon.Position.above, 200))
      triggerOnBranchesPopupShown()
    }

    task {
      triggerByListItemAndHighlight { item -> item.toString() == branchName }
    }

    task {
      val repositories = GitRepositoryManager.getInstance(project).repositories
      val checkoutAndRebaseText = GitBundle.message("branches.checkout.and.rebase.onto.branch",
                                                    GitBranchPopupActions.getCurrentBranchTruncatedPresentation(project, repositories))
      text(GitLessonsBundle.message("git.feature.branch.checkout.and.rebase", strong(branchName), strong(checkoutAndRebaseText)))
      highlightListItemAndRehighlight { item -> item.toString().contains(checkoutAndRebaseText) }
      triggerOnNotification { notification -> notification.title == GitBundle.message("rebase.notification.successful.title") }
      restoreState(secondShowBranchesTaskId, delayMillis = 3 * defaultRestoreDelay) {
        previous.ui?.isShowing != true && !StoreReloadManager.getInstance().isReloadBlocked() // reload is blocked when rebase is running
      }
    }

    task("Vcs.Push") {
      openPushDialogText(GitLessonsBundle.message("git.feature.branch.open.push.dialog", strong(branchName)))
      triggerByUiComponentAndHighlight(false, false) { _: PushLog -> true }
    }

    val forcePushText = DvcsBundle.message("action.force.push").dropMnemonic()
    task {
      text(GitLessonsBundle.message("git.feature.branch.choose.force.push",
                                    strong(branchName), strong(forcePushText), strong(DvcsBundle.message("action.push").dropMnemonic())))
      triggerByUiComponentAndHighlight(usePulsation = true) { _: BasicOptionButtonUI.ArrowButton -> true }
      val forcePushDialogTitle = DvcsBundle.message("force.push.dialog.title")
      triggerByUiComponentAndHighlight(false, false) { ui: JDialog ->
        ui.title?.contains(forcePushDialogTitle) == true
      }
      restoreByUi()
    }

    task {
      text(GitLessonsBundle.message("git.feature.branch.confirm.force.push", strong(forcePushText)))
      text(GitLessonsBundle.message("git.feature.branch.force.push.tip", strong(forcePushText)))
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }
  }

  override fun prepare(project: Project) {
    super.prepare(project)
    val remoteProjectRoot = GitProjectUtil.createRemoteProject(remoteName, project)
    modifyRemoteProject(remoteProjectRoot)
  }

  private fun TaskContext.triggerOnBranchesPopupShown() {
    triggerByUiComponentAndHighlight(false, false) { ui: EngravedLabel ->
      val branchesInRepoText = DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", GitBundle.message("git4idea.vcs.name"),
                                                  DvcsUtil.getShortRepositoryName(repository))
      ui.text?.contains(branchesInRepoText) == true
    }
  }

  private fun TaskContext.highlightListItemAndRehighlight(checkList: TaskRuntimeContext.(item: Any) -> Boolean) {
    var showedList: JList<*>? = null
    triggerByPartOfComponent l@{ ui: JList<*> ->
      val ind = (0 until ui.model.size).find { checkList(ui.model.getElementAt(it)) } ?: return@l null
      showedList = ui
      ui.getCellBounds(ind, ind)
    }
    // it is a hack: restart current task to highlight list item when it will be shown again
    // rehighlightPreviousUi property can not be used in this case, because I can't highlight this list item in the previous task
    restoreState(restoreId = taskId) {
      showedList != null && !showedList!!.isShowing
    }
  }

  private fun modifyRemoteProject(remoteProjectRoot: File) {
    val files = mutableListOf<File>()
    FileUtil.processFilesRecursively(remoteProjectRoot, files::add)
    val firstFile = files.find { it.name == firstFileName }
    val secondFile = files.find { it.name == secondFileName }
    if (firstFile != null && secondFile != null) {
      gitChange(remoteProjectRoot, "user.name", committerName)
      gitChange(remoteProjectRoot, "user.email", committerEmail)
      createOneFileCommit(remoteProjectRoot, firstFile, firstCommitMessage) {
        it.appendText(firstFileAddition)
      }
      createOneFileCommit(remoteProjectRoot, secondFile, secondCommitMessage) {
        it.appendText(secondFileAddition)
      }
    }
    else error("Failed to find files to modify in $remoteProjectRoot")
  }

  private fun gitChange(root: File, param: String, value: String) {
    val handler = GitLineHandler(null, root, GitCommand.CONFIG)
    handler.addParameters(param, value)
    runGitCommandSynchronously(handler)
  }

  private fun createOneFileCommit(root: File, editingFile: File, commitMessage: String, editFileContent: (File) -> Unit) {
    editFileContent(editingFile)
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
}