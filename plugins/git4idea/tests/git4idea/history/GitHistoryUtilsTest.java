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
package git4idea.history;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.config.GitVersion;
import git4idea.history.browser.SHAHash;
import git4idea.test.GitSingleRepoTest;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortHash;
import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.*;
import static git4idea.test.GitExecutor.*;
import static git4idea.test.GitTestUtil.USER_EMAIL;
import static git4idea.test.GitTestUtil.USER_NAME;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for low-level history methods in GitHistoryUtils.
 * There are some known problems with newlines and whitespaces in commit messages, these are ignored by the tests for now.
 * (see #convertWhitespacesToSpacesAndRemoveDoubles).
 */
public class GitHistoryUtilsTest extends GitSingleRepoTest {

  private File bfile;
  private List<GitTestRevision> myRevisions;
  private List<GitTestRevision> myRevisionsAfterRename;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRevisions = new ArrayList<>(7);
    myRevisionsAfterRename = new ArrayList<>(4);

    // 1. create a file
    // 2. simple edit with a simple commit message
    // 3. move & rename
    // 4. make 4 edits with commit messages of different complexity
    // (note: after rename, because some GitHistoryUtils methods don't follow renames).

    final String[] commitMessages = {
      "initial commit",
      "simple commit",
      "moved a.txt to dir/b.txt",
      "simple commit after rename",
      "commit with {%n} some [%ct] special <format:%H%at> characters including --pretty=tformat:%x00%x01%x00%H%x00%ct%x00%an%x20%x3C%ae%x3E%x00%cn%x20%x3C%ce%x3E%x00%x02%x00%s%x00%b%x00%x02%x01",
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

    // initial
    int commitIndex = 0;
    File afile = touch("a.txt", contents[commitIndex]);
    addCommit(repo, commitMessages[commitIndex]);
    commitIndex++;

    // modify
    overwrite(afile, contents[commitIndex]);
    addCommit(repo, commitMessages[commitIndex]);
    int RENAME_COMMIT_INDEX = commitIndex;
    commitIndex++;

    // mv to dir
    File dir = mkdir("dir");
    bfile = new File(dir.getPath(), "b.txt");
    assertFalse("File " + bfile + " shouldn't have existed", bfile.exists());
    mv(repo, afile, bfile);
    assertTrue("File " + bfile + " was not created by mv command", bfile.exists());
    commit(repo, commitMessages[commitIndex]);
    commitIndex++;

    // modifications
    for (int i = 0; i < 4; i++) {
      overwrite(bfile, contents[commitIndex]);
      addCommit(repo, commitMessages[commitIndex]);
      commitIndex++;
    }

    // Retrieve hashes and timestamps
    String[] revisions = log(repo, "--pretty=format:%H#%at#%P", "-M").split("\n");
    assertEquals("Incorrect number of revisions", commitMessages.length, revisions.length);
    // newer revisions go first in the log output
    for (int i = revisions.length - 1, j = 0; i >= 0; i--, j++) {
      String[] details = revisions[j].trim().split("#");
      GitTestRevision revision = new GitTestRevision(details[0], details[1], commitMessages[i],
                                                     USER_NAME, USER_EMAIL, USER_NAME, USER_EMAIL, null,
                                                     contents[i]);
      myRevisions.add(revision);
      if (i > RENAME_COMMIT_INDEX) {
        myRevisionsAfterRename.add(revision);
      }
    }

    assertEquals("setUp failed", 5, myRevisionsAfterRename.size());
    cd(projectPath);
    updateChangeListManager();
  }

  @Override
  protected boolean makeInitialCommit() {
    return false;
  }

  // Inspired by IDEA-89347
  public void testCyclicRename() throws Exception {
    List<TestCommit> commits = new ArrayList<>();

    File source = mkdir("source");
    File initialFile = touch("source/PostHighlightingPass.java", "Initial content");
    String initMessage = "Created PostHighlightingPass.java in source";
    addCommit(repo, initMessage);
    String hash = last(this);
    commits.add(new TestCommit(hash, initMessage, initialFile.getPath()));

    String filePath = initialFile.getPath();

    commits.add(modify(filePath));

    TestCommit commit = move(filePath, mkdir("codeInside-impl"), "Moved from source to codeInside-impl");
    filePath = commit.myPath;
    commits.add(commit);
    commits.add(modify(filePath));

    commit = move(filePath, mkdir("codeInside"), "Moved from codeInside-impl to codeInside");
    filePath = commit.myPath;
    commits.add(commit);
    commits.add(modify(filePath));

    commit = move(filePath, mkdir("lang-impl"), "Moved from codeInside to lang-impl");
    filePath = commit.myPath;
    commits.add(commit);
    commits.add(modify(filePath));

    commit = move(filePath, source, "Moved from lang-impl back to source");
    filePath = commit.myPath;
    commits.add(commit);
    commits.add(modify(filePath));

    commit = move(filePath, mkdir("java"), "Moved from source to java");
    filePath = commit.myPath;
    commits.add(commit);
    commits.add(modify(filePath));

    Collections.reverse(commits);
    VirtualFile vFile = VcsUtil.getVirtualFileWithRefresh(new File(filePath));
    assertNotNull(vFile);
    List<VcsFileRevision> history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(vFile));
    assertEquals("History size doesn't match. Actual history: \n" + toReadable(history), commits.size(), history.size());
    assertEquals("History is different.", toReadable(commits), toReadable(history));
  }

  private static class TestCommit {
    private final String myHash;
    private final String myMessage;
    private final String myPath;

    public TestCommit(String hash, String message, String path) {
      myHash = hash;
      myMessage = message;
      myPath = path;
    }

    public String getHash() {
      return myHash;
    }

    public String getCommitMessage() {
      return myMessage;
    }
  }

  private TestCommit move(String file, File dir, String message) {
    final String NAME = "PostHighlightingPass.java";
    mv(repo, file, dir.getPath());
    file = new File(dir, NAME).getPath();
    addCommit(repo, message);
    String hash = last(this);
    return new TestCommit(hash, message, file);
  }

  private TestCommit modify(String file) throws IOException {
    FileUtil.appendToFile(new File(file), "Modified");
    String message = "Modified PostHighlightingPass";
    addCommit(repo, message);
    String hash = last(this);
    return new TestCommit(hash, message, file);
  }

  @NotNull
  private String toReadable(@NotNull Collection<VcsFileRevision> history) {
    int maxSubjectLength = findMaxLength(history, revision -> revision.getCommitMessage());
    StringBuilder sb = new StringBuilder();
    for (VcsFileRevision revision : history) {
      GitFileRevision rev = (GitFileRevision)revision;
      String relPath = FileUtil.getRelativePath(new File(projectPath), rev.getPath().getIOFile());
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", getShortHash(rev.getHash()), rev.getCommitMessage(), relPath));
    }
    return sb.toString();
  }

  private String toReadable(List<TestCommit> commits) {
    int maxSubjectLength = findMaxLength(commits, revision -> revision.getCommitMessage());
    StringBuilder sb = new StringBuilder();
    for (TestCommit commit : commits) {
      String relPath = FileUtil.getRelativePath(new File(projectPath), new File(commit.myPath));
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", getShortHash(commit.getHash()), commit.getCommitMessage(), relPath));
    }
    return sb.toString();
  }

  private static <T> int findMaxLength(@NotNull Collection<T> list, @NotNull Function<T, String> convertor) {
    int max = 0;
    for (T element : list) {
      int length = convertor.fun(element).length();
      if (length > max) {
        max = length;
      }
    }
    return max;
  }

  public void testGetCurrentRevision() throws Exception {
    GitRevisionNumber revisionNumber = (GitRevisionNumber)GitHistoryUtils.getCurrentRevision(myProject, toFilePath(bfile), null);
    assertEquals(revisionNumber.getRev(), myRevisions.get(0).myHash);
    assertEquals(revisionNumber.getTimestamp(), myRevisions.get(0).myDate);
  }

  public void testGetCurrentRevisionInMasterBranch() throws Exception {
    GitRevisionNumber revisionNumber = (GitRevisionNumber)GitHistoryUtils.getCurrentRevision(myProject, toFilePath(bfile), "master");
    assertEquals(revisionNumber.getRev(), myRevisions.get(0).myHash);
    assertEquals(revisionNumber.getTimestamp(), myRevisions.get(0).myDate);
  }

  public void testGetCurrentRevisionInOtherBranch() throws Exception {
    checkout(repo, "-b feature");
    overwrite(bfile, "new content");
    addCommit(repo, "new content");
    final String[] output = log(repo, "master --pretty=%H#%at", "-n1").trim().split("#");

    GitRevisionNumber revisionNumber = (GitRevisionNumber)GitHistoryUtils.getCurrentRevision(myProject, toFilePath(bfile), "master");
    assertEquals(revisionNumber.getRev(), output[0]);
    assertEquals(revisionNumber.getTimestamp(), GitTestRevision.gitTimeStampToDate(output[1]));
  }

  @NotNull
  private static FilePath toFilePath(@NotNull File file) {
    return VcsUtil.getFilePath(file);
  }

  public void testGetLastRevisionForExistingFile() throws Exception {
    final ItemLatestState state = GitHistoryUtils.getLastRevision(myProject, toFilePath(bfile));
    assertTrue(state.isItemExists());
    final GitRevisionNumber revisionNumber = (GitRevisionNumber)state.getNumber();
    assertEquals(revisionNumber.getRev(), myRevisions.get(0).myHash);
    assertEquals(revisionNumber.getTimestamp(), myRevisions.get(0).myDate);
  }

  public void testGetLastRevisionForNonExistingFile() throws Exception {
    git("remote add origin git://example.com/repo.git");
    git("config branch.master.remote origin");
    git("config branch.master.merge refs/heads/master");

    git("rm " + bfile.getPath());
    commit(repo, "removed bfile");
    String[] hashAndDate = log(repo, "--pretty=format:%H#%ct", "-n1").split("#");
    git("update-ref refs/remotes/origin/master HEAD"); // to avoid pushing to this fake origin

    touch("dir/b.txt", "content");
    addCommit(repo, "recreated bfile");

    refresh();
    repo.update();

    final ItemLatestState state = GitHistoryUtils.getLastRevision(myProject, toFilePath(bfile));
    assertTrue(!state.isItemExists());
    final GitRevisionNumber revisionNumber = (GitRevisionNumber)state.getNumber();
    assertEquals(revisionNumber.getRev(), hashAndDate[0]);
    assertEquals(revisionNumber.getTimestamp(), GitTestRevision.gitTimeStampToDate(hashAndDate[1]));
  }

  public void testHistory() throws Exception {
    List<VcsFileRevision> revisions = GitFileHistory.collectHistory(myProject, toFilePath(bfile));
    assertHistory(revisions);
  }

  public void testAppendableHistory() throws Exception {
    final List<GitFileRevision> revisions = new ArrayList<>(3);
    Consumer<GitFileRevision> consumer = gitFileRevision -> revisions.add(gitFileRevision);
    Consumer<VcsException> exceptionConsumer = exception -> fail("No exception expected " + ExceptionUtil.getThrowableText(exception));
    GitFileHistory.loadHistory(myProject, toFilePath(bfile), repo.getRoot(), null, consumer, exceptionConsumer);
    assertHistory(revisions);
  }

  public void testOnlyHashesHistory() throws Exception {
    final List<Pair<SHAHash, Date>> history = GitHistoryUtils.onlyHashesHistory(myProject, toFilePath(bfile), projectRoot);
    assertEquals(history.size(), myRevisionsAfterRename.size());
    Iterator<GitTestRevision> itAfterRename = myRevisionsAfterRename.iterator();
    for (Pair<SHAHash, Date> pair : history) {
      GitTestRevision revision = itAfterRename.next();
      assertEquals(pair.first.toString(), revision.myHash);
      assertEquals(pair.second, revision.myDate);
    }
  }

  public void testLoadingDetailsWithU0001Character() throws Exception {
    List<VcsFullCommitDetails> details = ContainerUtil.newArrayList();

    String message = "subject containing \u0001 symbol in it\n\ncommit body containing \u0001 symbol in it";
    touch("file.txt", "content");
    addCommit(repo, message);

    GitHistoryUtils.loadDetails(myProject, repo.getRoot(), details::add);

    VcsFullCommitDetails lastCommit = ContainerUtil.getFirstItem(details);
    assertNotNull(lastCommit);
    assertEquals(message, lastCommit.getFullMessage());
  }

  public void testLoadingDetailsWithoutChanges() throws Exception {
    assumeTrue("Not testing: Git doesn't know --allow-empty-message in " + vcs.getVersion(),
               vcs.getVersion().isLaterOrEqual(new GitVersion(1, 7, 2, 0)));

    List<String> expected = ContainerUtil.newArrayList();

    String messageFile = "message.txt";
    touch(messageFile, "");

    int commitCount = 100;
    for (int i = 0; i < commitCount; i++) {
      echo("file.txt", "content number " + i);
      add(repo);
      git("commit --allow-empty-message -F " + messageFile);
      expected.add(last(this));
    }
    expected = ContainerUtil.reverse(expected);

    List<String> actualHashes = ContainerUtil.map(GitLogUtil.collectFullDetails(myProject, repo.getRoot(),
                                                                                 "--max-count=" + commitCount),
                                                  detail -> detail.getId().asString());

    assertEquals(expected, actualHashes);
  }

  private void assertHistory(@NotNull List<? extends VcsFileRevision> actualRevisions) throws VcsException {
    assertEquals("Incorrect number of commits in history", myRevisions.size(), actualRevisions.size());
    for (int i = 0; i < actualRevisions.size(); i++) {
      assertEqualRevisions((GitFileRevision)actualRevisions.get(i), myRevisions.get(i));
    }
  }

  private static void assertEqualRevisions(GitFileRevision actual, GitTestRevision expected) throws VcsException {
    String actualRev = ((GitRevisionNumber)actual.getRevisionNumber()).getRev();
    assertEquals(expected.myHash, actualRev);
    assertEquals(expected.myDate, ((GitRevisionNumber)actual.getRevisionNumber()).getTimestamp());
    // TODO: whitespaces problem is known, remove convertWhitespaces... when it's fixed
    assertEquals(convertWhitespacesToSpacesAndRemoveDoubles(expected.myCommitMessage),
                 convertWhitespacesToSpacesAndRemoveDoubles(actual.getCommitMessage()));
    assertEquals(expected.myAuthorName, actual.getAuthor());
    assertEquals(expected.myBranchName, actual.getBranchName());
    assertNotNull("No content in revision " + actualRev, actual.getContent());
    assertEquals(new String(expected.myContent), new String(actual.getContent()));
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

    public GitTestRevision(String hash,
                           String gitTimestamp,
                           String commitMessage,
                           String authorName,
                           String authorEmail,
                           String committerName,
                           String committerEmail,
                           String branch,
                           String content) {
      myHash = hash;
      myDate = gitTimeStampToDate(gitTimestamp);
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
      return new Date(Long.parseLong(gitTimestamp) * 1000);
    }
  }
}
