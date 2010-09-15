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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.history.GitHistoryUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for low-level history methods in GitHistoryUtils.
 * There are some known problems with newlines and whitespaces in commit messages, these are ignored by the tests for now.
 * (see #convertWhitespacesToSpacesAndRemoveDoubles).
 *
 * @author Kirill Likhodedov
 */
public class GitHistoryUtilsTestCase extends GitTestCase {

  private VirtualFile afile;
  private FilePath bfilePath;
  private List<GitTestRevision> myRevisions;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRevisions = new ArrayList<GitTestRevision>(3);

    final String[] commitMessages = {
      "simple commit",
      "commit with {%n} some [%ct] special <format:%H%at> characters including \"--pretty=tformat:%x00%x01%x00%H%x00%ct%x00%an%x20%x3C%ae%x3E%x00%cn%x20%x3C%ce%x3E%x00%x02%x00%s%x00%b%x00%x02%x01\"",
      "commit subject\n\ncommit body which is \n multilined.",
      "first line\n" +
        "second line\n" +
        "third line\n" +
        "\n" +
        "fifth line\n" +
        "\n" +
        "seventh line & the end.",
      "moved a.txt to dir/b.txt"
    };
    final String[] contents = {
      "initial content",
      "second content",
      "third content",
      "fourth content",
      "fourth content" // content is the same after rename
    };

    int index = 0;
    afile = myRepo.createFile("a.txt", contents[index]);
    myRepo.addCommit(commitMessages[index]);
    index++;
    editFileInCommand(myProject, afile, contents[index]);
    myRepo.addCommit(commitMessages[index]);
    index++;
    editFileInCommand(myProject, afile, contents[index]);
    myRepo.addCommit(commitMessages[index]);
    index++;
    editFileInCommand(myProject, afile, contents[index]);
    myRepo.addCommit(commitMessages[index]);
    index++;

    VirtualFile dir = myRepo.getDirFixture().findOrCreateDir("dir");
    myRepo.mv(afile, "dir/b.txt");
    bfilePath = VcsUtil.getFilePath(new File(dir.getPath(), "b.txt"));
    myRepo.commit(commitMessages[index]);
    index++;

    // Retrieve hashes and timestamps
    String[] revisions = myRepo.log("--pretty=format:%H#%at").getStdout().split("\n");
    int length = revisions.length;
    // later revisions fo first in the log output
    for (int i = length-1, j = 0; i >= 0; i--, j++) {
      String[] details = revisions[j].trim().split("#");
      myRevisions.add(new GitTestRevision(details[0], details[1], commitMessages[i],
                                          String.format("%s <%s>", CONFIG_USER_NAME, CONFIG_USER_EMAIL), null, contents[i]));
    }
  }

  @Test
  public void testGetCurrentRevision() throws Exception {
    GitRevisionNumber revisionNumber = (GitRevisionNumber) GitHistoryUtils.getCurrentRevision(myProject, VcsUtil.getFilePath(afile.getPath()));
    assertEquals(revisionNumber.getRev(), myRevisions.get(0).myHash);
    assertEquals(revisionNumber.getTimestamp(), myRevisions.get(0).myDate);
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
      public void consume(GitFileRevision gitFileRevision) {
        revisions.add(gitFileRevision);
      }
    };
    Consumer<VcsException> exceptionConsumer = new Consumer<VcsException>() {
      public void consume(VcsException exception) {
        fail("No exception expected", exception);
      }
    };
    GitHistoryUtils.history(myProject, bfilePath, consumer, exceptionConsumer);
    assertEquals(revisions.size(), myRevisions.size());
    for (int i = 0; i < revisions.size(); i++) {
      assertEqualRevisions(revisions.get(i), myRevisions.get(i));
    }
  }

  private static void assertEqualRevisions(GitFileRevision actual, GitTestRevision expected) throws IOException {
    assertEquals(((GitRevisionNumber) actual.getRevisionNumber()).getRev(), expected.myHash);
    assertEquals(((GitRevisionNumber) actual.getRevisionNumber()).getTimestamp(), expected.myDate);
    // TODO: whitespaces problem is known, remove replace(...) when it's fixed
    assertEquals(convertWhitespacesToSpacesAndRemoveDoubles(actual.getCommitMessage()), convertWhitespacesToSpacesAndRemoveDoubles(expected.myCommitMessage));
    assertEquals(actual.getAuthor(), expected.myAuthor);
    assertEquals(actual.getBranchName(), expected.myBranchName);
    assertEquals(actual.getContent(), expected.myContent);
  }

  private static String convertWhitespacesToSpacesAndRemoveDoubles(String s) {
    return s.replaceAll("[\\s^ ]", " ").replaceAll(" +", " ");
  }

  private static class GitTestRevision {
    final String myHash;
    final Date myDate;
    final String myCommitMessage;
    final String myAuthor;
    final String myBranchName;
    final byte[] myContent;

    public GitTestRevision(String hash, String gitTimestamp, String commitMessage, String author, String branch, String content) {
      myHash = hash;
      myDate = new Date(Long.parseLong(gitTimestamp)*1000);
      myCommitMessage = commitMessage;
      myAuthor = author;
      myBranchName = branch;
      myContent = content.getBytes();
    }
  }
  
}
