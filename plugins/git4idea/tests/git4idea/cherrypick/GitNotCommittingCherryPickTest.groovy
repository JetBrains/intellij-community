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

import com.intellij.notification.NotificationType
import git4idea.history.browser.GitCherryPicker
import git4idea.history.browser.GitCommit
import git4idea.test.GitLightRepository
import git4idea.test.MockGit
import git4idea.test.MockVcsHelper
import org.junit.Before
import org.junit.Test

import static MockGit.OperationName.CHERRY_PICK
import static junit.framework.Assert.*

/**
 * Tests for {@link GitCherryPicker}, when the "auto-commit on cherry-pick" option is deselected.
 * Most situations are equal or similar, so the majority of cherry pick tests are located in {@link GitAutoCommittingCherryPickTest}.
 *
 * @author Kirill Likhodedov
 */
class GitNotCommittingCherryPickTest extends GitCherryPickTest {

  @Before
  void setUp() {
    super.setUp()
    myCherryPicker = new GitCherryPicker(myProject, myGit, myPlatformFacade, false)
  }

  @Test
  void "clean tree, no conflicts, then show commit dialog, commit on ok"() {
    GitCommit commit = commit()

    myGit.registerOperationExecutors(new MockGit.SuccessfulCherryPickExecutor(myRepository, commit))
    OKCommitDialogHandler handler = new OKCommitDialogHandler(myRepository)
    myVcsHelper.registerHandler(handler)

    invokeCherryPick(commit)

    assertHeadCommit(commit)
    assertOnlyDefaultChangelist()
    assertTrue "Commit dialog was not shown", handler.wasCommitDialogShown()
    // notification is shown from the successful commit, can't check from here
  }

  @Test
  void "cancel in commit dialog shouldn't show a notification"() {
    GitCommit commit = commit()

    myGit.registerOperationExecutors(new MockGit.SuccessfulCherryPickExecutor(myRepository, commit))
    CancelCommitDialogHandler handler = new CancelCommitDialogHandler()
    myVcsHelper.registerHandler(handler)

    invokeCherryPick(commit)

    assertTrue "Commit dialog was not shown", handler.wasCommitDialogShown()
    assertNull "Notification should not be shown when cancelling a single commit", myTestNotificator.lastNotification
  }

  @Test
  void "cancel in 2nd commit dialog after successful commit should shown a notification"() {
    GitCommit commit1 = commit()
    GitCommit commit2 = commit()

    myGit.registerOperationExecutors(new MockGit.SimpleSuccessOperationExecutor(CHERRY_PICK, ""),
                                     new MockGit.SimpleSuccessOperationExecutor(CHERRY_PICK, ""))

    int dialogNumber = 0;
    MockVcsHelper.CommitHandler handler = new MockVcsHelper.CommitHandler() {
      boolean commit(String commitMessage) {
        dialogNumber++;
        // answer OK in the first dialog, Cancel - in the second.
        return dialogNumber == 1;
      }
    }
    myVcsHelper.registerHandler(handler)

    invokeCherryPick([commit1, commit2])

    assertEquals "Commit dialog shown wrong number of times", 2, dialogNumber
    assertNotificationShown("Cherry-pick cancelled",
                            """
                            ${commitDetails(commit2)}
                            <hr/>
                            However cherry-pick succeeded for the following commit:<br/>
                            ${commitDetails(commit1)}
                            """, NotificationType.WARNING)
  }

  @Test
  void "dirty tree, conflicting with commit, then show error"() {
    myGit.registerOperationExecutors(new MockGit.SimpleErrorOperationExecutor(CHERRY_PICK, LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK))

    def commit = commit()
    OKCommitDialogHandler handler = new OKCommitDialogHandler(myRepository)
    myVcsHelper.registerHandler(handler)

    invokeCherryPick(commit)

    assertNotCherryPicked()
    assertFalse "Commit dialog was shown, but it shouldn't", handler.wasCommitDialogShown()
    assertOnlyDefaultChangelist()
    assertNotificationShown("Cherry-pick failed",
                            """
                            ${commitDetails(commit)}<br/>
                            Your local changes would be overwritten by cherry-pick.<br/>
                            Commit your changes or stash them to proceed.
                            """,
                            NotificationType.ERROR)
  }

  @Test
  void "conflict, merge ok, commit cancelled, then new & active changelist"() {
    prepareConflict()

    CancelCommitDialogHandler handler = new CancelCommitDialogHandler()
    myVcsHelper.registerHandler(handler)

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", handler.wasCommitDialogShown()
    assertChangeLists([DEFAULT, newCommitMessage(commit)], newCommitMessage(commit))
  }
  
  @Test
  void "2 simple commits in a row, then 2 commit dialogs in a row"() {
    GitCommit commit1 = commit()
    GitCommit commit2 = commit()

    myGit.registerOperationExecutors(new MockGit.SimpleSuccessOperationExecutor(CHERRY_PICK, ""),
                                     new MockGit.SimpleSuccessOperationExecutor(CHERRY_PICK, ""))

    CountingOKCommitHandler handler = new CountingOKCommitHandler(myRepository)
    myVcsHelper.registerHandler(handler)

    invokeCherryPick([commit1, commit2])

    assertOnlyDefaultChangelist()
    assertEquals "Commit dialog shown wrong number of times", 2, handler.myCommitDialogs
    assertLastCommits(commit2, commit1)
  }

  @Test
  void "3 commits, 2nd conflicts with committed, then 1st success, on 2nd show merge dialog"() {
    GitCommit commit1 = commit("First")
    GitCommit commit2 = commit("Second")
    GitCommit commit3 = commit("Third")

    myGit.registerOperationExecutors(new MockGit.SimpleSuccessOperationExecutor(CHERRY_PICK, ""))
    prepareConflict()
    myGit.registerOperationExecutors(new MockGit.SimpleSuccessOperationExecutor(CHERRY_PICK, ""))

    CountingOKCommitHandler handler = new CountingOKCommitHandler(myRepository)
    myVcsHelper.registerHandler(handler)

    invokeCherryPick([commit1, commit2, commit3])

    assertMergeDialogShown()
    assertEquals "Commit dialog shown wrong number of times", 3, handler.myCommitDialogs
    assertLastCommits commit3, commit2, commit1
  }

  private static class CountingOKCommitHandler implements MockVcsHelper.CommitHandler {

    GitLightRepository myRepository
    int myCommitDialogs;

    CountingOKCommitHandler(GitLightRepository repository) {
      myRepository = repository
    }

    @Override
    boolean commit(String commitMessage) {
      myCommitDialogs++
      myRepository.commit(commitMessage)
      return true;
    }

  }

}
