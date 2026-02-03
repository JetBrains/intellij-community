// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class GitRefGroupsTest extends GitRefManagerTest {
  public void test_single_tracked_branch() {
    check(given("HEAD", "master", "origin/master"), Arrays.asList("HEAD"), Pair.create("Local", Arrays.asList("master")),
          Pair.create("origin/...", Arrays.asList("origin/master")));
  }

  public void test_single_local_branch() {
    check(given("HEAD", "master"), Arrays.asList("HEAD"), Pair.create("Local", Arrays.asList("master")));
  }

  public void test_local_tracked_and_remote_branch() {
    check(given("HEAD", "master", "origin/master", "origin/remote_branch", "local_branch"), Arrays.asList("HEAD"),
          Pair.create("Local", Arrays.asList("master", "local_branch")),
          Pair.create("origin/...", Arrays.asList("origin/master", "origin/remote_branch")));
  }

  private void check(@NotNull Collection<? extends VcsRef> actual,
                     @NotNull List<String> expectedSingleGroups,
                     Pair<String, List<String>>... expectedOtherGroups) {

    List<RefGroup> actualGroups = new GitRefManager(getProject(), repositoryManager).groupForBranchFilter(actual);

    List<SingletonRefGroup> singleGroups = ContainerUtil.findAll(actualGroups, SingletonRefGroup.class);
    assertEquals(expectedSingleGroups, ContainerUtil.map(singleGroups,
                                                         (Function<RefGroup, String>)singletonRefGroup -> singletonRefGroup.getName()));

    actualGroups.removeAll(singleGroups);

    assertEquals(Arrays.asList(expectedOtherGroups), ContainerUtil.map(actualGroups, refGroup -> Pair.create(refGroup.getName(), ContainerUtil.map(refGroup.getRefs(), vcsRef -> vcsRef.getName()))));
  }
}
