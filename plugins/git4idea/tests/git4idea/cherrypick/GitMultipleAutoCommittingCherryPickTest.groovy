/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.cherrypick

import org.junit.Before
import git4idea.history.browser.CherryPicker
import org.junit.Test
import git4idea.history.browser.GitCommit
import com.intellij.notification.NotificationType
import git4idea.test.MockGit

import static git4idea.test.MockGit.OperationName.CHERRY_PICK
import static git4idea.test.MockGit.OperationName.CHERRY_PICK
import static git4idea.test.MockGit.OperationName.CHERRY_PICK
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import git4idea.tests.TestDialogHandler
import com.intellij.openapi.ui.DialogWrapper

/**
 *   // multiple commits with "commit at once" option
 * @author Kirill Likhodedov
 */
class GitMultipleAutoCommittingCherryPickTest extends GitCherryPickTest {

  @Before
  protected void setUp() {
    super.setUp()
    myCherryPicker = new CherryPicker(myProject, myGit, myPlatformFacade, myRepositoryManager, true)
  }

  @Test
  void "2 commits, no problems, then commit all & notify"() {
    GitCommit commit1 = commit("First commit to cherry-pick")
    GitCommit commit2 = commit("Second commit to cherry-pick")
    invokeCherryPick([commit1, commit2])
    assertLastCommits commit2, commit1
    assertNotificationShown("Succesfully cherry-picked", notificationContent(commit1, commit2), NotificationType.INFORMATION)
  }

  @Test
  void "3 commits, 2nd cherry-pick conflicts with local, then 1st success, 2nd stop & error"() {
    GitCommit commit1 = commit("First")
    GitCommit commit2 = commit("Second")
    GitCommit commit3 = commit("Third")

    myGit.registerOperationExecutors(new MockGit.SuccessfulOperationExecutor(CHERRY_PICK),
                                     new MockGit.SimpleErrorOperationExecutor(CHERRY_PICK, LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK))

    invokeCherryPick([commit1, commit2, commit3])

    assertHeadCommit(commit1)
    assertNotificationShown("Cherry-picked with errors",
                            """Successfully cherry-picked ${notificationContent(commit1)}<br/>
Couldn't cherry-pick ${notificationContent(commit2)} because local changes prevent it. View them. <br/>
Didn't cherry-pick others""", NotificationType.ERROR)
  }

  @Test
  void "3 commits, 2nd conflicts with committed, then 1st success, on 2nd show merge dialog"() {
    GitCommit commit1 = commit("First")
    GitCommit commit2 = commit("Second")
    GitCommit commit3 = commit("Third")

    myGit.registerOperationExecutors(new MockGit.SuccessfulOperationExecutor(CHERRY_PICK))
    prepareConflict()

    myDialogManager.registerDialogHandler(CommitChangeListDialog, new TestDialogHandler<CommitChangeListDialog>() {
      @Override
      int handleDialog(CommitChangeListDialog dialog) {
        return DialogWrapper.OK_EXIT_CODE
      }
    })

    assertMergeDialogShown()
    assertCommitDialogShown()
    assertLastCommits commit3, commit2, commit1
  }

}
