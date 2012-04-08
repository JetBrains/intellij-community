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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import git4idea.history.browser.CherryPicker
import git4idea.history.browser.GitCommit
import git4idea.test.MockVcsHelper
import org.junit.Before
import org.junit.Test

import static git4idea.test.MockGit.*
import static git4idea.test.MockGit.OperationName.CHERRY_PICK
import static git4idea.test.MockGit.OperationName.GET_UNMERGED_FILES
import static junit.framework.Assert.assertTrue

/**
 * Cherry-pick of one or multiple commits with "commit at once" option enabled.
 *
 * @author Kirill Likhodedov
 */
class GitAutoCommittingCherryPickTest extends GitCherryPickTest {

  public static final String EMPTY_CHERRY_PICK = """
# On branch master
# Your branch is ahead of 'origin/master' by 11 commits.
#
# Untracked files:
#   (use "git add <file>..." to include in what will be committed)
#
#\t.idea/
#\tlocal_staged.patch
#\tout/
nothing added to commit but untracked files present (use "git add" to track)
The previous cherry-pick is now empty, possibly due to conflict resolution.
If you wish to commit it anyway, use:

    git commit --allow-empty

Otherwise, please use 'git reset'
"""

  @Before
  void setUp() {
    super.setUp()
    myCherryPicker = new CherryPicker(myProject, myGit, myPlatformFacade, true)
  }

  @Test
  void "clean tree, no conflicts, then commit & notify, no new changelists"() {
    GitCommit commit = commit()

    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit.subject))
    invokeCherryPick(commit)

    assertHeadCommit(commit)
    assertOnlyDefaultChangelist()
    assertNotificationShown("Successful cherry-pick", notificationContent(commit), NotificationType.INFORMATION)
  }

  @Test
  void "dirty tree, conflicting with commit, then show error"() {
    myGit.registerOperationExecutors(new SimpleErrorOperationExecutor(CHERRY_PICK, LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK))

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
    myGit.registerOperationExecutors(new SimpleSuccessOperationExecutor(GET_UNMERGED_FILES, UNMERGED_FILE))
    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertChangeLists([DEFAULT, newCommitMessage(commit)], newCommitMessage(commit))
  }

  String newCommitMessage(GitCommit commit) {
    "${commit.description}\n(cherry-picked from ${commit.hash.value})"
  }

  @Test
  void "conflict, merge completed, then commit dialog"() {
    prepareConflict()
    GitCommit commit = commit()

    boolean commitDialogShown = false;
    myVcsHelper.registerHandler(new MockVcsHelper.CommitHandler() {
      @Override
      boolean commit(String commitMessage) {
        commitDialogShown = true;
        return true;
      }
    })

    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", commitDialogShown
  }

  @Test
  void "conflict, merge finished, commit succeeded, no new changelists"() {
    prepareConflict()

    boolean commitDialogShown = false;
    myVcsHelper.registerHandler(new MockVcsHelper.CommitHandler() {
      @Override
      boolean commit(String commitMessage) {
        commitDialogShown = true;
        return true;
      }
    })

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", commitDialogShown
    assertOnlyDefaultChangelist()
  }

  @Test
  void "conflict, merge ok, commit cancelled, then new & active changelist"() {
    prepareConflict()

    boolean commitDialogShown = false;
    myVcsHelper.registerHandler(new MockVcsHelper.CommitHandler() {
      @Override
      boolean commit(String commitMessage) {
        commitDialogShown = true;
        return false;
      }
    })

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", commitDialogShown
    assertChangeLists([DEFAULT, newCommitMessage(commit)], newCommitMessage(commit))
  }

  @Test
  void "2 commits, no problems, then commit all & notify"() {
    GitCommit commit1 = commit("First commit to cherry-pick")
    GitCommit commit2 = commit("Second commit to cherry-pick")
    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1.subject),
                                     new SuccessfulCherryPickExecutor(myRepository, commit2.subject))

    invokeCherryPick([commit1, commit2])
    assertLastCommits commit2, commit1
    assertNotificationShown("Succesfully cherry-picked", notificationContent(commit1, commit2), NotificationType.INFORMATION)
  }

  @Test
  void "3 commits, 2nd cherry-pick conflicts with local, then 1st success, 2nd stop & error"() {
    GitCommit commit1 = commit("First")
    GitCommit commit2 = commit("Second")
    GitCommit commit3 = commit("Third")

    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1.subject),
                                     new SimpleErrorOperationExecutor(CHERRY_PICK, LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK),
                                     new SuccessfulCherryPickExecutor(myRepository, commit3.subject))

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

    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1.subject))
    prepareConflict()
    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit3.subject))

    boolean commitDialogShown = false;
    myVcsHelper.registerHandler(new MockVcsHelper.CommitHandler() {
      @Override
      boolean commit(String commitMessage) {
        commitDialogShown = true;
        return true;
      }
    })

    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", commitDialogShown
    assertLastCommits commit3, commit2, commit1
  }

  @Test
  void "Notify if changes have already been applied"() {
    // Inspired by IDEA-73548
    myGit.registerOperationExecutors(new SimpleErrorOperationExecutor(CHERRY_PICK, EMPTY_CHERRY_PICK))

    GitCommit commit = commit()
    invokeCherryPick(commit)

    assertNotCherryPicked()
    assertNotificationShown("Nothing to cherry-pick", "All changes from ${notificationContent(commit)} have already been applied",
                            NotificationType.WARNING)
  }

  @Test
  void "1st successful, 2nd empty (all applied), then compound notification"() {
    // Inspired by IDEA-73548
    GitCommit commit1 = commit()
    GitCommit commit2 = commit()
    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1.subject),
                                     new SimpleErrorOperationExecutor(CHERRY_PICK, EMPTY_CHERRY_PICK))

    invokeCherryPick([ commit1, commit2 ])

    assertHeadCommit(commit1)
    assertNotificationShown("Cherry-picked with problems",
"""Successfully cherry-picked ${notificationContent(commit1)}<br/>
Not cherry-picked ${notificationContent(commit2)} - all changes have already been applied""", NotificationType.WARNING)
  }

}
