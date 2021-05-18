// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.dvcs.push.ui.PushLog
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.EngravedLabel
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.util.findBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.ift.GitLessonsUtil.findVcsLogData
import git4idea.ift.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import git4idea.ift.GitLessonsUtil.proceedLink
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import git4idea.ift.GitProjectUtil
import git4idea.index.actions.runProcess
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import training.dsl.*
import java.io.File
import javax.swing.JDialog

class GitFeatureBranchWorkflowLesson : GitLesson("Git.BasicWorkflow", "Feature branch workflow") {
  override val existedFile = "src/git/simple_cat.yml"
  private val branchName = "feature"
  private lateinit var repository: GitRepository
  private val remoteProjectName = "RemoteLearningProject"

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      val remoteProjectRoot = reCreateRemoteProjectDir()
      GitProjectUtil.copyGitProject(File(remoteProjectRoot.path))
      runProcess(project, "", false) {
        val git = Git.getInstance()
        repository = GitRepositoryManager.getInstance(project).repositories.first()
        git.addRemote(repository, "origin", remoteProjectRoot.path).throwOnError()
        repository.update()
        git.fetch(repository, repository.remotes.first(), emptyList())
        git.checkout(repository, branchName, null, false, false).throwOnError()
        git.setUpstream(repository, "origin/main", "main")
        repository.update()
      }
      modifyRemoteProject(remoteProjectRoot)
    }

    task("ActivateVersionControlToolWindow") {
      text("Suppose you have finished the work on your ${strong(branchName)} branch and pushed the changes to remote hoping to merge it with the ${strong("main")} branch later. Press ${action(it)} to open Git tool window and overview the project history.")
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
    }

    resetGitLogWindow()
    lateinit var vcsData: VcsLogData
    task {
      val future = findVcsLogData()
      stateCheck {
        val data = future.getNow(null)
        if (data != null) {
          vcsData = data
          true
        }
        else false
      }
    }

    task("main") {
      text("But when you were worked on it some of your colleagues may also push their changes to the ${strong(it)} branch. So we should check that possible changes from ${strong(it)} will not conflict with our changes.")
      highlightSubsequentCommitsInGitLog(sequenceLength = 2) { commit ->
        val root = vcsData.roots.single()
        commit.id == vcsData.dataPack.findBranch(branchName, root)?.commitHash
      }
      proceedLink()
    }

    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: TextPanel.WithIconAndArrows -> ui.text == branchName }
    }

    lateinit var firstShowBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      firstShowBranchesTaskId = taskId
      text("At first, we need to checkout the ${strong("main")} branch. Please press ${action(it)} or click the highlighted current branch to open the branches list.")
      text("Your active branch is displayed here.", LearningBalloonConfig(Balloon.Position.above, 200))
      triggerByUiComponentAndHighlight(false, false) { ui: EngravedLabel ->
        ui.text.contains("Git Branches")
      }
    }

    task {
      triggerByListItemAndHighlight { item -> item.toString() == "main" }
    }

    task("main") {
      lateinit var curBranchName: String
      before {
        curBranchName = repository.currentBranchName ?: error("Not found information about active branch")
      }
      text("Select the ${strong(it)} branch and choose ${strong("Checkout")}.")
      stateCheck { repository.currentBranchName == it }
      restoreState(firstShowBranchesTaskId, delayMillis = defaultRestoreDelay) {
        val newBranchName = repository.currentBranchName
        previous.ui?.isShowing != true || (newBranchName != curBranchName && newBranchName != it)
      }
    }

    prepareRuntimeTask {
      val showSettingsOption = ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE)
      showSettingsOption.value = true  // needed to show update project dialog
    }

    task("Vcs.UpdateProject") {
      text("Second, we should update the ${strong("main")} branch to be aware of the possible changes from remote. Press ${action(it)} to open update project dialog.")
      triggerByUiComponentAndHighlight(false, false) { ui: JDialog ->
        ui.title == "Update Project"
      }
    }

    task {
      text("Click ${strong("OK")} button to confirm update.")
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreState(delayMillis = defaultRestoreDelay) {
        previous.ui?.isShowing != true && !ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning
      }
    }

    task("Git.Branches") {
      text("There really were some changes in the ${strong("main")} branch.")
      highlightSubsequentCommitsInGitLog(sequenceLength = 2) {
        val root = vcsData.roots.single()
        it.id == vcsData.dataPack.findBranch("origin/main", root)?.commitHash
      }
      proceedLink()
    }

    lateinit var secondShowBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      secondShowBranchesTaskId = taskId
      text("So we should rebase our ${strong(branchName)} branch on ${strong("main")}. Press ${action(it)} or click the highlighted current branch to open the branches list again.")
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: TextPanel.WithIconAndArrows ->
        ui.text == "main"
      }
      triggerByUiComponentAndHighlight(false, false) { ui: EngravedLabel ->
        ui.text.contains("Git Branches")
      }
    }

    task {
      triggerByListItemAndHighlight { item -> item.toString() == branchName }
    }

    task {
      text("Select the ${strong(branchName)} branch and choose ${strong("Checkout and Rebase onto Current")}.")
      triggerByListItemAndHighlight { item -> item.toString() == "Checkout and Rebase onto Current" }
      triggerOnNotification { notification -> notification.title == "Rebase successful" }
      restoreState(secondShowBranchesTaskId, delayMillis = 3 * defaultRestoreDelay) {
        previous.ui?.isShowing != true && !StoreReloadManager.getInstance().isReloadBlocked() // reload is blocked when rebase is running
      }
    }

    task("Vcs.Push") {
      text("When ${strong(branchName)} branch become updated we should update it in the remote repository too. Press ${action(it)} to open push dialog.")
      triggerByUiComponentAndHighlight(false, false) { _: PushLog -> true }
    }

    task("Force Push") {
      text("We can't just push the changes, because our remote ${strong(branchName)} branch conflicts with updated local branch. We should use ${strong(it)}. Press highlighted arrow near ${strong("Push")} button to open drop-down menu and select ${strong(it)}.")
      triggerByUiComponentAndHighlight(usePulsation = true) { _: BasicOptionButtonUI.ArrowButton -> true }
      triggerByUiComponentAndHighlight(false, false) { ui: JDialog ->
        ui.title == it
      }
      restoreByUi()
    }

    task("Force Push") {
      text("Press ${strong(it)} button again to confirm action.")
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }
  }

  private fun TaskRuntimeContext.reCreateRemoteProjectDir(): File {
    val learnProjectPath = ProjectRootManager.getInstance(project).contentRoots[0].toNioPath()
    val learnProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(learnProjectPath)
                           ?: error("Learning project not found")
    val projectsRoot = learnProjectRoot.parent.toNioPath().toFile()
    val remoteProjectRoot = projectsRoot.listFiles()?.find { it.name == remoteProjectName }.let {
      if (it != null) {
        it.deleteRecursively()
        it
      }
      else File(projectsRoot.absolutePath + File.separator + remoteProjectName)
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
        it.appendText("""
    - steal:
        condition: food was left unattended
        action:
          - steal a piece of food and hide""")
      }
      createOneFileCommit(remoteProjectRoot, secondFile, "Add fact about Puss in boots") {
        it.appendText("""
    - care_for_weapon:
        condition: favourite sword become blunt
        actions:
          - sharpen the sword using the stone""")
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