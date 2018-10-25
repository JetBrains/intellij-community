/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github

import com.intellij.notification.NotificationType
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.github.util.GithubGitHelper

/**
 * @author Aleksey Pivovarov
 */
class GithubCreatePullRequestTest : GithubCreatePullRequestTestBase() {
  fun testSimple() {
    registerDefaultCreatePullRequestDialogHandler("master", myUsername)
    myAuthenticationManager.setDefaultAccount(myProject, myAccount)

    val coordinatesSet = GithubGitHelper.getInstance().getPossibleRemoteUrlCoordinates(myProject)
    UsefulTestCase.assertSize(1, coordinatesSet)
    val coordinates = coordinatesSet.iterator().next()

    GithubCreatePullRequestAction.createPullRequest(myProject, myRepository!!, coordinates.remote, coordinates.url, myAccount)

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null)
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  fun testParent() {
    registerDefaultCreatePullRequestDialogHandler("file2", myUsername2)
    git("remote add somename " + GithubGitHelper.getInstance().getRemoteUrl(myAccount2.server, myUsername2,
                                                                            GithubCreatePullRequestTestBase.PROJECT_NAME))
    myAuthenticationManager.setDefaultAccount(myProject, myAccount)
    myRepository!!.update()

    val coordinatesSet = GithubGitHelper.getInstance().getPossibleRemoteUrlCoordinates(myProject)
    UsefulTestCase.assertSize(2, coordinatesSet)
    val coordinates = ContainerUtil.find(coordinatesSet) { c -> c.remote.name != "somename" }

    GithubCreatePullRequestAction.createPullRequest(myProject, myRepository!!, coordinates!!.remote, coordinates.url, myAccount)

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null)
    checkRemoteConfigured()
    checkLastCommitPushed()
  }
}
