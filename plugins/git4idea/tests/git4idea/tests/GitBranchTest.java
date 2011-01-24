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

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchTest extends GitCollaborativeTest {

  private List<TestBranch> myBranches;
  private VirtualFile myDir;
  private TestBranch myFeatureBranch;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    GitTestUtil.createFileStructure(myProject, myRepo, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt");
    myRepo.commit();
    myRepo.push("origin", "master");

    myBrotherRepo.pull();
    myBrotherRepo.createBranch("feature");
    createFileInCommand(myBrotherRepo.getDir(), "feature.txt", "feature content");
    myBrotherRepo.addCommit();
    myBrotherRepo.push("--all");

    myBrotherRepo.createBranch("eap");
    createFileInCommand(myBrotherRepo.getDir(), "eap.txt", "eap content");
    myBrotherRepo.addCommit();
    myBrotherRepo.push("--all");

    myRepo.pull();
    myRepo.createBranch("my_feature");

    String[] branches = myRepo.branch("-a").getStdout().split("\n");
    myBranches = new ArrayList<TestBranch>(branches.length);
    for (String b : branches) {
      boolean current = b.charAt(0) == '*';
      b = b.substring(2);
      final boolean remote = b.startsWith("remotes");  // we don't store the 'remote' prefix. Instead we store a boolean flag.
      if (remote) {
        b = b.substring("remotes/".length());
      }
      TestBranch branch = new TestBranch(current, !remote, b);
      if (current) {
        myFeatureBranch = branch; // it is current at the end of setUp.
      }
      myBranches.add(branch);
    }
    myDir = myRepo.getDir();
  }

  @Test
  public void testListAsStrings() throws VcsException {
    List<String> branches = new ArrayList<String>();
    GitBranch.listAsStrings(myProject, myDir, true, true, branches, null);
    assertEqualBranchStringLists(branches, myBranches);
  }

  @Test
  public void testListAsStringsWithNoActiveBranch() throws VcsException, IOException {
    checkoutRemoteBranch();

    List<String> branches = new ArrayList<String>();
    GitBranch.listAsStrings(myProject, myDir, true, true, branches, null);
    assertEqualBranchStringLists(branches, myBranches);
  }

  @Test
  public void testList() throws VcsException {
    List<GitBranch> branches = new ArrayList<GitBranch>();
    GitBranch.list(myProject, myDir, true, true, branches, null);
    assertEqualBranchLists(branches, myBranches);
  }

  @Test
  public void testListWithNoActiveBranch() throws VcsException, IOException {
    checkoutRemoteBranch();

    List<GitBranch> branches = new ArrayList<GitBranch>();
    GitBranch.list(myProject, myDir, true, true, branches, null);
    assertEqualBranchLists(branches, myBranches);
  }

  @Test
  public void testCurrent() throws VcsException {
    final GitBranch current = GitBranch.current(myProject, myDir);
    assertEqualBranches(current, new TestBranch(true, true, "my_feature"));
  }

  @Test
  public void testCurrentWithNoActiveBranch() throws VcsException, IOException {
    checkoutRemoteBranch();
    assertNull(GitBranch.current(myProject, myDir));
  }

  private void checkoutRemoteBranch() throws IOException {
    myRepo.checkout("remotes/origin/feature");
    myFeatureBranch.isCurrent = false;
  }

  private static void assertEqualBranchStringLists(List<String> actual, List<TestBranch> expected) {
    assertEquals(actual.size(), expected.size(), "Size differs. branches: [" + actual + "], myBranches: [" + expected + "]");
    for (TestBranch b : expected) {
      assertTrue(actual.contains(b.name));
    }
  }

  private static void assertEqualBranchLists(List<GitBranch> actual, List<TestBranch> expected) {
    assertEquals(actual.size(), expected.size(), "Size differs. branches: [" + actual + "], myBranches: [" + expected + "]");
    Collections.sort(actual, new GitBranchComparator());
    Collections.sort(expected, new TestGitBranchComparator());
    for (Iterator ait = actual.iterator(), eit = expected.iterator(); ait.hasNext(); ) {
      GitBranch gb = (GitBranch)ait.next();
      TestBranch tgb = (TestBranch)eit.next();
      assertEqualBranches(gb, tgb);
    }
  }

  private static void assertEqualBranches(GitBranch gb, TestBranch tgb) {
    assertEquals(gb.getName(), tgb.name);
    assertEquals(gb.isActive(), tgb.isCurrent);
    assertEquals(gb.isRemote(), !tgb.isLocal);
  }

  private static class TestBranch {
    boolean isCurrent;
    boolean isLocal; // false if remote
    String name;
    TestBranch(boolean current, boolean local, String name) {
      isCurrent = current;
      isLocal = local;
      this.name = name;
    }
    @Override public String toString() {
      return name;
    }
  }

  private static class GitBranchComparator implements Comparator<GitBranch> {
    @Override
    public int compare(GitBranch o1, GitBranch o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }

  private static class TestGitBranchComparator implements Comparator<TestBranch> {
    @Override
    public int compare(TestBranch o1, TestBranch o2) {
      return o1.name.compareTo(o2.name);
    }
  }

}
