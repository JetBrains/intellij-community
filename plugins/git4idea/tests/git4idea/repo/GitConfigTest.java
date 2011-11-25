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
import com.intellij.openapi.util.text.StringUtil;
import git4idea.tests.GitTestUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

import static org.testng.Assert.assertNotNull;

/**
 * @author Kirill Likhodedov
 */
public class GitConfigTest {

  @DataProvider(name = "remote")
  public Object[][] loadRemotes() throws IOException {
    return loadData("remote");
  }
  
  @DataProvider(name = "branch")
  public Object[][] loadBranches() throws IOException {
    return loadData("branch");
  }

  public static Object[][] loadData(String subfolder) throws IOException {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    File dataDir = new File(new File(new File(pluginRoot, "testData"), "config"), subfolder);
    File[] tests = dataDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return !name.startsWith(".");
      }
    });
    Object[][] data = new Object[tests.length][];
    for (int i = 0; i < tests.length; i++) {
      File testDir = tests[i];
      File descriptionFile = null;
      File configFile = null;
      File resultFile = null;
      for (File file : testDir.listFiles()) {
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
      assertNotNull(descriptionFile, String.format("description file not found in %s among %s", testDir, Arrays.toString(testDir.list())));
      assertNotNull(configFile, String.format("config file file not found in %s among %s", testDir, Arrays.toString(testDir.list())));
      assertNotNull(resultFile, String.format("result file file not found in %s among %s", testDir, Arrays.toString(testDir.list())));

      String testName = FileUtil.loadFile(descriptionFile).split("\n")[0]; // description is in the first line of the desc-file
      data[i] = new Object[]{
        testName, configFile, resultFile
      };
    }
    return data;
  }

  @Test(dataProvider = "remote")
  public void testRemotes(String testName, File configFile, File resultFile) throws IOException {
    GitConfig config = GitConfig.read(configFile);
    GitTestUtil.assertEqualCollections(config.getRemotes(), readRemoteResults(resultFile));
  }
  
  @Test(dataProvider = "branch")
  public void testBranches(String testName, File configFile, File resultFile) throws IOException {
    GitConfig config = GitConfig.read(configFile);
    GitTestUtil.assertEqualCollections(config.getBranchTrackInfos(), readBranchResults(resultFile));
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
    return new GitRemote(getOrEmpty(remoteInfo, 0), Collections.singletonList(getOrEmpty(remoteInfo, 1)),
                         Collections.singletonList(getOrEmpty(remoteInfo, 2)), getOrEmpty(remoteInfo, 3), getOrEmpty(remoteInfo, 4));
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
      List<String> urls = getUrls(info[1]);
      Collection<String> pushUrls = getUrls(info[2]);
      String fetchSpec = getOrEmpty(info, 3);
      String pushSpec = getOrEmpty(info, 4);
      GitRemote remote = new GitRemote(name, urls, pushUrls, fetchSpec, pushSpec);
      remotes.add(remote);
    }
    return remotes;
  }

  private static List<String> getUrls(String line) {
    if (StringUtil.isEmptyOrSpaces(line)) {
      return Collections.emptyList();
    }
    return Arrays.asList(line.split(" "));
  }

  private static String getOrEmpty(String[] array, int i) {
    return array.length < i + 1 ? "" : array[i];
  }

}
