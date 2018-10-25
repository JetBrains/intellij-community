package org.jetbrains.plugins.github

import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.Executor.cd
import git4idea.commands.Git
import git4idea.test.TestDialogHandler
import git4idea.test.git
import org.jetbrains.plugins.github.api.GithubApiRequests
import java.io.IOException

/**
 * @author Aleksey Pivovarov
 */
class GithubShareProjectTest : GithubShareProjectTestBase() {

  @Throws(Throwable::class)
  fun testSimple() {
    registerDefaultShareDialogHandler()
    registerDefaultUntrackedFilesDialogHandler()

    createProjectFiles()

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkGithubExists()
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  fun testGithubAlreadyExists() {
    val shown = Ref.create(false)
    dialogManager.registerDialogHandler(GithubShareAction.GithubExistingRemotesDialog::class.java,
                                        TestDialogHandler {
                                          shown.set(true)
                                          DialogWrapper.OK_EXIT_CODE
                                        })

    registerDefaultShareDialogHandler()
    registerDefaultUntrackedFilesDialogHandler()

    createProjectFiles()
    GithubShareAction.shareProjectOnGithub(myProject, projectRoot)
    assertFalse(shown.get())

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot)
    assertTrue(shown.get())
  }

  @Throws(Throwable::class)
  fun testExistingGit() {
    registerDefaultShareDialogHandler()
    registerDefaultUntrackedFilesDialogHandler()

    createProjectFiles()

    cd(projectRoot.path)
    git("init")
    setGitIdentity(projectRoot)
    git("add file.txt")
    git("commit -m init")

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkGithubExists()
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  @Throws(Throwable::class)
  fun testExistingFreshGit() {
    registerDefaultShareDialogHandler()
    registerDefaultUntrackedFilesDialogHandler()

    createProjectFiles()

    Git.getInstance().init(myProject, projectRoot)

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkGithubExists()
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  @Throws(Throwable::class)
  fun testEmptyProject() {
    registerSelectNoneUntrackedFilesDialogHandler()
    registerDefaultShareDialogHandler()

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully created empty repository on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkGithubExists()
    checkRemoteConfigured()
  }

  @Throws(IOException::class)
  private fun checkGithubExists() {
    val githubInfo = mainAccount.executor.execute(
      GithubApiRequests.Repos.get(mainAccount.account.server, mainAccount.username, projectName))
    assertNotNull("GitHub repository does not exist", githubInfo)
  }
}
