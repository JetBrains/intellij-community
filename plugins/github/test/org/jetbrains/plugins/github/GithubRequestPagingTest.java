/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubRepo;
import org.jetbrains.plugins.github.test.GithubTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assume.assumeNotNull;

/**
 * @author Aleksey Pivovarov
 */
public class GithubRequestPagingTest extends GithubTest {

  @Override
  protected void beforeTest() throws Exception {
    assumeNotNull(myLogin2);
  }

  public void testAvailableRepos() throws Throwable {

    List<GithubRepo> availableRepos = GithubApiUtil.getUserRepos(myGitHubSettings.getAuthData(), myLogin2);
    List<String> realData = new ArrayList<String>();
    for (GithubRepo info : availableRepos) {
      realData.add(info.getName());
    }

    List<String> expectedData = new ArrayList<String>();
    for (int i = 1; i <= 251; i++) {
      expectedData.add(String.valueOf(i));
    }

    assertContainsElements(realData, expectedData);
  }
}
