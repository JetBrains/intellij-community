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
import com.intellij.openapi.ui.DialogWrapper;
import git4idea.test.TestDialogHandler;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.dvcs.test.Executor.cd;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestTest extends GithubCreatePullRequestTestBase {
  public void testSimple() throws Exception {
    registerDefaultCreatePullRequestDialogHandler(myLogin1 + ":master");

    cd(myProjectRoot.getPath());
    cloneRepo();
    createBranch();
    createChanges();

    GithubCreatePullRequestAction.createPullRequest(myProject, myProjectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null);
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testParent() throws Exception {
    registerDefaultCreatePullRequestDialogHandler(myLogin2 + ":file2");

    cd(myProjectRoot.getPath());
    cloneRepo();
    createBranch();
    createChanges();

    GithubCreatePullRequestAction.createPullRequest(myProject, myProjectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null);
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testLoadBranches() throws Exception {
    final List<String> expected = new ArrayList<String>();
    expected.add(myLogin1 + ":master");
    expected.add(myLogin2 + ":master");
    expected.add(myLogin2 + ":file2");

    myDialogManager.registerDialogHandler(GithubCreatePullRequestDialog.class, new TestDialogHandler<GithubCreatePullRequestDialog>() {
      @Override
      public int handleDialog(GithubCreatePullRequestDialog dialog) {
        try {
          Thread.sleep(3000);
        }
        catch (InterruptedException ignore) {
        }
        List<Object> loaded = dialog.getBranches();
        assertContainsElements(loaded, expected);
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    GithubCreatePullRequestAction.createPullRequest(myProject, myProjectRoot);
  }
}
