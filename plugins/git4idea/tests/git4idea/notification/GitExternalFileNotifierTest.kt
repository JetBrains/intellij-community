// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.notification

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.vcs.AbstractVcsTestCase.setStandardConfirmation
import com.intellij.util.ThrowableRunnable
import git4idea.test.GitSingleRepoTest
import junit.framework.TestCase

class GitExternalFileNotifierTest : GitSingleRepoTest() {

  override fun setUp() {
    super.setUp()

    setStandardConfirmation(project, vcs.name, VcsConfiguration.StandardConfirmation.ADD, VcsShowConfirmationOption.Value.SHOW_CONFIRMATION)
    projectRoot.children //ensure that all subsequent VFS events will be fired after new files added to projectRoot
  }

  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable {
        setStandardConfirmation(project, vcs.name, VcsConfiguration.StandardConfirmation.ADD,
                                VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY)
      })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  fun `test notification after external files added`(){
    file("file1.txt").create()
    file("file2.txt").create()

    refresh()

    assertNotificationByMessage(VcsBundle.message("external.files.add.notification.message", vcs.displayName))
  }

  fun `test notification after external files and project configuration files added`(){
    file("file1.txt").create()
    file("file2.txt").create()
    file("module.iml").create()

    refresh()

    assertSize(2, vcsNotifier.notifications)
    assertNotificationByMessage(VcsBundle.message("external.files.add.notification.message", vcs.displayName))
    assertNotificationByMessage(VcsBundle.message("project.configuration.files.add.notification.message", vcs.displayName))
  }

  private fun assertNotificationByMessage(notificationContent: String) =
    vcsNotifier.notifications.find { it.content == notificationContent }
    ?: TestCase.fail("Notification $notificationContent not found")
}
