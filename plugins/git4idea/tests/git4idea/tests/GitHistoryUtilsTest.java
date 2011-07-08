/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.tests;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.history.wholeTree.CommitHashPlusParents;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.testng.Assert.*;

/**
 * Tests for low-level history methods in GitHistoryUtils.
 * There are some known problems with newlines and whitespaces in commit messages, these are ignored by the tests for now.
 * (see #convertWhitespacesToSpacesAndRemoveDoubles).
 *
 * @author Kirill Likhodedov
 */
public class GitHistoryUtilsTest extends GitSingleUserTest {

  private VirtualFile afile;
  private FilePath bfilePath;
  private VirtualFile bfile;
  private List<GitTestRevision> myRevisions;
  private List<GitTestRevision> myRevisionsAfterRename;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRevisions = new ArrayList<GitTestRevision>(7);
    myRevisionsAfterRename = new ArrayList<GitTestRevision>(4);

    // 1. create a file
    // 2. simple edit with a simple comit message
    // 3. move & rename
    // 4. make 4 edits with commit messages of different complexity
    // (note: after rename, because some GitHistoryUtils methods don't follow renames).

    final String[] commitMessages = {
      "initial commit",
      "simple commit",
      "moved a.txt to dir/b.txt",
      "simple commit after rename",
      "commit with {%n} some [%ct] special <format:%H%at> characters including \"--pretty=tformat:%x00%x01%x00%H%x00%ct%x00%an%x20%x3C%ae%x3E%x00%cn%x20%x3C%ce%x3E%x00%x02%x00%s%x00%b%x00%x02%x01\"",
      "commit subject\n\ncommit body which is \n multilined.",
      "first line\nsecond line\nthird line\n\nfifth line\n\nseventh line & the end.",
    };
    final String[] contents = {
      "initial content",
      "second content",
      "second content", // content is the same after rename
      "fourth content",
      "fifth content",
      "sixth content",
      "seventh content",
    };

    int commitIndex = 0;
    afile = myRepo.createFile("a.txt", contents[commitIndex]);
    myRepo.addCommit(commitMessages[commitIndex]);
    commitIndex++;

    editFileInCommand(myProject, afile, contents[commitIndex]);
    myRepo.addCommit(commitMessages[commitIndex]);
    int RENAME_COMMIT_INDEX = commitIndex;
    commitIndex++;

    VirtualFile dir = myRepo.getDirFixture().findOrCreateDir("dir");
    myRepo.mv(afile, "dir/b.txt");
    myRepo.refresh();
    final File bIOFile = new File(dir.getPath(), "b.txt");
    bfilePath = VcsUtil.getFilePath(bIOFile);
    bfile = VcsUtil.getVirtualFile(bIOFile);
    myRepo.commit(commitMessages[commitIndex]);
    commitIndex++;

    for (int i = 0; i < 4; i++) {
      editFileInCommand(myProject, bfile, contents[commitIndex]);
      myRepo.addCommit(commitMessages[commitIndex]);
      commitIndex++;
    }

    // Retrieve hashes and timestamps
    String[] revisions = myRepo.log("--pretty=format:%H#%at#%P", "-M").getStdout().split("\n");
    int length = revisions.length;
    // later revisions to the first in the log output
    for (int i = length-1, j = 0; i >= 0; i--, j++) {
      String[] details = revisions[j].trim().split("#");
      String[] parents;
      if (details.length > 2) {
        parents = details[2].split(" ");
      } else {
        parents = ArrayUtil.EMPTY_STRING_ARRAY;
      }
      final GitTestRevision revision = new GitTestRevision(details[0], details[1], parents, commitMessages[i],
                                                           CONFIG_USER_NAME, CONFIG_USER_EMAIL, CONFIG_USER_NAME, CONFIG_USER_EMAIL, null,
                                                           contents[i]);
      myRevisions.add(revision);
      if (i > RENAME_COMMIT_INDEX) {
        myRevisionsAfterRename.add(revision);
      }
    }

