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
import java.util.Collections;
import java.util.List;

public class GitLabelComparatorTest extends GitRefManagerTest {

  public void testEmpty() {
    check(Collections.emptyList(), Collections.emptyList());
  }

  public void testSingle() {
    check(given("HEAD"),
          expect("HEAD"));
  }

  public void test_HEAD_is_at_left_from_branch() {
    check(given("master", "HEAD"),
          expect("HEAD", "master"));
  }

  public void testLocalBranchesAreComparedAsStrings() {
    check(given("release", "feature"),
          expect("feature", "release"));
  }

  public void test_tag_is_to_the_right_of_remote_branch() {
    check(given("refs/tags/v1", "origin/master"),
          expect("origin/master", "refs/tags/v1"));
  }

  public void test_master_is_to_the_left_of_other_local_branches() {
    check(given("feature", "master"),
          expect("master", "feature"));
  }

  public void test_origin_master_is_to_the_left_of_other_remote_branches() {
    check(given("origin/master", "origin/aaa"),
          expect("origin/master", "origin/aaa"));
  }

  public void test_remote_tracking_branch_is_to_the_left_of_other_remote_branches() {
    check(given("feature", "origin/aaa", "origin/feature"),
          expect("feature", "origin/feature", "origin/aaa"));
  }

  public void test_complex_1() {
    check(given("refs/tags/v1", "feature", "HEAD", "master"),
          expect("HEAD", "master", "feature", "refs/tags/v1"));
  }

  public void test_complex_2() {
    check(given("origin/master", "origin/great_feature", "refs/tags/v1", "release", "HEAD", "master"),
          expect("HEAD", "master", "release", "origin/master", "origin/great_feature", "refs/tags/v1"));
  }

  // may happen e.g. in multi-repo case
  public void testTwoMasters() {
    check(given("master", "master"),
          expect("master", "master"));
  }

  private void check(Collection<VcsRef> unsorted, List<VcsRef> expected) {
    // for the sake of simplicity we check only names of references
    List<VcsRef> actual = sort(unsorted);
    assertEquals("Collections size don't match", expected.size(), actual.size());
    for (int i = 0; i < actual.size(); i++) {
      assertEquals("Incorrect element at place " + i, expected.get(i).getName(), actual.get(i).getName());
    }
  }

  @NotNull
  private List<VcsRef> sort(@NotNull final Collection<VcsRef> refs) {
    return ContainerUtil.sorted(refs, new GitRefManager(repositoryManager).getLabelsOrderComparator());
  }
}
