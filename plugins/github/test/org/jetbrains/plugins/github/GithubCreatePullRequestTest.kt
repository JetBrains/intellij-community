// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.notification.NotificationType
import com.intellij.util.containers.ContainerUtil
import git4idea.test.git

class GithubCreatePullRequestTest : GithubCreatePullRequestTestBase() {
  fun testSimple() {
    registerDefaultCreatePullRequestDialogHandler("master", mainAccount.username)

    val coordinatesSet = gitHelper.getPossibleRemoteUrlCoordinates(myProject)
    assertSize(1, coordinatesSet)
    val coordinates = coordinatesSet.iterator().next()

    GithubCreatePullRequestAction.createPullRequest(myProject, repository, coordinates.remote, coordinates.url, mainAccount.account)

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null)
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  fun testParent() {
    registerDefaultCreatePullRequestDialogHandler("file2", secondaryAccount.username)
    repository.git(
      "remote add somename " + gitHelper.getRemoteUrl(secondaryAccount.account.server, secondaryAccount.username, PROJECT_NAME))
    repository.update()

    val coordinatesSet = gitHelper.getPossibleRemoteUrlCoordinates(myProject)
    assertSize(2, coordinatesSet)
    val coordinates = ContainerUtil.find(coordinatesSet) { c -> c.remote.name != "somename" }

    GithubCreatePullRequestAction.createPullRequest(myProject, repository, coordinates!!.remote, coordinates.url, mainAccount.account)

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null)
    checkRemoteConfigured()
    checkLastCommitPushed()
  }
}