    assertEquals(myRevisionsAfterRename.size(), 5);
  }

  @Test
  public void testGetCurrentRevision() throws Exception {
    GitRevisionNumber revisionNumber = (GitRevisionNumber) GitHistoryUtils.getCurrentRevision(myProject, bfilePath, null);
    assertEquals(revisionNumber.getRev(), myRevisions.get(0).myHash);
    assertEquals(revisionNumber.getTimestamp(), myRevisions.get(0).myDate);
  }

  @Test
  public void testGetCurrentRevisionInMasterBranch() throws Exception {
    GitRevisionNumber revisionNumber = (GitRevisionNumber) GitHistoryUtils.getCurrentRevision(myProject, bfilePath, "master");
    assertEquals(revisionNumber.getRev(), myRevisions.get(0).myHash);
    assertEquals(revisionNumber.getTimestamp(), myRevisions.get(0).myDate);
  }

  @Test
  public void testGetCurrentRevisionInOtherBranch() throws Exception {
    myRepo.checkout("feature");
    editFileInCommand(myProject, bfile, "new content");
    myRepo.addCommit();
    final String[] output = myRepo.log("--pretty=%H#%at", "-n1").getStdout().trim().split("#");

    GitRevisionNumber revisionNumber = (GitRevisionNumber) GitHistoryUtils.getCurrentRevision(myProject, bfilePath, "master");
    assertEquals(revisionNumber.getRev(), output[0]);
    assertEquals(revisionNumber.getTimestamp(), GitTestRevision.gitTimeStampToDate(output[1]));
  }

  @Test
  public void testGetLastRevisionForExistingFile() throws Exception {
    final ItemLatestState state = GitHistoryUtils.getLastRevision(myProject, bfilePath);
    assertTrue(state.isItemExists());
    final GitRevisionNumber revisionNumber = (GitRevisionNumber) state.getNumber();
    assertEquals(revisionNumber.getRev(), myRevisions.get(0).myHash);
    assertEquals(revisionNumber.getTimestamp(), myRevisions.get(0).myDate);
  }

  // TODO: need to configure a remote branch to run this test
  //@Test
  public void testGetLastRevisionForNonExistingFile() throws Exception {
    myRepo.config("branch.master.remote", "origin");
    myRepo.config("branch.master.merge", "refs/heads/master");
    myRepo.rm(bfilePath.getPath());
    myRepo.commit();
    VirtualFile dir = myRepo.getDirFixture().findOrCreateDir("dir");
    createFileInCommand(dir, "b.txt", "content");
    String[] hashAndData = myRepo.log("--pretty=format:%H#%ct", "-n1").getStdout().split("#");

    final ItemLatestState state = GitHistoryUtils.getLastRevision(myProject, bfilePath);
    assertTrue(!state.isItemExists());
    final GitRevisionNumber revisionNumber = (GitRevisionNumber) state.getNumber();
    assertEquals(revisionNumber.getRev(), hashAndData[0]);
    assertEquals(revisionNumber.getTimestamp(), GitTestRevision.gitTimeStampToDate(hashAndData[1]));
  }

  @Test
  public void testHistory() throws Exception {
        List<VcsFileRevision> revisions = GitHistoryUtils.history(myProject, bfilePath);
    assertEquals(revisions.size(), myRevisions.size());
    for (int i = 0; i < revisions.size(); i++) {
      assertEqualRevisions((GitFileRevision) revisions.get(i), myRevisions.get(i));
    }
  }

  @Test
  public void testAppendableHistory() throws Exception {
    final List<GitFileRevision> revisions = new ArrayList<GitFileRevision>(3);
    Consumer<GitFileRevision> consumer = new Consumer<GitFileRevision>() {
      @Override
      public void consume(GitFileRevision gitFileRevision) {
        revisions.add(gitFileRevision);
      }
    };
    Consumer<VcsException> exceptionConsumer = new Consumer<VcsException>() {
      @Override
      public void consume(VcsException exception) {
        fail("No exception expected", exception);
      }
    };
    GitHistoryUtils.history(myProject, bfilePath, null, consumer, exceptionConsumer);
    assertEquals(revisions.size(), myRevisions.size());
    for (int i = 0; i < revisions.size(); i++) {
      assertEqualRevisions(revisions.get(i), myRevisions.get(i));
    }
  }

  @Test
  public void testOnlyHashesHistory() throws Exception {
    final List<Pair<SHAHash,Date>> history = GitHistoryUtils.onlyHashesHistory(myProject, bfilePath, myRepo.getDir());
    assertEquals(history.size(), myRevisionsAfterRename.size());
    for (Iterator hit = history.iterator(), myIt = myRevisionsAfterRename.iterator(); hit.hasNext(); ) {
      Pair<SHAHash,Date> pair = (Pair<SHAHash, Date>) hit.next();
      GitTestRevision revision = (GitTestRevision)myIt.next();
      assertEquals(pair.first.toString(), revision.myHash);
      assertEquals(pair.second, revision.myDate);
    }
  }

  @Test
  public void testHistoryWithLinks() throws Exception {
    /*List<GitCommit> commits = GitHistoryUtils.historyWithLinks(myProject, bfilePath, Collections.<String>emptySet(), null);
    assertEquals(commits.size(), myRevisionsAfterRename.size());
    for (Iterator hit = commits.iterator(), myIt = myRevisionsAfterRename.iterator(); hit.hasNext(); ) {
      GitCommit commit = (GitCommit)hit.next();
      GitTestRevision revision = (GitTestRevision)myIt.next();
      assertCommitEqualToTestRevision(commit, revision);
    }*/
  }

  /*@Test
  public void testCommitsDetails() throws Exception {
    List<String> ids = new ArrayList<String>(myRevisionsAfterRename.size());
    for (GitTestRevision rev : myRevisionsAfterRename) {
      ids.add(rev.myHash);
    }
    final List<GitCommit> gitCommits = GitHistoryUtils.commitsDetails(myProject, bfilePath, Collections.<String>emptySet(), ids);
    assertCommitsEqualToTestRevisions(gitCommits, myRevisionsAfterRename);
  }*/

  @Test
  public void testHashesWithParents() throws Exception {
    final int expectedSize = myRevisionsAfterRename.size();

    final List<CommitHashPlusParents> hashesWithParents = new ArrayList<CommitHashPlusParents>(3);
    AsynchConsumer<CommitHashPlusParents> consumer = new AsynchConsumer<CommitHashPlusParents>() {
      @Override
      public void consume(CommitHashPlusParents gitFileRevision) {
        hashesWithParents.add(gitFileRevision);
      }

      @Override
      public void finished() {
      }
    };

    GitHistoryUtils.hashesWithParents(myProject, bfilePath, consumer, null, null);

    assertEquals(hashesWithParents.size(), expectedSize);
    for (Iterator hit = hashesWithParents.iterator(), myIt = myRevisionsAfterRename.iterator(); hit.hasNext(); ) {
      CommitHashPlusParents chpp = (CommitHashPlusParents)hit.next();
      GitTestRevision rev = (GitTestRevision)myIt.next();
      assertEquals(chpp.getHash(), rev.myHash);
      final List<AbstractHash> parents = chpp.getParents();
      final ArrayList<String> list = new ArrayList<String>();
      for (AbstractHash parent : parents) {
        list.add(parent.getString());
      }
      assertEqualHashes(list, Arrays.asList(rev.myParents));
    }
  }
  
  private static void assertEqualRevisions(GitFileRevision actual, GitTestRevision expected) throws IOException {
    assertEquals(((GitRevisionNumber) actual.getRevisionNumber()).getRev(), expected.myHash);
    assertEquals(((GitRevisionNumber) actual.getRevisionNumber()).getTimestamp(), expected.myDate);
    // TODO: whitespaces problem is known, remove convertWhitespaces... when it's fixed
    assertEquals(convertWhitespacesToSpacesAndRemoveDoubles(actual.getCommitMessage()), convertWhitespacesToSpacesAndRemoveDoubles(expected.myCommitMessage));
    assertEquals(actual.getAuthor(), expected.myAuthorName);
    assertEquals(actual.getBranchName(), expected.myBranchName);
    try {
      assertEquals(actual.getContent(), expected.myContent);
    }
    catch (VcsException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
  }

  private static void assertCommitEqualToTestRevision(GitCommit commit, GitTestRevision expected) throws IOException {
    assertEquals(commit.getHash().toString(), expected.myHash);
    assertEquals(commit.getAuthor(), expected.myAuthorName);
    assertEquals(commit.getAuthorEmail(), expected.myAuthorEmail);
    assertEquals(commit.getCommitter(), expected.myCommitterName);
    assertEquals(commit.getCommitterEmail(), expected.myCommitterEmail);
    assertEquals(commit.getDate(), expected.myDate);
    assertEquals(convertWhitespacesToSpacesAndRemoveDoubles(commit.getDescription()), convertWhitespacesToSpacesAndRemoveDoubles(expected.myCommitMessage));
    assertEqualHashes(commit.getParentsHashes(), Arrays.asList(expected.myParents));
  }

  private static void assertEqualHashes(Collection<String> actualParents, Collection<String> expectedParents) {
    assertEquals(actualParents.size(), expectedParents.size());
    for (Iterator<String> ait = actualParents.iterator(), eit = expectedParents.iterator(); ait.hasNext(); ) {
      assertTrue(eit.next().startsWith(ait.next()));
    }
  }

  private static void assertCommitsEqualToTestRevisions(Collection<GitCommit> actualCommits, Collection<GitTestRevision> expectedRevisions) throws IOException {
    assertEquals(actualCommits.size(), expectedRevisions.size());
    for (Iterator hit = actualCommits.iterator(), myIt = expectedRevisions.iterator(); hit.hasNext(); ) {
      GitCommit commit = (GitCommit)hit.next();
      GitTestRevision revision = (GitTestRevision)myIt.next();
      assertCommitEqualToTestRevision(commit, revision);
    }
  }

  private static String convertWhitespacesToSpacesAndRemoveDoubles(String s) {
    return s.replaceAll("[\\s^ ]", " ").replaceAll(" +", " ");
  }

  private static class GitTestRevision {
    final String myHash;
    final Date myDate;
    final String myCommitMessage;
    final String myAuthorName;
    final String myAuthorEmail;
    final String myCommitterName;
    final String myCommitterEmail;
    final String myBranchName;
    final byte[] myContent;
    private String[] myParents;

    public GitTestRevision(String hash, String gitTimestamp, String[] parents, String commitMessage, String authorName, String authorEmail, String committerName, String committerEmail, String branch, String content) {
      myHash = hash;
      myDate = gitTimeStampToDate(gitTimestamp);
      myParents = parents;
      myCommitMessage = commitMessage;
      myAuthorName = authorName;
      myAuthorEmail = authorEmail;
      myCommitterName = committerName;
      myCommitterEmail = committerEmail;
      myBranchName = branch;
      myContent = content.getBytes();
    }

    @Override
    public String toString() {
      return myHash;
    }

    public static Date gitTimeStampToDate(String gitTimestamp) {
      return new Date(Long.parseLong(gitTimestamp)*1000);
    }
  }
  
}
