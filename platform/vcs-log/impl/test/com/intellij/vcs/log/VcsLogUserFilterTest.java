/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.ui.filter.VcsLogUserFilterImpl;
import com.intellij.vcs.log.util.VcsUserUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.singleton;

public abstract class VcsLogUserFilterTest {
  @NotNull protected final Project myProject;
  @NotNull protected final VcsLogProvider myLogProvider;
  @NotNull protected final VcsLogObjectsFactory myObjectsFactory;

  public VcsLogUserFilterTest(@NotNull VcsLogProvider logProvider, @NotNull Project project) {
    myProject = project;
    myLogProvider = logProvider;
    myObjectsFactory = ServiceManager.getService(myProject, VcsLogObjectsFactory.class);
  }

  public void testYoutrackIssuesWithWeirdNames() throws Exception {
    MultiMap<VcsUser, String> commits =
      generateHistory("User [company]", "user@company.com", "Userovich, User", "userovich@company.com", "User (user)",
                      "useruser@company.com");

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      checkFilterForUser(user, commits.keySet(), commits.get(user), builder);
    }
    assertFilteredCorrectly(builder);
  }

  public void testWeirdCharacters() throws Exception {
    List<String> names = ContainerUtil.newArrayList();

    for (Character c : UserNameRegex.EXTENDED_REGEX_CHARS) {
      String name = "user" + Character.toString(c) + "userovich";
      names.add(name);
      names.add(name + "@company.com");
    }

    MultiMap<VcsUser, String> commits = generateHistory(ArrayUtil.toStringArray(names));

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      checkFilterForUser(user, commits.keySet(), commits.get(user), builder);
    }
    assertFilteredCorrectly(builder);
  }

  public void testFullMatching() throws Exception {
    VcsUser nik = myObjectsFactory.createUser("nik", "nik@company.com");
    List<VcsUser> users = Arrays.asList(nik,
                                        myObjectsFactory.createUser("Chainik", "chainik@company.com"),
                                        myObjectsFactory.createUser("Nik Fury", "nikfury@company.com"),
                                        myObjectsFactory.createUser("nikniknik", "nikniknik@company.com"));

    MultiMap<VcsUser, String> commits = generateHistory(users);
    StringBuilder builder = new StringBuilder();
    checkFilterForUser(nik, commits.keySet(), commits.get(nik), builder);
    assertFilteredCorrectly(builder);
  }

  public void testSynonyms() throws Exception {
    MultiMap<VcsUser, String> commits =
      generateHistory("User Userovich", "user@company.com", "User.Userovich", "user@company.com");

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      checkFilterForUser(user, commits.keySet(), commits.values(), builder);
    }
    assertFilteredCorrectly(builder);
  }

  private void checkFilterForUser(@NotNull VcsUser user,
                                  @NotNull Set<VcsUser> allUsers,
                                  @NotNull Collection<? extends String> expectedHashes,
                                  @NotNull StringBuilder errorMessageBuilder) throws VcsException {
    VcsLogUserFilter userFilter =
      new VcsLogUserFilterImpl(singleton(VcsUserUtil.getShortPresentation(user)), Collections.emptyMap(), allUsers);
    List<String> actualHashes = getFilteredHashes(userFilter);

    List<String> expected = ContainerUtil.reverse(ContainerUtil.newArrayList(expectedHashes));
    if (!expected.equals(actualHashes)) {
      errorMessageBuilder.append(TestCase.format(user.toString(), expectedHashes, actualHashes)).append("\n");
    }
  }

  @NotNull
  private List<String> getFilteredHashes(@NotNull VcsLogUserFilter filter) throws VcsException {
    VcsLogFilterCollectionImpl filters = new VcsLogFilterCollectionImpl(null, filter, null, null, null, null, null);
    List<TimedVcsCommit> commits = myLogProvider.getCommitsMatchingFilter(myProject.getBaseDir(), filters, -1);
    return ContainerUtil.map(commits, new Function<TimedVcsCommit, String>() {
      @Override
      public String fun(TimedVcsCommit commit) {
        return commit.getId().asString();
      }
    });
  }

  @NotNull
  private MultiMap<VcsUser, String> generateHistory(String... names) throws IOException {
    TestCase.assertTrue("Incorrect user names (should be pairs of users and emails) " + Arrays.toString(names), names.length % 2 == 0);

    List<VcsUser> users = ContainerUtil.newArrayList();
    for (int i = 0; i < names.length / 2; i++) {
      users.add(myObjectsFactory.createUser(names[2 * i], names[2 * i + 1]));
    }

    return generateHistory(users);
  }

  @NotNull
  private MultiMap<VcsUser, String> generateHistory(@NotNull List<VcsUser> users) throws IOException {
    MultiMap<VcsUser, String> commits = MultiMap.createLinked();

    for (VcsUser user : users) {
      recordCommit(commits, user);
    }

    VfsUtil.markDirtyAndRefresh(false, true, false, myProject.getBaseDir());
    return commits;
  }

  private static void assertFilteredCorrectly(@NotNull StringBuilder builder) {
    TestCase.assertTrue("Incorrectly filtered log for\n" + builder.toString(), builder.toString().isEmpty());
  }

  private void recordCommit(@NotNull MultiMap<VcsUser, String> commits, @NotNull VcsUser user) throws IOException {
    String commit = commit(user);
    commits.putValue(user, commit);
  }

  @NotNull
  protected abstract String commit(VcsUser user) throws IOException;
}
