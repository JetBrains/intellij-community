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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubIssue;
import org.jetbrains.plugins.github.test.GithubTest;

import java.util.Arrays;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public class GithubIssuesTest extends GithubTest {
  private static final String REPO_NAME = "IssuesTest";

  public void testAssigneeIssues1() throws Exception {
    List<GithubIssue> result = GithubApiUtil.getIssuesAssigned(myAuth, myLogin2, REPO_NAME, myLogin1, 100);
    List<Long> issues = ContainerUtil.map(result, new Function<GithubIssue, Long>() {
      @Override
      public Long fun(GithubIssue githubIssue) {
        return githubIssue.getNumber();
      }
    });

    List<Long> expected = Arrays.asList(6L, 7L, 8L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testAssigneeIssues2() throws Exception {
    List<GithubIssue> result = GithubApiUtil.getIssuesAssigned(myAuth, myLogin2, REPO_NAME, myLogin2, 100);
    List<Long> issues = ContainerUtil.map(result, new Function<GithubIssue, Long>() {
      @Override
      public Long fun(GithubIssue githubIssue) {
        return githubIssue.getNumber();
      }
    });

    List<Long> expected = Arrays.asList(1L, 2L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testAssigneeIssues3() throws Exception {
    List<GithubIssue> result = GithubApiUtil.getIssuesAssigned(myAuth, myLogin2, REPO_NAME, "", 100);
    List<Long> issues = ContainerUtil.map(result, new Function<GithubIssue, Long>() {
      @Override
      public Long fun(GithubIssue githubIssue) {
        return githubIssue.getNumber();
      }
    });

    List<Long> expected = Arrays.asList(1L, 2L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 13L, 14L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testQueriedIssues1() throws Exception {
    List<GithubIssue> result = GithubApiUtil.getIssuesQueried(myAuth, myLogin2, REPO_NAME, "abracadabra");
    List<Long> issues = ContainerUtil.map(result, new Function<GithubIssue, Long>() {
      @Override
      public Long fun(GithubIssue githubIssue) {
        return githubIssue.getNumber();
      }
    });

    List<Long> expected = Arrays.asList(10L, 12L);

    assertContainsElements(issues, expected);
  }

  public void testQueriedIssues2() throws Exception {
    List<GithubIssue> result = GithubApiUtil.getIssuesQueried(myAuth, myLogin2, REPO_NAME, "commentary");
    List<Long> issues = ContainerUtil.map(result, new Function<GithubIssue, Long>() {
      @Override
      public Long fun(GithubIssue githubIssue) {
        return githubIssue.getNumber();
      }
    });

    List<Long> expected = Arrays.asList(11L);

    assertContainsElements(issues, expected);
  }
}
