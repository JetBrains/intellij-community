package git4idea.repo;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.test.GitPlatformTest;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class GitRepositoryReaderTest extends GitPlatformTest {

  private File myDataDir;
  private File myTempDir;

  private GitRepositoryReader myRepositoryReader;
  private File myGitDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTempDir = new File(myProjectRoot.getPath(), "test");
    myTempDir.mkdir();

    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    myDataDir = new File(new File(pluginRoot, "testData"), "repo");
  }

  private void prepareTest(File testDir) throws IOException {
    FileUtil.copyDir(testDir, myTempDir);
    myGitDir = new File(myTempDir, ".git");
    FileUtil.rename(new File(myTempDir, "dot_git"), myGitDir);
    TestCase.assertTrue(myGitDir.exists());
    myRepositoryReader = new GitRepositoryReader(myGitDir);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private void doTest(@NotNull ResultConsumer test) throws Exception {
    for (File dir : myDataDir.listFiles()) {
      if (!dir.isDirectory()) {
        continue;
      }
      prepareTest(dir);
      test.consume(dir);
    }
  }

  @NotNull
  private static String readHead(@NotNull File dir) throws IOException {
    return FileUtil.loadFile(new File(dir, "head.txt")).trim();
  }

  @NotNull
  private static GitLocalBranch readCurrentBranch(@NotNull File resultDir) throws IOException {
    String branch = FileUtil.loadFile(new File(resultDir, "current-branch.txt")).trim();
    return readBranchFromLine(branch);
  }

  @NotNull
  private static GitLocalBranch readBranchFromLine(@NotNull String branch) {
    List<String> branchAndHash = StringUtil.split(branch, " ");
    return new GitLocalBranch(branchAndHash.get(1), HashImpl.build(branchAndHash.get(0)));
  }

  public void testHEAD() throws Exception {
    doTest(new ResultConsumer() {
      @Override
      public void consume(@NotNull File resultDir) throws Exception {
        assertEquals("HEAD is incorrect", readHead(resultDir), myRepositoryReader.readCurrentRevision());
      }
    });
  }

  public void testCurrentBranch() throws Exception {
    doTest(new ResultConsumer() {
      @Override
      public void consume(@NotNull File resultDir) throws Exception {
        assertEqualBranches(readCurrentBranch(resultDir), myRepositoryReader.readCurrentBranch());
      }
    });
  }

  private static void assertEqualBranches(@NotNull GitLocalBranch expected, @NotNull GitLocalBranch actual) {
    assertEquals(expected.getName(), actual.getName());
    assertEquals(expected.getHash(), actual.getHash());
  }

  public void testBranches() throws Exception {
    doTest(new ResultConsumer() {
      @Override
      public void consume(@NotNull File resultDir) throws Exception {
        Collection<GitRemote> remotes = GitConfig.read(myPlatformFacade, new File(myGitDir, "config")).parseRemotes();
        GitBranchesCollection branchesCollection = myRepositoryReader.readBranches(remotes);
        GitLocalBranch currentBranch = myRepositoryReader.readCurrentBranch();
        Collection<? extends GitBranch> localBranches = branchesCollection.getLocalBranches();
        Collection<? extends GitBranch> remoteBranches = branchesCollection.getRemoteBranches();
        
        assertEqualBranches(readCurrentBranch(resultDir), currentBranch);
        assertBranches(localBranches, readBranches(resultDir, true));
        assertBranches(remoteBranches, readBranches(resultDir, false));
      }
    });
  }

  private static void assertBranches(Collection<? extends GitBranch> actualBranches, Collection<? extends GitBranch> expectedBranches) {
    VcsTestUtil.assertEqualCollections(actualBranches, expectedBranches, new VcsTestUtil.EqualityChecker<GitBranch, GitBranch>() {
      @Override
      public boolean areEqual(@NotNull GitBranch actual, @NotNull GitBranch expected) {
        return branchesAreEqual(actual, expected);
      }
    });
  }

  @NotNull
  private static Collection<GitBranch> readBranches(@NotNull File resultDir, boolean local) throws IOException {
    String content = FileUtil.loadFile(new File(resultDir, local ? "local-branches.txt" : "remote-branches.txt"));
    Collection<GitBranch> branches = ContainerUtil.newArrayList();
    for (String line : StringUtil.splitByLines(content)) {
      branches.add(readBranchFromLine(line));
    }
    return branches;
  }

  private static boolean branchesAreEqual(GitBranch actual, GitBranch expected) {
    return actual.getName().equals(expected.getName()) && actual.getHash().equals(expected.getHash());
  }

  private abstract static class ResultConsumer {
    public abstract void consume(@NotNull File resultDir) throws Exception;
  }
}
