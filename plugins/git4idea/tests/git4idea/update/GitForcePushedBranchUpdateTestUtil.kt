// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.test.VcsPlatformTestContext
import git4idea.actions.branch.GitForcePushedBranchUpdateExecutor
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import git4idea.test.cd
import git4idea.test.file
import org.assertj.core.api.Assertions.assertThat

internal fun GitForcePushedBranchUpdateExecutor.waitForUpdate() {
  waitCoroutinesBlocking(coroutineScope)
}

internal fun GitRepository.commitsFrom(vararg logCommandParams: String): List<VcsCommitMetadata> {
  return GitLogUtil.collectMetadata(project, root, *logCommandParams).commits
}

internal fun GitRepository.cd(): GitRepository {
  cd(this)
  return this
}

internal fun GitRepository.assertExists(file: String) = cd().file(file).assertExists()

internal fun GitRepository.assertNotExists(file: String) = cd().file(file).assertNotExists()

internal fun VcsPlatformTestContext.assertNotificationByMessage(notificationContent: String) {
  assertThat(vcsNotifier.notifications.map { it.content })
    .describedAs("Notification '$notificationContent' not found")
    .contains(notificationContent)
}
