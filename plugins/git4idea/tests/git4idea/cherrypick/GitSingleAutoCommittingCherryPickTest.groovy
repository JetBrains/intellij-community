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
import com.intellij.notification.Notification
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import git4idea.tests.TestDialogHandler
import com.intellij.openapi.ui.DialogWrapper

/**
 *   // single commit with "commit at once" option
 *
 * @author Kirill Likhodedov
 */
class GitSingleAutoCommittingCherryPickTest extends GitCherryPickTest {

  @Before
  void setUp() {
    super.setUp()
    myCherryPicker = new CherryPicker(myProject, myGit, myPlatformFacade, myRepositoryManager, true)
  }

  @Test
  void "clean tree, no conflicts, then commit & notify, no new changelists"() {
    GitCommit commit = commit()
    invokeCherryPick(commit)

    assertHeadCommit(commit)
    assertOnlyDefaultChangelist()
    assertNotificationShown("Successful cherry-pick", notificationContent(commit), NotificationType.INFORMATION)
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
  void "conflict, merge dialog, not all merged, then new & active changelist"() {
    prepareConflict()
    myGit.registerOperationExecutors(new MockGit.SimpleErrorOperationExecutor(MockGit.OperationName.GET_UNMERGED_FILES,
"""
100644 d87b28d6fd6e97620603e64ce70fc2f24535ec28 1\ttest.txt
100644 7b50450f5deb7cce3b5ce92ba866f1af6e58c3c6 2\ttest.txt
100644 a784477cdd0437a84751c52f72b971503deb48cb 3\ttest.txt
"""
    ))

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertChangeLists([DEFAULT, commit.getSubject()], commit.getSubject())
  }

  @Test
  void "conflict, merge completed, then commit dialog"() {
    prepareConflict()
    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertCommitDialogShown()
  }

  @Test
  void "conflict, merge finished, commit succeeded, no new changelists"() {
    prepareConflict()

    myDialogManager.registerDialogHandler(CommitChangeListDialog, new TestDialogHandler<CommitChangeListDialog>() {
      @Override
      int handleDialog(CommitChangeListDialog dialog) {
        return DialogWrapper.OK_EXIT_CODE
      }
    })

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertCommitDialogShown()
    assertOnlyDefaultChangelist()
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
}
