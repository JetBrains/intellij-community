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
package git4idea.push;

import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class GitPushSpecParserTest {

  @Test
  public void test_star_position() {
    assertInvalid("Stars should be in both parts of spec", "HEAD:refs/heads/*");
    assertInvalid("Stars should be in both parts of spec", "refs/heads/*:refs/for/master");
    assertInvalid("Star should be at the last position", "refs/for/*/branch:refs/for/*");
    assertInvalid("Star should be at the last position", "refs/heads/*:refs/*/for");
    assertInvalid("Star is supported only for refs/heads/*", "refs/for/*:refs/remotes/origin/*");

    assertValid("Star can be absent", "HEAD:refs/for/master");
    assertValid("Stars can be at the last positions of both parts", "+refs/heads/*:refs/remotes/origin/*");
    assertValid("Sole star is allowed", "*:refs/remotes/origin/*");
    assertValid("Sole star is allowed", "*:*");
  }

  @Test
  public void test_branch_name_substitution() {
    assertTarget("master", "+refs/heads/*:refs/remotes/origin/*", "refs/remotes/origin/master");
  }
  
  @Test
  public void test_complex_branch_name_can_be_partially_wildcarded() {
    assertTarget("qa/ticket1", "refs/heads/qa/*:refs/remotes/origin/qa/*", "refs/remotes/origin/qa/ticket1");
  }

  @Test
  public void test_one_of_several_specs_matches() {
    List<String> specs = Arrays.asList("+refs/heads/master:refs/remotes/origin/master",
                                       "refs/heads/qa/*:refs/remotes/origin/qa/*");
    assertEquals("refs/remotes/origin/qa/ticket1", getTargetRef("qa/ticket1", specs));
    assertEquals("refs/remotes/origin/master", getTargetRef("master", specs));
    assertNull(getTargetRef("feature", specs));
  }

  @Nullable
  private static String getTargetRef(@NotNull String sourceBranch, @NotNull List<String> specs) {
    GitRepository myRepo = Mockito.mock(GitRepository.class);
    return GitPushSpecParser.getTargetRef(myRepo, sourceBranch, specs);
  }

  private static void assertTarget(@NotNull String sourceBranch, @NotNull String spec, @NotNull String expectedTarget) {
    assertEquals(expectedTarget, getTargetRef(sourceBranch, Collections.singletonList(spec)));
  }

  private static void assertInvalid(@NotNull String message, @NotNull String spec) {
    assertNull(message, getTargetRef("master", Collections.singletonList(spec)));
  }

  private static void assertValid(@NotNull String message, @NotNull String spec) {
    assertNotNull(message, getTargetRef("master", Collections.singletonList(spec)));
  }
}