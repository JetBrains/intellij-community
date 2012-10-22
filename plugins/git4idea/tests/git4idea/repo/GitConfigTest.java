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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitBranch;
import git4idea.test.GitTestPlatformFacade;
import git4idea.test.GitTestUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitConfigTest {
  
  @DataProvider(name = "remote")
  public Object[][] loadRemotes() throws IOException {
    return GitTestUtil.loadConfigData(getTestDataFolder("remote"));
  }
  
  @DataProvider(name = "branch")
  public Object[][] loadBranches() throws IOException {
    return GitTestUtil.loadConfigData(getTestDataFolder("branch"));
  }

  private static File getTestDataFolder(String subfolder) {
    File testData = GitTestUtil.getTestDataFolder();
    return new File(new File(testData, "config"), subfolder);
  }

  @Test(dataProvider = "remote")
  public void testRemotes(String testName, File configFile, File resultFile) throws IOException {
    GitConfig config = GitConfig.read(new GitTestPlatformFacade(), configFile);
    GitTestUtil.assertEqualCollections(config.parseRemotes(), readRemoteResults(resultFile));
  }
  
  @Test(dataProvider = "branch")
  public void testBranches(String testName, File configFile, File resultFile) throws IOException {
    GitConfig config = GitConfig.read(new GitTestPlatformFacade(), configFile);
    GitTestUtil.assertEqualCollections(
      config.parseTrackInfos(config.parseRemotes(), Collections.<GitBranch>emptyList(), Collections.<GitBranch>emptyList()),
      readBranchResults(resultFile));
  }

  private static Collection<GitBranchTrackInfo> readBranchResults(File file) throws IOException {
    String content = FileUtil.loadFile(file);
    Collection<GitBranchTrackInfo> remotes = new ArrayList<GitBranchTrackInfo>();
    String[] remStrings = content.split("BRANCH\n");
    for (String remString : remStrings) {
      if (StringUtil.isEmptyOrSpaces(remString)) {
        continue;
      }
      String[] info = remString.split("\n");
      String branch = info[0];
      GitRemote remote = getRemote(info[1]);
      String remoteSpec = info[2];
      String remoteBranchName = info[3];
      boolean merge = info[4].equals("merge");
      remotes.add(new GitBranchTrackInfo(branch, remote, remoteSpec, merge));
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
    Set<GitRemote> remotes = new HashSet<GitRemote>();
    String[] remStrings = content.split("REMOTE\n");
    for (String remString : remStrings) {
      if (StringUtil.isEmptyOrSpaces(remString)) {
        continue;
      }
      String[] info = remString.split("\n");
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
    return Arrays.asList(line.split(" "));
  }

  private static List<String> getSingletonOrEmpty(String[] array, int i) {
    return array.length < i + 1 ? Collections.<String>emptyList() : Collections.singletonList(array[i]);
  }

}
