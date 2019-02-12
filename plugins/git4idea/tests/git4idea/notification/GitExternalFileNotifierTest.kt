// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.notification

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import git4idea.GitVcs
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitBranchWorkerTest
import git4idea.test.GitSingleRepoTest
import junit.framework.TestCase
import java.io.File

class GitExternalFileNotifierTest : GitSingleRepoTest() {

  override fun setUp() {
    super.setUp()

    Registry.get("vcs.process.externally.added.files").setValue(true, testRootDisposable)
    projectRoot.children //ensure that all subsequent VFS events will be fired after new files added to projectRoot
  }

  fun `test notification after external files added`() {
    file("file1.txt").create()
    file("file2.txt").create()

    refresh()
    waitForAllEvents()

    assertNotificationByMessage(VcsBundle.message("external.files.add.notification.message", vcs.displayName))
  }

  fun `test no notification after branch checkout`() {
    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler())

    worker.checkoutNewBranch("new", listOf(repo))
    val file1 = file("file1.txt").create().addCommit("commit file1")
    val file2 = file("file2.txt").create().addCommit("commit file2")

    worker.checkout("master", false, listOf(repo))
    file1.assertNotExists()
    file2.assertNotExists()

    worker.checkout("new", false, listOf(repo))
    assertTrue(file2.exists())
    assertTrue(file1.exists())

    refresh()
    waitForAllEvents()

    assertNoNotification(VcsBundle.message("external.files.add.notification.message", vcs.displayName))
  }

  fun `test add external files silently`() {
    VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY = true

    val file1 = file("file1.txt").create().file
    val file2 = file("file2.txt").create().file

    refresh()
    waitForAllEvents()

    assertAdded(file1)
    assertAdded(file2)
    assertNoNotification(VcsBundle.message("external.files.add.notification.message", vcs.displayName))
  }

  private fun assertAdded(file: File) =
    assertTrue(changeListManager.getStatus(getVirtualFile(file)) == FileStatus.ADDED)

  private fun waitForAllEvents() = GitVcs.getInstance(project).vfsListener.waitForAllEventsProcessedInTestMode()

  private fun assertNotificationByMessage(notificationContent: String) =
    vcsNotifier.notifications.find { it.content == notificationContent }
    ?: TestCase.fail("Notification $notificationContent not found")

  private fun assertNoNotification(notificationContent: String) {
    if (vcsNotifier.notifications.find { it.content == notificationContent } != null) {
      TestCase.fail("Notification $notificationContent found")
    }
  }
}
