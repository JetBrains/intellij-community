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

import com.google.common.collect.Collections2;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.test.GitPlatformTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static git4idea.test.GitExecutor.git;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class GitConfigTest extends GitPlatformTest {

  public void testRemotes() throws IOException {
    Collection<TestSpec> objects = loadRemotes();
    for (TestSpec spec : objects) {
      doTestRemotes(spec.name, spec.config, spec.result);
    }
  }

  public void testBranches() throws IOException {
    Collection<TestSpec> objects = loadBranches();
    for (TestSpec spec : objects) {
      doTestBranches(spec.name, spec.config, spec.result);
    }
  }

  //inspired by IDEA-135557
  public void test_branch_with_hash_symbol() {
    createRepository();
    addRemote("http://example.git");
    git("update-ref refs/remotes/origin/a#branch HEAD");
    git("branch --track a#branch origin/a#branch");

    File gitDir = new File(myProjectPath, ".git");
    GitConfig config = GitConfig.read(new File(gitDir, "config"));
    VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gitDir);
    GitRepositoryReader reader = new GitRepositoryReader(GitRepositoryFiles.getInstance(dir));
    GitBranchState state = reader.readState(config.parseRemotes());
    Collection<GitBranchTrackInfo> trackInfos = config.parseTrackInfos(state.getLocalBranches().keySet(), state.getRemoteBranches().keySet());
    assertTrue("Couldn't find correct a#branch tracking information among: [" + trackInfos + "]",
               ContainerUtil.exists(trackInfos, info -> info.getLocalBranch().getName().equals("a#branch") &&
                                                    info.getRemoteBranch().getNameForLocalOperations().equals("origin/a#branch")));
  }

  // IDEA-143363 Check that remote.pushdefault (generic, without remote name) doesn't fail the config parsing procedure
  public void test_remote_unspecified_section() {
    createRepository();
    addRemote("git@github.com:foo/bar.git");
    git("config remote.pushdefault origin");

    assertSingleRemoteInConfig();
  }

  public void test_invalid_section_with_remote_prefix_is_ignored() {
    createRepository();
    addRemote("git@github.com:foo/bar.git");
    git("config remote-cfg.newkey newval");

    assertSingleRemoteInConfig();
  }

  public void test_config_options_are_case_insensitive() {
    createRepository();
    addRemote("git@github.com:foo/bar.git");
    String pushUrl = "git@github.com:foo/push.git";
    git("config remote.origin.pushurl " + pushUrl);

    GitConfig config = readConfig();
    GitRemote remote = getFirstItem(config.parseRemotes());
    assertNotNull(remote);
    assertSameElements("pushurl parsed incorrectly", remote.getPushUrls(), singletonList(pushUrl));
  }

  public void test_config_values_are_case_sensitive() {
    createRepository();
    String expectedName = "ORIGIN";
    addRemote(expectedName, "git@github.com:foo/bar.git");

    GitConfig config = readConfig();
    GitRemote remote = getFirstItem(config.parseRemotes());
    assertNotNull(remote);
    assertEquals("Remote name is incorrect", expectedName, remote.getName());
  }

  private static void addRemote(@NotNull String url) {
    addRemote("origin", url);
  }

  private static void addRemote(@NotNull String name, @NotNull String url) {
    git(String.format("remote add %s %s", name, url));
  }

  private void createRepository() {
    GitTestUtil.createRepository(myProject, myProjectPath, true);
  }

  private static void assertSingleRemote(@NotNull Collection<GitRemote> remotes) {
    assertEquals(1, remotes.size());
    GitRemote remote = getFirstItem(remotes);
    assertNotNull(remote);
    assertEquals("origin", remote.getName());
    assertEquals("git@github.com:foo/bar.git", remote.getFirstUrl());
  }

  @NotNull
  private GitConfig readConfig() {
    File gitDir = new File(myProjectPath, ".git");
    return GitConfig.read(new File(gitDir, "config"));
  }

  private void assertSingleRemoteInConfig() {
    Collection<GitRemote> remotes = readConfig().parseRemotes();
    assertSingleRemote(remotes);
  }

  private void doTestRemotes(String testName, File configFile, File resultFile) throws IOException {
    GitConfig config = GitConfig.read(configFile);
    VcsTestUtil.assertEqualCollections(testName, config.parseRemotes(), readRemoteResults(resultFile));
  }

  private void doTestBranches(String testName, File configFile, File resultFile) throws IOException {
    Collection<GitBranchTrackInfo> expectedInfos = readBranchResults(resultFile);
    Collection<GitLocalBranch> localBranches = Collections2.transform(expectedInfos, input -> {
      assert input != null;
      return input.getLocalBranch();
    });
    Collection<GitRemoteBranch> remoteBranches = Collections2.transform(expectedInfos, input -> {
      assert input != null;
      return input.getRemoteBranch();
    });

    VcsTestUtil.assertEqualCollections(testName,
                                       GitConfig.read(configFile).parseTrackInfos(localBranches, remoteBranches),
                                       expectedInfos);
  }

  public Collection<TestSpec> loadRemotes() throws IOException {
    return loadConfigData(getTestDataFolder("remote"));
  }
  
  public Collection<TestSpec> loadBranches() throws IOException {
    return loadConfigData(getTestDataFolder("branch"));
  }

  private static File getTestDataFolder(String subfolder) {
    File testData = getTestDataFolder();
    return new File(new File(testData, "config"), subfolder);
  }

  @NotNull
  public static Collection<TestSpec> loadConfigData(@NotNull File dataFolder) throws IOException {
    File[] tests = dataFolder.listFiles((dir, name) -> !name.startsWith("."));
    Collection<TestSpec> data = ContainerUtil.newArrayList();
    for (File testDir : tests) {
      File descriptionFile = null;
      File configFile = null;
      File resultFile = null;
      File[] files = testDir.listFiles();
      assertNotNull("No test specifications found in " + testDir.getPath(), files);
      for (File file : files) {
        if (file.getName().endsWith("_desc.txt")) {
          descriptionFile = file;
        }
        else if (file.getName().endsWith("_config.txt")) {
          configFile = file;
        }
        else if (file.getName().endsWith("_result.txt")) {
          resultFile = file;
        }
      }
      assertNotNull(String.format("description file not found in %s among %s", testDir, Arrays.toString(testDir.list())), descriptionFile);
      assertNotNull(String.format("config file file not found in %s among %s", testDir, Arrays.toString(testDir.list())), configFile);
      assertNotNull(String.format("result file file not found in %s among %s", testDir, Arrays.toString(testDir.list())), resultFile);

      String testName = FileUtil.loadFile(descriptionFile).split("\n")[0]; // description is in the first line of the desc-file
      if (!testName.toLowerCase().startsWith("ignore")) {
        data.add(new TestSpec(testName, configFile, resultFile));
      }
    }
    return data;
  }

  @NotNull
  public static File getTestDataFolder() {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    return new File(pluginRoot, "testData");
  }

  private static Collection<GitBranchTrackInfo> readBranchResults(File file) throws IOException {
    String content = FileUtil.loadFile(file);
    Collection<GitBranchTrackInfo> remotes = new ArrayList<>();
    List<String> remStrings = StringUtil.split(content, "BRANCH");
    for (String remString : remStrings) {
      if (StringUtil.isEmptyOrSpaces(remString)) {
        continue;
      }
      String[] info = StringUtil.splitByLines(remString.trim());
      String branch = info[0];
      GitRemote remote = getRemote(info[1]);
      String remoteBranchAtRemote = info[2];
      String remoteBranchHere = info[3];
      boolean merge = info[4].equals("merge");
      remotes.add(new GitBranchTrackInfo(new GitLocalBranch(branch),
                                         new GitStandardRemoteBranch(remote, remoteBranchAtRemote),
                                         merge));
    }
    return remotes;
  }

  private static GitRemote getRemote(String remoteString) {
    String[] remoteInfo = remoteString.split(" ");
    return new GitRemote(remoteInfo[0], getSingletonOrEmpty(remoteInfo, 1), getSingletonOrEmpty(remoteInfo, 2),
                         getSingletonOrEmpty(remoteInfo, 3), getSingletonOrEmpty(remoteInfo, 4));
  }

  private static Set<GitRemote> readRemoteResults(File resultFile) throws IOException {
    String content = FileUtil.loadFile(resultFile);
    Set<GitRemote> remotes = new HashSet<>();
    List<String> remStrings = StringUtil.split(content, "REMOTE");
    for (String remString : remStrings) {
      if (StringUtil.isEmptyOrSpaces(remString)) {
        continue;
      }
      String[] info = StringUtil.splitByLines(remString.trim());
      String name = info[0];
      List<String> urls = getSpaceSeparatedStrings(info[1]);
      Collection<String> pushUrls = getSpaceSeparatedStrings(info[2]);
      List<String> fetchSpec = getSpaceSeparatedStrings(info[3]);
      List<String> pushSpec = getSpaceSeparatedStrings(info[4]);
      GitRemote remote = new GitRemote(name, urls, pushUrls, fetchSpec, pushSpec);
      remotes.add(remote);
    }
    return remotes;
  }

  private static List<String> getSpaceSeparatedStrings(String line) {
    if (StringUtil.isEmptyOrSpaces(line)) {
      return Collections.emptyList();
    }
    return asList(line.split(" "));
  }

  private static List<String> getSingletonOrEmpty(String[] array, int i) {
    return array.length < i + 1 ? Collections.emptyList() : singletonList(array[i]);
  }

  private static class TestSpec {
    @NotNull String name;
    @NotNull File config;
    @NotNull File result;

    public TestSpec(@NotNull String testName, @NotNull File configFile, @NotNull File resultFile) {
      this.name = testName;
      this.config = configFile;
      this.result = resultFile;
    }
  }

}
