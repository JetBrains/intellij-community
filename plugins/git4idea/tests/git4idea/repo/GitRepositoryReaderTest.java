/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.Processor;
import git4idea.GitBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.tests.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class GitRepositoryReaderTest extends LightIdeaTestCase {

  private GitRepositoryReader myRepositoryReader;
  private File myTempDir;
  private File myGitDir;
  private Collection<GitTestBranch> myLocalBranches;
  private Collection<GitTestBranch> myRemoteBranches;

  protected void setUp() throws Exception {
    super.setUp();
    myTempDir = FileUtil.createTempDirectory("GitRepositoryReaderTest", null);

    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    File dataDir = new File(new File(pluginRoot, "testData"), "repo");

    FileUtil.copyDir(dataDir, myTempDir);
    myGitDir = new File(myTempDir, ".git");
    FileUtil.rename(new File(myTempDir, "dot_git"), myGitDir);
    assertTrue(myGitDir.exists());
    myRepositoryReader = new GitRepositoryReader(myGitDir);
    
    myLocalBranches = readBranches(true);
    myRemoteBranches = readBranches(false);
  }

  protected void tearDown() throws Exception {
    FileUtil.delete(myTempDir);
    super.tearDown();
  }
  
  public void testHEAD() {
    assertEquals("0e1d130689bc52f140c5c374aa9cc2b8916c0ad7", myRepositoryReader.readCurrentRevision());
  }
  
  public void testCurrentBranch() {
    assertBranch(myRepositoryReader.readCurrentBranch(), new GitTestBranch("master", "0e1d130689bc52f140c5c374aa9cc2b8916c0ad7"));
  }
  
  public void testBranches(){
    GitBranchesCollection branchesCollection = myRepositoryReader.readBranches();
    GitBranch currentBranch = branchesCollection.getCurrentBranch();
    Collection<GitBranch> localBranches = branchesCollection.getLocalBranches();
    Collection<GitBranch> remoteBranches = branchesCollection.getRemoteBranches();
    
    assertBranch(currentBranch, new GitTestBranch("master", "0e1d130689bc52f140c5c374aa9cc2b8916c0ad7"));
    assertBranches(localBranches, myLocalBranches);
    assertBranches(remoteBranches, myRemoteBranches);
  }

  private static void assertBranches(Collection<GitBranch> actual, Collection<GitTestBranch> expected) {
    GitTestUtil.assertEqualCollections(actual, expected, new GitTestUtil.EqualityChecker<GitBranch, GitTestBranch>() {
      @Override
      public boolean areEqual(@NotNull GitBranch actual, @NotNull GitTestBranch expected) {
        return branchesAreEqual(actual, expected);
      }
    });
  }

  private Collection<GitTestBranch> readBranches(boolean local) throws IOException {
    final Collection<GitTestBranch> branches = new ArrayList<GitTestBranch>();
    final File refsHeads = new File(new File(myGitDir, "refs"), local ? "heads" : "remotes");
    FileUtil.processFilesRecursively(refsHeads, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.equals(refsHeads)) { // don't process the root
          return true;
        }
        if (file.isDirectory()) { // don't process dirs
          return true;
        }
        String name = FileUtil.getRelativePath(refsHeads, file);
        GitTestBranch branch = null;
        try {
          branch = new GitTestBranch(name, FileUtil.loadFile(file));
        }
        catch (IOException e) {
          fail(e.toString());
          e.printStackTrace();
        }
        if (!branches.contains(branch)) {
          branches.add(branch);
        }
        return true;
      }
    });

    // read from packed-refs, these have less priority, so the won't overwrite hashes from branch files
    String packedRefs = FileUtil.loadFile(new File(myGitDir, "packed-refs"));
    for (String ref : packedRefs.split("\n")) {
      String[] refAndName = ref.split(" ");
      String name = refAndName[1];
      String prefix = local ? "refs/heads/" : "refs/remotes/";
      if (name.startsWith(prefix)) {
        GitTestBranch branch = new GitTestBranch(name.substring(prefix.length()), refAndName[0]);
        if (!branches.contains(branch)) {
          branches.add(branch);
        }
      }
    }
    return branches;
  }
  
  private static void assertBranch(GitBranch actual, GitTestBranch expected) {
    assertTrue(String.format("Branches are not equal. Actual: %s:%sExpected: %s", actual.getName(), actual.getHash(), expected), branchesAreEqual(actual, expected));
  }

  private static boolean branchesAreEqual(GitBranch actual, GitTestBranch expected) {
    return actual.getName().equals(expected.getName()) && actual.getHash().equals(expected.getHash());
  }
  
  private static class GitTestBranch {
    private final String myName;
    private final String myHash;

    private GitTestBranch(String name, String hash) {
      myName = name;
      myHash = hash;
    }

    String getName() {
      return myName;
    }
    
    String getHash() {
      return myHash;
    }

    @Override
    public String toString() {
      return myName + ":" + myHash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GitTestBranch branch = (GitTestBranch)o;

      if (myName != null ? !myName.equals(branch.myName) : branch.myName != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName != null ? myName.hashCode() : 0;
    }
  }

}
