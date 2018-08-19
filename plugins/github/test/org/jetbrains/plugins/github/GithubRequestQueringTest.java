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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.test.GithubTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assume.assumeNotNull;

/**
 * @author Aleksey Pivovarov
 */
public class GithubRequestQueringTest extends GithubTest {

  @Override
  protected void beforeTest() {
    assumeNotNull(myAccount2);
  }

  public void testLinkPagination() throws Throwable {
    List<GithubRepo> availableRepos = GithubApiPagesLoader
      .loadAll(myExecutor2, new EmptyProgressIndicator(), GithubApiRequests.CurrentUser.Repos.pages(myAccount2.getServer(), false));
    List<String> realData = new ArrayList<>();
    for (GithubRepo info : availableRepos) {
      realData.add(info.getName());
    }

    List<String> expectedData = new ArrayList<>();
    for (int i = 1; i <= 251; i++) {
      expectedData.add(String.valueOf(i));
    }

    assertContainsElements(realData, expectedData);
  }

  public void testOwnRepos() throws Throwable {
    List<GithubRepo> result = GithubApiPagesLoader
      .loadAll(myExecutor, new EmptyProgressIndicator(), GithubApiRequests.CurrentUser.Repos.pages(myAccount.getServer(), false));

    assertTrue(ContainerUtil.exists(result, (it) -> it.getName().equals("example")));
    assertTrue(ContainerUtil.exists(result, (it) -> it.getName().equals("PullRequestTest")));
    assertFalse(ContainerUtil.exists(result, (it) -> it.getName().equals("org_repo")));
  }

  public void testAllRepos() throws Throwable {
    List<GithubRepo> result = GithubApiPagesLoader
      .loadAll(myExecutor, new EmptyProgressIndicator(), GithubApiRequests.CurrentUser.Repos.pages(myAccount.getServer()));

    assertTrue(ContainerUtil.exists(result, (it) -> it.getName().equals("example")));
    assertTrue(ContainerUtil.exists(result, (it) -> it.getName().equals("PullRequestTest")));
    assertTrue(ContainerUtil.exists(result, (it) -> it.getName().equals("org_repo")));
  }
}
