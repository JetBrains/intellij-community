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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.data.GithubIssue;
import org.jetbrains.plugins.github.test.GithubTest;

import java.util.Arrays;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public class GithubIssuesTest extends GithubTest {
  private static final String REPO_NAME = "IssuesTest";

  public void testAssigneeIssues1() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesAssigned(c, myUsername2, REPO_NAME, myUsername, 100, false));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(6L, 7L, 8L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testAssigneeIssues2() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesAssigned(c, myUsername2, REPO_NAME, myUsername2, 100, false));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(1L, 2L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testAssigneeIssues3() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesAssigned(c, myUsername2, REPO_NAME, "", 100, false));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(1L, 2L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 13L, 14L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testAssigneeIssues4() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesAssigned(c, myUsername2, REPO_NAME, myUsername, 100, true));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(3L, 6L, 7L, 8L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testAssigneeIssues5() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesAssigned(c, myUsername2, REPO_NAME, myUsername2, 100, true));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(1L, 2L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testAssigneeIssues6() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesAssigned(c, myUsername2, REPO_NAME, "", 100, true));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testQueriedIssues1() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesQueried(c, myUsername2, REPO_NAME, null, "abracadabra", true));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(10L, 12L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testQueriedIssues2() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesQueried(c, myUsername2, REPO_NAME, null, "commentary", true));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(11L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }

  public void testQueriedIssues3() throws Exception {
    List<GithubIssue> result = myApiTaskExecutor.execute(myAccount, c ->
      GithubApiUtil.getIssuesQueried(c, myUsername2, REPO_NAME, null, "abracadabra", false));
    List<Long> issues = ContainerUtil.map(result, githubIssue -> githubIssue.getNumber());

    List<Long> expected = Arrays.asList(10L);

    assertTrue(Comparing.haveEqualElements(issues, expected));
  }
}
