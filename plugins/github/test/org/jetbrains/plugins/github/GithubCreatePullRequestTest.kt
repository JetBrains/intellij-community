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
package org.jetbrains.plugins.github;

import com.intellij.notification.NotificationType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates;
import org.jetbrains.plugins.github.util.GithubGitHelper;

import java.util.Set;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestTest extends GithubCreatePullRequestTestBase {
  public void testSimple() {
    registerDefaultCreatePullRequestDialogHandler("master", myUsername);
    myAuthenticationManager.setDefaultAccount(myProject, myAccount);

    Set<GitRemoteUrlCoordinates> coordinatesSet = GithubGitHelper.getInstance().getPossibleRemoteUrlCoordinates(myProject);
    assertSize(1, coordinatesSet);
    GitRemoteUrlCoordinates coordinates = coordinatesSet.iterator().next();

    GithubCreatePullRequestAction.createPullRequest(myProject, myRepository, coordinates.getRemote(), coordinates.getUrl(), myAccount);

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null);
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testParent() {
    registerDefaultCreatePullRequestDialogHandler("file2", myUsername2);
    git("remote add somename " + GithubGitHelper.getInstance().getRemoteUrl(myAccount2.getServer(), myUsername2, PROJECT_NAME));
    myAuthenticationManager.setDefaultAccount(myProject, myAccount);
    myRepository.update();

    Set<GitRemoteUrlCoordinates> coordinatesSet = GithubGitHelper.getInstance().getPossibleRemoteUrlCoordinates(myProject);
    assertSize(2, coordinatesSet);
    GitRemoteUrlCoordinates coordinates = ContainerUtil.find(coordinatesSet, c -> !c.getRemote().getName().equals("somename"));

    GithubCreatePullRequestAction.createPullRequest(myProject, myRepository, coordinates.getRemote(), coordinates.getUrl(), myAccount);

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null);
    checkRemoteConfigured();
    checkLastCommitPushed();
  }
}
