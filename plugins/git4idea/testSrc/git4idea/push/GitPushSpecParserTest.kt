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
package git4idea.push

import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class GitPushSpecParserTest {

  @Test
  fun test_star_position() {
    assertInvalid("Stars should be in both parts of spec", "HEAD:refs/heads/*")
    assertInvalid("Stars should be in both parts of spec", "refs/heads/*:refs/for/master")
    assertInvalid("Star should be at the last position", "refs/for/*/branch:refs/for/*")
    assertInvalid("Star should be at the last position", "refs/heads/*:refs/*/for")
    assertInvalid("Star is supported only for refs/heads/*", "refs/for/*:refs/remotes/origin/*")

    assertValid("Star can be absent", "HEAD:refs/for/master")
    assertValid("Stars can be at the last positions of both parts", "+refs/heads/*:refs/remotes/origin/*")
    assertValid("Sole star is allowed", "*:refs/remotes/origin/*")
    assertValid("Sole star is allowed", "*:*")
  }

  @Test
  fun test_branch_name_substitution() {
    assertTarget("master", "+refs/heads/*:refs/remotes/origin/*", "refs/remotes/origin/master")
  }

  @Test
  fun test_complex_branch_name_can_be_partially_wildcarded() {
    assertTarget("qa/ticket1", "refs/heads/qa/*:refs/remotes/origin/qa/*", "refs/remotes/origin/qa/ticket1")
  }

  @Test
  fun test_one_of_several_specs_matches() {
    val specs = listOf("refs/heads/qa/*:refs/remotes/origin/qa/*", "+refs/heads/master:refs/remotes/origin/master")
    assertEquals("refs/remotes/origin/qa/ticket1", getTargetRef("qa/ticket1", specs))
    assertEquals("refs/remotes/origin/master", getTargetRef("master", specs))
    assertNull(getTargetRef("feature", specs))
  }

  @Test
  fun test_tag_specs_ignored() {
    val specs = listOf("+refs/heads/master:refs/remotes/origin/master", "refs/tags/*:refs/tags/*")
    assertNull(getTargetRef("qa/ticket1", specs))
    assertEquals("refs/remotes/origin/master", getTargetRef("master", specs))
    assertNull(getTargetRef("feature", specs))
  }

  @Test
  fun test_refs_heads_matches() {
    val specs = listOf(
      "refs/heads/*:refs/heads/pr/*",
      "refs/heads/master:refs/heads/qa/master",
      "refs/tags/*:refs/tags/*"
    )
    assertEquals("refs/heads/pr/qa/ticket1", getTargetRef("qa/ticket1", specs))
    assertEquals("refs/heads/qa/master", getTargetRef("master", specs))
  }

  @Test
  fun test_short_ref_name_wildcard() {
    val specs = listOf("ma*:refs/heads/ma*")
    assertNull(getTargetRef("qa/ticket1", specs))
  }

  private fun getTargetRef(sourceBranch: String, specs: List<String>): String? {
    val myRepo = Mockito.mock(GitRepository::class.java)
    return GitPushSpecParser.getTargetRef(myRepo, GitLocalBranch(sourceBranch), specs)
  }

  private fun assertTarget(sourceBranch: String, spec: String, expectedTarget: String) {
    assertEquals(expectedTarget, getTargetRef(sourceBranch, listOf(spec)))
  }

  private fun assertInvalid(message: String, spec: String) {
    assertNull(message, getTargetRef("master", listOf(spec)))
  }

  private fun assertValid(message: String, spec: String) {
    assertNotNull(message, getTargetRef("master", listOf(spec)))
  }
}