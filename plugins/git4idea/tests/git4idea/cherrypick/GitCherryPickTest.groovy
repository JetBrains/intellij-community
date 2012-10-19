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

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.vcs.FilePathImpl
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.testFramework.vcs.MockChangeListManager
import com.intellij.testFramework.vcs.MockContentRevision
import git4idea.history.browser.GitCherryPicker
import git4idea.history.browser.GitCommit
import git4idea.history.browser.SHAHash
import git4idea.history.wholeTree.AbstractHash
import git4idea.test.GitFastTest
import git4idea.test.GitLightRepository
import git4idea.test.MockGit
import git4idea.test.MockVcsHelper
import sun.security.provider.SHA

import static MockGit.OperationName.CHERRY_PICK
import static MockGit.commitMessageForCherryPick
import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertTrue
import git4idea.test.TestNotificator
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile

/**
 * Common parent for all tests on cherry-pick
 *
 * @author Kirill Likhodedov
 */
class GitCherryPickTest extends GitFastTest {
  
  public static final String DEFAULT = MockChangeListManager.DEFAULT_CHANGE_LIST_NAME;
  public static final String UNMERGED_FILE = """
100644 d87b28d6fd6e97620603e64ce70fc2f24535ec28 1\ttest.txt
100644 7b50450f5deb7cce3b5ce92ba866f1af6e58c3c6 2\ttest.txt
100644 a784477cdd0437a84751c52f72b971503deb48cb 3\ttest.txt
"""
  public static final String CHERRY_PICK_CONFLICT = """
error: could not apply ec15d8e... message
hint: after resolving the conflicts, mark the corrected paths
hint: with 'git add <paths>' or 'git rm <paths>'
hint: and commit the result with 'git commit'
"""
  GitCherryPicker myCherryPicker
  GitLightRepository myRepository
  GitLightRepository.Commit myInitialCommit
  TestNotificator myTestNotificator

  static final LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK =
    """
    error: Your local changes to the following files would be overwritten by merge:
    \ttest.txt
    Please, commit your changes or stash them before you can merge.
    Aborting
    """;

  static final UNTRACKED_FILES_OVERWRITTEN_BY_CHERRY_PICK =
    """
    error: The following untracked working tree files would be overwritten by merge:
    \tcp.txt
    Please move or remove them before you can merge.
    Aborting
    """

  void setUp() {
    super.setUp()

    myRepository = new GitLightRepository()
    myRepositoryManager.add(myRepository)
    myInitialCommit = myRepository.commit("initial")
    myTestNotificator = myPlatformFacade.getNotificator(myProject) as TestNotificator;
  }

  GitCommit commit(String commitMessage = "plain commit") {
    AbstractHash hash = AbstractHash.create(Integer.toHexString(new SHA().hashCode()))
    List<Change> changes = new ArrayList<Change>();
    changes.add(new Change(null, new MockContentRevision(new FilePathImpl(new MockVirtualFile("name")), VcsRevisionNumber.NULL)));
    new GitCommit(NullVirtualFile.INSTANCE, hash, SHAHash.emulate(hash), "John Smith", null, null, commitMessage, commitMessage,
                  null, null, null, null, null, null, null, changes, 0)
  }

  void assertOnlyDefaultChangelist() {
    assertChangeLists( [DEFAULT], DEFAULT)
  }

  void invokeCherryPick(GitCommit commit) {
    invokeCherryPick([commit])
  }

  void invokeCherryPick(List<GitCommit> commits) {
    myCherryPicker.cherryPick(Collections.singletonMap(myRepository, commits))
  }

  void assertHeadCommit(GitCommit commit) {
    assertEquals "Wrong commit at the HEAD", commitMessageForCherryPick(commit), myRepository.head.commitMessage
  }

  void assertLastCommits(GitCommit... commits) {
    GitLightRepository.Commit current = myRepository.head
    int level = 0;
    for (GitCommit commit : commits) {
      assertEquals "Wrong commit at level $level", commitMessageForCherryPick(commit), current.commitMessage
      current = current.parent
      level++;
    }
  }

  void assertChangeLists(Collection<String> changeLists, String activeChangelist) {
    ChangeListManager changeListManager = myPlatformFacade.getChangeListManager(myProject)
    List<LocalChangeList> lists = changeListManager.changeLists
    Collection<String> listNames = lists.collect { it.name }
    assertEquals "Change lists are different", changeLists.toSet(), listNames.toSet()
    assertEquals "Wrong active changelist", activeChangelist, changeListManager.defaultChangeList.name
  }

  String commitDetails(GitCommit commit) {
    "${commit.shortHash.toString()} \"${commit.subject}\""
  }

  String notificationContent(GitCommit... commits) {
    commits.collect { commitDetails(it) }.join("<br/>")
  }

  void assertNotCherryPicked() {
    // 1. assert not committed (i.e. git cherry-pick was not performed)
    assertNothingCommitted()
    // 2. assert working tree not changed (i.e. git cherry-pick -n was not performed either)
    assertTrue myPlatformFacade.getChangeListManager(myProject).getAllChanges().isEmpty()
  }

  void assertNothingCommitted() {
    assertEquals(myInitialCommit, myRepository.head)
  }

  void prepareConflict() {
    myGit.registerOperationExecutors(new MockGit.SimpleErrorOperationExecutor(CHERRY_PICK, CHERRY_PICK_CONFLICT),
                                     new MockGit.SimpleSuccessOperationExecutor(MockGit.OperationName.GET_UNMERGED_FILES, UNMERGED_FILE))
  }

  void assertMergeDialogShown() {
    assertTrue "Merge dialog was not shown", myVcsHelper.mergeDialogWasShown()
  }

  String newCommitMessage(GitCommit commit) {
    "${commit.description}\n(cherry-picked from ${commit.hash.value})"
  }

  protected static class OKCommitDialogHandler implements MockVcsHelper.CommitHandler {

    private final GitLightRepository myRepository;
    boolean myCommitDialogShown

    OKCommitDialogHandler(GitLightRepository repository) {
      myRepository = repository
    }

    @Override
    boolean commit(String commitMessage) {
      myCommitDialogShown = true;
      myRepository.commit(commitMessage) // answering OK in the dialog => committing
      return true;
    }

    boolean wasCommitDialogShown() {
      myCommitDialogShown
    }
  }

  protected static class CancelCommitDialogHandler implements MockVcsHelper.CommitHandler {

    boolean myCommitDialogShown

    @Override
    boolean commit(String commitMessage) {
      myCommitDialogShown = true;
      return false;
    }

    boolean wasCommitDialogShown() {
      myCommitDialogShown
    }
  }

}
