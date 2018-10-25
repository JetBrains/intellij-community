package org.jetbrains.plugins.github

import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.Executor.cd
import git4idea.commands.Git
import git4idea.test.TestDialogHandler
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
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
    initGitChecks()
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
    TestCase.assertFalse(shown.get())

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot)
    TestCase.assertTrue(shown.get())
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
    initGitChecks()
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
    initGitChecks()
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
    initGitChecks()
    checkGitExists()
    checkGithubExists()
    checkRemoteConfigured()
  }

  @Throws(IOException::class)
  protected fun checkGithubExists() {
    val githubInfo = myExecutor.execute<GithubRepoDetailed>(GithubApiRequests.Repos.get(myAccount.server, myUsername, PROJECT_NAME))
    TestCase.assertNotNull("GitHub repository does not exist", githubInfo)
  }
}
