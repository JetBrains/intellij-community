// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.actions.branch.GitForcePushedBranchUpdateExecutor
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTest
import git4idea.test.cd
import git4idea.test.file
import junit.framework.TestCase

abstract class GitForcePushedBranchUpdateBaseTest : GitPlatformTest() {

  internal fun GitForcePushedBranchUpdateExecutor.waitForUpdate() {
    waitCoroutinesBlocking(coroutineScope)
  }

  protected fun GitRepository.commitsFrom(vararg logCommandParams: String): List<VcsCommitMetadata> {
    return GitLogUtil.collectMetadata(project, root, *logCommandParams).commits
  }

  protected fun GitRepository.cd(): GitRepository {
    cd(this)
    return this
  }

  protected fun GitRepository.assertExists(file: String) = cd().file(file).assertExists()
  protected fun GitRepository.assertNotExists(file: String) = cd().file(file).assertNotExists()

  protected fun assertNotificationByMessage(notificationContent: String) =
    vcsNotifier.notifications.find { it.content == notificationContent }
    ?: TestCase.fail("Notification $notificationContent not found")
}
