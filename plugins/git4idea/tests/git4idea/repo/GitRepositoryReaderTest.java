package git4idea.repo;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.test.GitPlatformTest;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class GitRepositoryReaderTest extends GitPlatformTest {

  @NotNull private final File myTestCaseDir;

  private File myTempDir;
  private GitRepositoryReader myRepositoryReader;
  private File myGitDir;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    File dataDir = new File(new File(pluginRoot, "testData"), "repo");
    File[] testCases = dataDir.listFiles(FileUtilRt.ALL_DIRECTORIES);
    return ContainerUtil.map(testCases, new Function<File, Object[]>() {
      @Override
      public Object[] fun(File file) {
        return new Object[] { file.getName(), file };
      }
    });
  }

  @SuppressWarnings({"UnusedParameters"})
  public GitRepositoryReaderTest(@NotNull String name, @NotNull File testDir) {
    myTestCaseDir = testDir;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myTempDir = new File(myProjectRoot.getPath(), "test");
    prepareTest(myTestCaseDir);
  }

  @After
  @Override
  public void tearDown() throws Exception {
    FileUtil.delete(myTempDir);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          GitRepositoryReaderTest.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private void prepareTest(File testDir) throws IOException {
    assertTrue("Temp directory was not created", myTempDir.mkdir());
    FileUtil.copyDir(testDir, myTempDir);
    myGitDir = new File(myTempDir, ".git");
    File dotGit = new File(myTempDir, "dot_git");
    if (!dotGit.exists()) {
      File dotGitZip = new File(myTempDir, "dot_git.zip");
      assertTrue("Neither dot_git nor dot_git.zip were found", dotGitZip.exists());
      ZipUtil.extract(dotGitZip, myTempDir, null);
    }
    FileUtil.rename(dotGit, myGitDir);
    TestCase.assertTrue(myGitDir.exists());
    myRepositoryReader = new GitRepositoryReader(myGitDir);
  }


  @NotNull
  private static String readHead(@NotNull File dir) throws IOException {
    return FileUtil.loadFile(new File(dir, "head.txt")).trim();
  }

  @NotNull
  private static Branch readCurrentBranch(@NotNull File resultDir) throws IOException {
    String branch = FileUtil.loadFile(new File(resultDir, "current-branch.txt")).trim();
    return readBranchFromLine(branch);
  }

  @NotNull
  private static Branch readBranchFromLine(@NotNull String branch) {
    List<String> branchAndHash = StringUtil.split(branch, " ");
    return new Branch(branchAndHash.get(1), HashImpl.build(branchAndHash.get(0)));
  }

  @Test
  public void testBranches() throws Exception {
    Collection<GitRemote> remotes = GitConfig.read(myPlatformFacade, new File(myGitDir, "config")).parseRemotes();
    GitBranchState state = myRepositoryReader.readState(remotes);

    assertEquals("HEAD revision is incorrect", readHead(myTempDir), state.getCurrentRevision());
    assertEqualBranches(readCurrentBranch(myTempDir), state.getCurrentBranch());
    assertBranches(state.getLocalBranches(), readBranches(myTempDir, true));
    assertBranches(state.getRemoteBranches(), readBranches(myTempDir, false));
  }

  private static void assertEqualBranches(@NotNull Branch expected, @NotNull GitLocalBranch actual) {
    assertEquals(expected.name, actual.getName());
    assertEquals("Incorrect hash of branch " + actual.getName(), expected.hash, actual.getHash());
  }

  private static void assertBranches(Collection<? extends GitBranch> actualBranches, Collection<Branch> expectedBranches) {
    VcsTestUtil.assertEqualCollections(actualBranches, expectedBranches, new VcsTestUtil.EqualityChecker<GitBranch, Branch>() {
      @Override
      public boolean areEqual(GitBranch actual, Branch expected) {
        return branchesAreEqual(actual, expected);
      }
    });
  }

  @NotNull
  private static Collection<Branch> readBranches(@NotNull File resultDir, boolean local) throws IOException {
    String content = FileUtil.loadFile(new File(resultDir, local ? "local-branches.txt" : "remote-branches.txt"));
    Collection<Branch> branches = ContainerUtil.newArrayList();
    for (String line : StringUtil.splitByLines(content)) {
      branches.add(readBranchFromLine(line));
    }
    return branches;
  }

  private static boolean branchesAreEqual(GitBranch actual, Branch expected) {
    return actual.getFullName().equals(expected.name) && actual.getHash().equals(expected.hash);
  }

  private static class Branch {
    final String name;
    final Hash hash;

    private Branch(String name, Hash hash) {
      this.name = name;
      this.hash = hash;
    }

    @Override
    public String toString() {
      return name;
    }
  }

}
