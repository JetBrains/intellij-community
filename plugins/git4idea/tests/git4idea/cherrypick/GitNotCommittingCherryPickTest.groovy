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

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import git4idea.history.browser.CherryPicker
import git4idea.history.browser.GitCommit
import git4idea.tests.TestDialogHandler
import org.junit.Before
import org.junit.Test
import git4idea.test.MockGit

import static git4idea.test.MockGit.OperationName.CHERRY_PICK
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import java.util.concurrent.atomic.AtomicInteger

import static junit.framework.Assert.assertEquals

/**
 * 
 * @author Kirill Likhodedov
 */
class GitNotCommittingCherryPickTest extends GitCherryPickTest {

  @Before
  void setUp() {
    super.setUp()
    myCherryPicker = new CherryPicker(myProject, myGit, myPlatformFacade, false)
  }

  @Test
  void "clean tree, no conflicts, then show commit dialog, commit on ok"() {
    GitCommit commit = commit()
    myDialogManager.registerDialogHandler(CommitChangeListDialog, new TestDialogHandler<CommitChangeListDialog>() {
      @Override
      int handleDialog(CommitChangeListDialog dialog) {
        return DialogWrapper.OK_EXIT_CODE
      }
    })

    invokeCherryPick(commit)

    assertCommitDialogShown()
    assertHeadCommit(commit)
    assertOnlyDefaultChangelist()
    // notification is shown from the successful commit, can't check from here
  }

  @Test
  void "dirty tree, conflicting with commit, then show error"() {
    myGit.registerOperationExecutors(new MockGit.SimpleErrorOperationExecutor(CHERRY_PICK, LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK))

    invokeCherryPick(commit())

    assertNotCherryPicked()
    assertOnlyDefaultChangelist()
    assertNotificationShown(new Notification(TEST_NOTIFICATION_GROUP, "Cherry-pick failed",
                                             "Your local changes to some files would be overwritten by cherry-pick. View them",
                                             NotificationType.ERROR))
  }

  @Test
  void "conflict, merge ok, commit cancelled, then new & active changelist"() {
    prepareConflict()

    myDialogManager.registerDialogHandler(CommitChangeListDialog, new TestDialogHandler<CommitChangeListDialog>() {
      @Override
      int handleDialog(CommitChangeListDialog dialog) {
        return DialogWrapper.CANCEL_EXIT_CODE
      }
    })

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertCommitDialogShown()
    assertChangeLists([DEFAULT, commit.getSubject()], commit.getSubject())
  }

  @Test
  void "3 commits in a row, 2nd with conflict"() {
    GitCommit commit1 = commit("First")
    GitCommit commit2 = commit("Second")
    GitCommit commit3 = commit("Third")

    myGit.registerOperationExecutors(new MockGit.SuccessfulCherryPickExecutor(myRepository, commit1.subject),
                                     new MockGit.SuccessfulCherryPickExecutor(myRepository, commit2.subject),
                                     new MockGit.SuccessfulCherryPickExecutor(myRepository, commit3.subject))
    prepareConflict()

    AtomicInteger commitDialogShown = new AtomicInteger()
    myDialogManager.registerDialogHandler(CommitChangeListDialog, new TestDialogHandler<CommitChangeListDialog>() {
      @Override
      int handleDialog(CommitChangeListDialog dialog) {
        commitDialogShown.incrementAndGet()
        return DialogWrapper.OK_EXIT_CODE
      }
    })

    assertEquals "Commit dialog wasn't shown necessary number of times", 3, commitDialogShown.get()
    assertMergeDialogShown()
    assertCommitDialogShown()
    assertLastCommits commit3, commit2, commit1
  }

}
