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
import java.io.IOException;
import java.util.*;

import static org.testng.Assert.assertNotNull;

/**
 * @author Kirill Likhodedov
 */
public class GitConfigTest {

  @DataProvider(name = "provider")
  public Object[][] loadData() throws IOException {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    File dataDir = new File(new File(pluginRoot, "testData"), "config");
    File[] tests = dataDir.listFiles();
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

  @Test(dataProvider = "provider")
  public void test(String testName, File configFile, File resultFile) throws IOException {
    GitConfig config = GitConfig.read(configFile);
    GitTestUtil.assertEqualCollections(config.getRemotes(), readResults(resultFile));
  }
  
  private static Set<GitRemote> readResults(File resultFile) throws IOException {
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
