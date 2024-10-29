// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.notification

import com.intellij.idea.IJIgnore
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.VfsUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitBranchWorkerTest
import git4idea.test.GitSingleRepoTest
import junit.framework.TestCase
import java.io.File
import kotlin.io.path.invariantSeparatorsPathString

class GitExternalFileNotifierTest : GitSingleRepoTest() {

  override fun setUpProject() {
    super.setUpProject()
    //ensure project root created by VFS (isFromRefresh == false) and not via external process like Git,
    //otherwise all unversioned files under such project root will be considered like external.
    VfsUtil.createDirectories(projectNioRoot.invariantSeparatorsPathString)
  }

  override fun setUp() {
    super.setUp()

    Registry.get("vcs.show.externally.added.files.notification").setValue(true, testRootDisposable)
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

  @IJIgnore(issue = "IJPL-158905")
  fun `test no notification after branch checkout`() {
    val worker = GitBranchWorker(project, git, GitBranchWorkerTest.TestUiHandler(project))

    worker.checkoutNewBranch("new", listOf(repo))
    val file1Name = "file1.txt"
    val file2Name = "file2.txt"
    //create file1 and file2 via VFS - this will ensure no notification about external files added pop-up
    val file1 = file(projectRoot.createFile(file1Name).name)
    git("add $file1Name")
    git("commit -m $file1Name")
    val file2 = file(projectRoot.createFile(file2Name).name)
    git("add $file2Name")
    git("commit -m $file2Name")

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

  private fun waitForAllEvents() {
    updateUntrackedFiles()
    changeListManager.waitEverythingDoneInTestMode()
    GitVcs.getInstance(project).vfsListener.waitForExternalFilesEventsProcessedInTestMode()
  }

  private fun assertNotificationByMessage(notificationContent: String) =
    vcsNotifier.notifications.find { it.content == notificationContent }
    ?: TestCase.fail("Notification $notificationContent not found")

  private fun assertNoNotification(notificationContent: String) {
    if (vcsNotifier.notifications.find { it.content == notificationContent } != null) {
      TestCase.fail("Notification $notificationContent found")
    }
  }
}
