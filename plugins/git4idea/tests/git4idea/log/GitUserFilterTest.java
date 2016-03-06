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
package git4idea.log;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.ui.filter.VcsLogUserFilterImpl;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static git4idea.test.GitExecutor.modify;
import static java.util.Collections.singleton;

public class GitUserFilterTest extends GitSingleRepoTest {
  private GitLogProvider myLogProvider;
  private VcsLogObjectsFactory myObjectsFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myLogProvider = GitTestUtil.findGitLogProvider(myProject);
    myObjectsFactory = ServiceManager.getService(myProject, VcsLogObjectsFactory.class);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();

    myObjectsFactory = null;
    myLogProvider = null;
  }

  public void testYoutrackIssuesWithWeirdNames() throws Exception {
    MultiMap<VcsUser, String> commits =
      generateHistory("User [company]", "user@company.com", "Userovich, User", "userovich@company.com", "User (user)",
                      "useruser@company.com");

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      VcsLogUserFilter userFilter =
        new VcsLogUserFilterImpl(singleton(user.getName() + " <" + user.getEmail() + ">"), Collections.<VirtualFile, VcsUser>emptyMap(),
                                 commits.keySet());
      List<String> actualHashes = getFilteredHashes(userFilter);

      List<String> expected = ContainerUtil.reverse(ContainerUtil.newArrayList(commits.get(user)));
      if (!expected.equals(actualHashes)) {
        builder.append(TestCase.format(user.toString(), commits.get(user), actualHashes)).append("\n");
      }
    }
    assertTrue("Incorrectly filtered log for\n" + builder.toString(), builder.toString().isEmpty());
  }

  public void testWeirdCharacters() throws Exception {
    List<String> names = ContainerUtil.newArrayList();

    for (Character c : new char[]{'.', '^', '$', '*', '+', '-', '?', '(', ')', '[', ']', '{', '}', '|'}) {
      String name = "user" + Character.toString(c) + "userovich";
      names.add(name);
      names.add(name + "@company.com");
    }

    MultiMap<VcsUser, String> commits =
      generateHistory(ArrayUtil.toStringArray(names));

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      VcsLogUserFilter userFilter =
        new VcsLogUserFilterImpl(singleton(user.getName()), Collections.<VirtualFile, VcsUser>emptyMap(),
                                 commits.keySet());
      List<String> actualHashes = getFilteredHashes(userFilter);

      List<String> expected = ContainerUtil.reverse(ContainerUtil.newArrayList(commits.get(user)));
      if (!expected.equals(actualHashes)) {
        builder.append(TestCase.format(user.toString(), commits.get(user), actualHashes)).append("\n");
      }
    }
    assertTrue("Incorrectly filtered log for\n" + builder.toString(), builder.toString().isEmpty());
  }

  @NotNull
  private List<String> getFilteredHashes(@NotNull VcsLogUserFilter filter) throws VcsException {
    VcsLogFilterCollectionImpl filters = new VcsLogFilterCollectionImpl(null, filter, null, null, null, null, null);
    List<TimedVcsCommit> commits = myLogProvider.getCommitsMatchingFilter(myProjectRoot, filters, -1);
    return ContainerUtil.map(commits, new Function<TimedVcsCommit, String>() {
      @Override
      public String fun(TimedVcsCommit commit) {
        return commit.getId().asString();
      }
    });
  }

  private MultiMap<VcsUser, String> generateHistory(String... names) throws IOException {
    MultiMap<VcsUser, String> commits = MultiMap.createLinked();

    assertTrue("Incorrect user names (should be pairs of users and emails) " + Arrays.toString(names), names.length % 2 == 0);

    for (int i = 0; i < names.length / 2; i++) {
      recordCommit(commits, names[2 * i], names[2 * i + 1]);
    }

    refresh();
    return commits;
  }

  private void recordCommit(@NotNull MultiMap<VcsUser, String> commits, @NotNull String name, @NotNull String email) throws IOException {
    VcsUser user = myObjectsFactory.createUser(name, email);
    String commit = commit(user);
    commits.putValue(user, commit);
  }

  @NotNull
  private static String commit(@NotNull VcsUser user) throws IOException {
    GitTestUtil.setupUsername(user.getName(), user.getEmail());
    String commit = modify("file.txt");
    GitTestUtil.setupDefaultUsername();
    return commit;
  }
}
