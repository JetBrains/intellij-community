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
package git4idea.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.test.GitTestUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Kirill Likhodedov
 */
public class NetrcDataTest {

  @DataProvider(name = "netrc")
  public Object[][] loadBranches() throws IOException {
    return GitTestUtil.loadConfigData(new File(GitTestUtil.getTestDataFolder(), "netrc"));
  }

  @Test(dataProvider = "netrc")
  public void testBranches(String testName, File netrcFile, File resultFile) throws IOException {
    NetrcData netrcData = NetrcData.parse(netrcFile);
    Collection<NetrcData.AuthRecord> expectedRecord = readAuthData(resultFile);
    GitTestUtil.assertEqualCollections(netrcData.getAuthData(), expectedRecord);

    if (!testName.startsWith("n0") && !testName.startsWith("n1")) {   // auth data not defined for these tests
      assertTrue(netrcData.hasAuthDataForUrl("http://bitbucket.org"));
      assertTrue(netrcData.hasAuthDataForUrl("https://bitbucket.org"));
      assertTrue(netrcData.hasAuthDataForUrl("bitbucket.org"));
      assertTrue(netrcData.hasAuthDataForUrl("https://bitbucket.org/user/repo.git"));
      assertTrue(netrcData.hasAuthDataForUrl("https://bitbucket.org/user/repo"));
    }
    
    assertFalse(netrcData.hasAuthDataForUrl("http://example.com"));
    assertFalse(netrcData.hasAuthDataForUrl("https://example.com"));
    assertFalse(netrcData.hasAuthDataForUrl("example.com"));
  }

  private static Collection<NetrcData.AuthRecord> readAuthData(File file) throws IOException {
    Collection<NetrcData.AuthRecord> authRecords = new ArrayList<NetrcData.AuthRecord>();
    for (String authLine : FileUtil.loadFile(file).split("\n")) {
      if (StringUtil.isEmptyOrSpaces(authLine)) {
        continue;
      }
      String[] data = authLine.split(" ");
      authRecords.add(new NetrcData.AuthRecord(data[0], data[1], data.length > 2 ? data[2] : null));
    }
    return authRecords;
  }
}
