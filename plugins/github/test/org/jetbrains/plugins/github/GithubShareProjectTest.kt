// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.Executor.cd
import git4idea.commands.Git
import git4idea.test.TestDialogHandler
import git4idea.test.git
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.ui.dialog.GithubExistingRemotesDialog

class GithubShareProjectTest : GithubShareProjectTestBase() {

  @Throws(Throwable::class)
  fun testSimple() {
    registerDefaultShareDialogHandler()
    registerDefaultUntrackedFilesDialogHandler()

    createProjectFiles()

    GHShareProjectUtil.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully created empty repository on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkRepoExists(mainAccount, GHRepositoryPath(mainAccount.username, projectName))
    checkRemoteConfigured()
  }

  fun testGithubAlreadyExists() {
    val shown = Ref.create(false)
    dialogManager.registerDialogHandler(GithubExistingRemotesDialog::class.java,
                                        TestDialogHandler {
                                          shown.set(true)
                                          DialogWrapper.CANCEL_EXIT_CODE
                                        })

    registerDefaultShareDialogHandler()
    registerDefaultUntrackedFilesDialogHandler()

    createProjectFiles()
    GHShareProjectUtil.shareProjectOnGithub(myProject, projectRoot)
    assertFalse(shown.get())
    checkRepoExists(mainAccount, GHRepositoryPath(mainAccount.username, projectName))

    GHShareProjectUtil.shareProjectOnGithub(myProject, projectRoot)
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

    GHShareProjectUtil.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkRepoExists(mainAccount, GHRepositoryPath(mainAccount.username, projectName))
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  @Throws(Throwable::class)
  fun testExistingFreshGit() {
    registerDefaultShareDialogHandler()
    registerDefaultUntrackedFilesDialogHandler()

    createProjectFiles()

    Git.getInstance().init(myProject, projectRoot)

    GHShareProjectUtil.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkRepoExists(mainAccount, GHRepositoryPath(mainAccount.username, projectName))
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  @Throws(Throwable::class)
  fun testEmptyProject() {
    registerSelectNoneUntrackedFilesDialogHandler()
    registerDefaultShareDialogHandler()

    GHShareProjectUtil.shareProjectOnGithub(myProject, projectRoot)

    checkNotification(NotificationType.INFORMATION, "Successfully created empty repository on GitHub", null)
    findGitRepo()
    checkGitExists()
    checkRepoExists(mainAccount, GHRepositoryPath(mainAccount.username, projectName))
    checkRemoteConfigured()
  }
}
