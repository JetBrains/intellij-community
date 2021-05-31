// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.CommonBundle
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.push.ui.PushLog
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
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
import git4idea.ift.GitLessonsUtil.highlightLatestCommitsFromBranch
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import git4idea.ift.GitProjectUtil
import git4idea.index.actions.runProcess
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import training.dsl.*
import training.project.ProjectUtils
import java.io.File
import javax.swing.JDialog

class GitFeatureBranchWorkflowLesson : GitLesson("Git.BasicWorkflow", GitLessonsBundle.message("git.feature.branch.lesson.name")) {
  override val existedFile = "src/git/simple_cat.yml"
  private val remoteName = "origin"
  private val branchName = "feature"
  private val main = "main"
  private lateinit var repository: GitRepository
  private val remoteProjectName = "RemoteLearningProject"

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

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      val remoteProjectRoot = reCreateRemoteProjectDir()
      GitProjectUtil.copyGitProject(File(remoteProjectRoot.path))
      runProcess(project, "", false) {
        val git = Git.getInstance()
        repository = GitRepositoryManager.getInstance(project).repositories.first()
        git.addRemote(repository, remoteName, remoteProjectRoot.path).throwOnError()
        repository.update()
        git.fetch(repository, repository.remotes.first(), emptyList())
        git.checkout(repository, branchName, null, false, false).throwOnError()
        git.setUpstream(repository, "$remoteName/$main", main)
        repository.update()
      }
      modifyRemoteProject(remoteProjectRoot)
    }

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
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.balloon"), LearningBalloonConfig(Balloon.Position.above, 200))
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
      text(GitLessonsBundle.message("git.feature.branch.checkout.branch", strong(main), strong(GitBundle.message("branches.checkout"))))
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
      text(GitLessonsBundle.message("git.feature.branch.open.update.dialog", strong(main), action(it)))
      val updateProjectDialogTitle = VcsBundle.message("action.display.name.update.scope", VcsBundle.message("update.project.scope.name"))
      triggerByUiComponentAndHighlight(false, false) { ui: JDialog ->
        ui.title?.contains(updateProjectDialogTitle) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.feature.branch.confirm.update", strong(CommonBundle.getOkButtonText())))
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

    lateinit var secondShowBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      secondShowBranchesTaskId = taskId
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.2", strong(branchName), strong(main), action(it)))
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: TextPanel.WithIconAndArrows ->
        ui.text == main
      }
      triggerOnBranchesPopupShown()
    }

    task {
      triggerByListItemAndHighlight { item -> item.toString() == branchName }
    }

    task {
      val checkoutAndRebaseText = GitBundle.message("branches.checkout.and.rebase.onto.current")
      text(GitLessonsBundle.message("git.feature.branch.checkout.and.rebase", strong(branchName), strong(checkoutAndRebaseText)))
      triggerByListItemAndHighlight { item -> item.toString().contains(checkoutAndRebaseText) }
      triggerOnNotification { notification -> notification.title == GitBundle.message("rebase.notification.successful.title") }
      restoreState(secondShowBranchesTaskId, delayMillis = 3 * defaultRestoreDelay) {
        previous.ui?.isShowing != true && !StoreReloadManager.getInstance().isReloadBlocked() // reload is blocked when rebase is running
      }
    }

    task("Vcs.Push") {
      text(GitLessonsBundle.message("git.feature.branch.open.push.dialog", strong(branchName), action(it)))
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
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }
  }

  private fun TaskContext.triggerOnBranchesPopupShown() {
    triggerByUiComponentAndHighlight(false, false) { ui: EngravedLabel ->
      val branchesInRepoText = DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", GitBundle.message("git4idea.vcs.name"),
                                                  DvcsUtil.getShortRepositoryName(repository))
      ui.text?.contains(branchesInRepoText) == true
    }
  }

  private fun TaskRuntimeContext.reCreateRemoteProjectDir(): File {
    val learnProjectPath = ProjectUtils.getProjectRoot(project).toNioPath()
    val learnProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(learnProjectPath)
                           ?: error("Learning project not found")
    val projectsRoot = learnProjectRoot.parent.toNioPath().toFile()
    val remoteProjectRoot = projectsRoot.listFiles()?.find { it.name == remoteProjectName }.let {
      it?.apply { deleteRecursively() } ?: File(projectsRoot.absolutePath + File.separator + remoteProjectName)
    }
    remoteProjectRoot.mkdir()
    return remoteProjectRoot
  }

  private fun modifyRemoteProject(remoteProjectRoot: File) {
    val files = mutableListOf<File>()
    FileUtil.processFilesRecursively(remoteProjectRoot, files::add)
    val firstFile = files.find { it.name == "sphinx_cat.yml" }
    val secondFile = files.find { it.name == "puss_in_boots.yml" }
    if(firstFile != null && secondFile != null) {
      gitChange(remoteProjectRoot, "user.name", "JonnyCatsville")
      gitChange(remoteProjectRoot, "user.email", "jonny.catsville@meow.com")
      createOneFileCommit(remoteProjectRoot, firstFile, "Add new fact about sphinx's behaviour") {
        it.appendText(firstFileAddition)
      }
      createOneFileCommit(remoteProjectRoot, secondFile, "Add fact about Puss in boots") {
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