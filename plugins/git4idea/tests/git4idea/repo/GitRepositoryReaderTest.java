// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitLocalBranch;
import git4idea.GitReference;
import git4idea.GitTag;
import git4idea.config.GitVcsSettings;
import git4idea.test.GitPlatformTest;
import junit.framework.TestCase;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
public class GitRepositoryReaderTest extends GitPlatformTest {
  @NotNull private final File myTestCaseDir;

  private File myTempDir;
  private GitRepositoryReader myRepositoryReader;
  private File myGitDir;
  private @Nullable VirtualFile myRootDir;
  private @NotNull GitRepositoryFiles myRepoFiles;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    File dataDir = new File(new File(pluginRoot, "testData"), "repo");
    File[] testCases = dataDir.listFiles(FileFilters.DIRECTORIES);
    return ContainerUtil.map(testCases, file -> new Object[]{file.getName(), file});
  }

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public GitRepositoryReaderTest(@NotNull String name, @NotNull File testDir) {
    myTestCaseDir = testDir;
  }

  @Before
  public void before() throws IOException {
    myTempDir = new File(getProjectRoot().getPath(), "test");
    prepareTest(myTestCaseDir);
  }

  @After
  public void after() {
    if (myTempDir != null) {
      FileUtil.delete(myTempDir);
    }
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
    myRootDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTempDir);
    VirtualFile gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myGitDir);
    myRepoFiles = GitRepositoryFiles.createInstance(myRootDir, gitDir);
    myRepositoryReader = new GitRepositoryReader(myProject, myRepoFiles);
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
    Assume.assumeTrue(Registry.is("git.read.branches.from.disk")); // not a valid git repository: .git/objects is missing

    Collection<GitRemote> remotes = GitConfig.read(new File(myGitDir, "config")).parseRemotes();
    GitBranchState state = myRepositoryReader.readState(remotes);

    assertEquals("HEAD revision is incorrect", readHead(myTempDir), state.getCurrentRevision());
    assertEqualBranches(readCurrentBranch(myTempDir), state.getCurrentBranch(), state.getLocalBranches().get(state.getCurrentBranch()));
    assertReferences(state.getLocalBranches(), readRefs(myTempDir, RefType.LOCAL_BRANCH));
    assertReferences(state.getRemoteBranches(), readRefs(myTempDir, RefType.REMOTE_BRANCH));
  }

  @Test
  public void testTags() throws Exception {
    Assume.assumeTrue(Registry.is("git.read.branches.from.disk")); // not a valid git repository

    try {
      GitVcsSettings.getInstance(myProject).getState().setShowTags(true);
      GitRepository repo = Mockito.mock(GitRepository.class);
      Mockito.when(repo.getProject()).thenReturn(myProject);
      Mockito.when(repo.getRepositoryFiles()).thenReturn(myRepoFiles);
      Mockito.when(repo.getCoroutineScope()).thenReturn(GlobalScope.INSTANCE);
      GitTagHolder holder = new GitTagHolder(repo);
      holder.ensureUpToDateForTests$intellij_vcs_git();
      Map<GitTag, Hash> tags = holder.getTags();
      assertReferences(tags, readRefs(myTempDir, RefType.TAG));
    }
    finally {
      GitVcsSettings.getInstance(myProject).getState().setShowTags(false);
    }
  }

  private static void assertEqualBranches(@NotNull Branch expected, @NotNull GitLocalBranch actual, @NotNull Hash hash) {
    assertEquals(expected.name, actual.getName());
    assertEquals("Incorrect hash of branch " + actual.getName(), expected.hash, hash);
  }

  private static void assertReferences(Map<? extends GitReference, Hash> actualBranches, Collection<Branch> expectedBranches) {
    VcsTestUtil.assertEqualCollections(actualBranches.entrySet(), expectedBranches,
                                       new VcsTestUtil.EqualityChecker<Map.Entry<? extends GitReference, Hash>, Branch>() {
                                         @Override
                                         public boolean areEqual(Map.Entry<? extends GitReference, Hash> actual, Branch expected) {
                                           return referencesAreEqual(actual.getKey(), actual.getValue(), expected);
                                         }
                                       });
  }

  @NotNull
  private static Collection<Branch> readRefs(@NotNull File resultDir, RefType refType) throws IOException {
    File file = new File(resultDir, refType.myPath);
    String content = FileUtil.loadFile(file);
    Collection<Branch> branches = new ArrayList<>();
    for (String line : StringUtil.splitByLines(content)) {
      branches.add(readBranchFromLine(line));
    }
    return branches;
  }

  private static boolean referencesAreEqual(GitReference actualBranch, Hash actualHash, Branch expected) {
    return actualBranch.getFullName().equals(expected.name) && actualHash.equals(expected.hash);
  }

  private static final class Branch {
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

  private enum RefType {
    LOCAL_BRANCH("local-branches.txt"),
    REMOTE_BRANCH("remote-branches.txt"),
    TAG("tags.txt");

    final String myPath;

    RefType(String path) {
      myPath = path;
    }
  }
}
