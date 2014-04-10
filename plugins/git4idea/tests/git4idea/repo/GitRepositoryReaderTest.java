package git4idea.repo;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.test.GitPlatformTest;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileFilter;
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
    File[] testCases = dataDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File file) {
        return file.isDirectory();
      }
    });
    return ContainerUtil.map(testCases, new Function<File, Object[]>() {
      @Override
      public Object[] fun(File file) {
        return new Object[] { file.getName(), file };
      }
    });
  }

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedParameters"})
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
  private static GitLocalBranch readCurrentBranch(@NotNull File resultDir) throws IOException {
    String branch = FileUtil.loadFile(new File(resultDir, "current-branch.txt")).trim();
    return readBranchFromLine(branch);
  }

  @NotNull
  private static GitLocalBranch readBranchFromLine(@NotNull String branch) {
    List<String> branchAndHash = StringUtil.split(branch, " ");
    return new GitLocalBranch(branchAndHash.get(1), HashImpl.build(branchAndHash.get(0)));
  }

  @Test
  public void testHEAD() throws Exception {
    assertEquals("HEAD is incorrect", readHead(myTempDir), myRepositoryReader.readCurrentRevision());
  }

  @Test
  public void testCurrentBranch() throws Exception {
    assertEqualBranches(readCurrentBranch(myTempDir), myRepositoryReader.readCurrentBranch());
  }

  @Test
  public void testBranches() throws Exception {
    Collection<GitRemote> remotes = GitConfig.read(myPlatformFacade, new File(myGitDir, "config")).parseRemotes();
    GitBranchesCollection branchesCollection = myRepositoryReader.readBranches(remotes);
    GitLocalBranch currentBranch = myRepositoryReader.readCurrentBranch();
    Collection<? extends GitBranch> localBranches = branchesCollection.getLocalBranches();
    Collection<? extends GitBranch> remoteBranches = branchesCollection.getRemoteBranches();

    assertEqualBranches(readCurrentBranch(myTempDir), currentBranch);
    assertBranches(localBranches, readBranches(myTempDir, true));
    assertBranches(remoteBranches, readBranches(myTempDir, false));
  }

  private static void assertEqualBranches(@NotNull GitLocalBranch expected, @NotNull GitLocalBranch actual) {
    assertEquals(expected.getName(), actual.getName());
    assertEquals(expected.getHash(), actual.getHash());
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

}
