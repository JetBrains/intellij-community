/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
    check(given("HEAD", "master", "origin/master"), ContainerUtil.list("HEAD"), Pair.create("Local", ContainerUtil.list("master")),
          Pair.create("Tracked", ContainerUtil.list("origin/master")),
          Pair.create("origin/...", ContainerUtil.list("origin/master")));
  }

  public void test_single_local_branch() {
    check(given("HEAD", "master"), ContainerUtil.list("HEAD"), Pair.create("Local", ContainerUtil.list("master")));
  }

  public void test_local_tracked_and_remote_branch() {
    check(given("HEAD", "master", "origin/master", "origin/remote_branch", "local_branch"), ContainerUtil.list("HEAD"),
          Pair.create("Local", ContainerUtil.list("master", "local_branch")), Pair.create("Tracked", ContainerUtil.list("origin/master")),
          Pair.create("origin/...", ContainerUtil.list("origin/master", "origin/remote_branch")));
  }

  private void check(@NotNull Collection<VcsRef> actual,
                     @NotNull List<String> expectedSingleGroups,
                     Pair<String, List<String>>... expectedOtherGroups) {

    List<RefGroup> actualGroups = new GitRefManager(repositoryManager).groupForBranchFilter(actual);

    List<SingletonRefGroup> singleGroups = ContainerUtil.findAll(actualGroups, SingletonRefGroup.class);
    assertEquals(expectedSingleGroups, ContainerUtil.map(singleGroups,
                                                         (Function<RefGroup, String>)singletonRefGroup -> singletonRefGroup.getName()));

    actualGroups.removeAll(singleGroups);

    assertEquals(Arrays.asList(expectedOtherGroups), ContainerUtil.map(actualGroups, refGroup -> Pair.create(refGroup.getName(), ContainerUtil.map(refGroup.getRefs(), vcsRef -> vcsRef.getName()))));
  }
}
