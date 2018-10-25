// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Clock
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.DateFormatUtil
import git4idea.GitUtil
import git4idea.test.TestDialogHandler
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.test.GithubGitRepoTest
import org.jetbrains.plugins.github.ui.GithubShareDialog
import java.io.IOException
import java.util.*

abstract class GithubShareProjectTestBase : GithubGitRepoTest() {
  protected lateinit var projectName: String

  override fun setUp() {
    super.setUp()

    val rnd = Random()
    val time = Clock.getTime()
    projectName = "new_project_from_" + getTestName(false) + "_" + DateFormatUtil.formatDate(time).replace('/', '-') + "_" + rnd.nextLong()
  }

  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { deleteGithubRepo() })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  @Throws(IOException::class)
  private fun deleteGithubRepo() {
    mainAccount.executor.execute(GithubApiRequests.Repos.delete(mainAccount.account.server, mainAccount.username, projectName))
  }

  protected fun registerDefaultShareDialogHandler() {
    dialogManager.registerDialogHandler(GithubShareDialog::class.java, TestDialogHandler { dialog ->
      dialog.testSetRepositoryName(projectName)
      DialogWrapper.OK_EXIT_CODE
    })
  }

  protected fun registerDefaultUntrackedFilesDialogHandler() {
    dialogManager.registerDialogHandler(GithubShareAction.GithubUntrackedFilesDialog::class.java,
                                        TestDialogHandler {
                                          // actually we should ask user for name/email ourselves (like in CommitDialog)
                                          for (repository in GitUtil.getRepositoryManager(myProject).repositories) {
                                            setGitIdentity(repository.root)
                                          }
                                          DialogWrapper.OK_EXIT_CODE
                                        })
  }

  protected fun registerSelectNoneUntrackedFilesDialogHandler() {
    dialogManager.registerDialogHandler(GithubShareAction.GithubUntrackedFilesDialog::class.java,
                                        TestDialogHandler { dialog ->
                                          // actually we should ask user for name/email ourselves (like in CommitDialog)
                                          for (repository in GitUtil.getRepositoryManager(myProject).repositories) {
                                            setGitIdentity(repository.root)
                                          }
                                          dialog.selectedFiles = emptyList<VirtualFile>()
                                          DialogWrapper.OK_EXIT_CODE
                                        })
  }
}
