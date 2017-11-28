/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;

public class GitBranchComparatorTest extends GitRefManagerTest {

  public void test_tracked_remote_branch_is_more_important_than_its_local_branch() {
    check("origin/139", given("origin/139", "139"));
  }

  public void test_tracked_remote_branch_is_more_important_than_any_local_branch() {
    check("origin/139", given("feature", "origin/139", "139"));
  }

  public void test_untracked_remote_branch_is_less_important_than_any_local_branch() {
    check("feature", given("feature", "origin/139"));
  }

  public void test_master_is_more_important_than_other_local_branches() {
    check("master", given("feature", "master"));
  }

  public void test_origin_master_is_more_important_than_other_remote_branches() {
    check("origin/master", given("origin/139", "master", "139", "origin/master", "origin/135"));
  }

  public void test_local_branch_is_more_important_than_tag() {
    check("feature", given("refs/tags/v1", "feature", "refs/tags/v2"));
  }

  public void test_two_local_non_tracking_branches_are_compared_lexicographically() {
    check("feature", given("zoo", "feature"));
  }

  public void test_tracking_local_branch_is_more_important_than_nontracking_local_branch() {
    Collection<VcsRef> refs = given("zoo", "origin/zoo", "feature");
    refs = ContainerUtil.filter(refs, ref -> !ref.getName().equals("origin/zoo"));
    assertEquals("zoo", getTheMostPowerfulRef(refs).getName());
  }

  public void test_two_remote_non_tracking_branches_are_compared_lexicographically() {
    check("github/zoo", given("origin/feature", "origin/zoo", "github/zoo"));
  }

  public void test_local_branch_is_more_important_than_HEAD() {
    check("feature", given("feature", "HEAD"));
  }

  public void test_tag_is_more_important_than_HEAD() {
    check("refs/tags/v1", given("refs/tags/v1", "HEAD"));
  }

  private void check(@NotNull String expectedBest, @NotNull Collection<VcsRef> givenBranches) {
    VcsRef actualBest = getTheMostPowerfulRef(givenBranches);
    assertEquals(expect(expectedBest).get(0), actualBest);
  }

  @NotNull
  private VcsRef getTheMostPowerfulRef(@NotNull Collection<VcsRef> givenBranches) {
    Comparator<VcsRef> comparator = new GitRefManager(myGitRepositoryManager).getBranchLayoutComparator();
    return ContainerUtil.sorted(givenBranches, comparator).get(0);
  }
}
