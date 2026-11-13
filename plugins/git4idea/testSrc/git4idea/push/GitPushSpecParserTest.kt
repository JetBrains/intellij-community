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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class GitPushSpecParserTest {

  @Test
  fun test_star_position() {
    assertNull(getTargetRefMaster("HEAD:refs/heads/*"), "Stars should be in both parts of spec")
    assertNull(getTargetRefMaster("refs/heads/*:refs/for/master"), "Stars should be in both parts of spec")
    assertNull(getTargetRefMaster("refs/for/*/branch:refs/for/*"), "Star should be at the last position")
    assertNull(getTargetRefMaster("refs/heads/*:refs/*/for"), "Star should be at the last position")
    assertNull(getTargetRefMaster("refs/for/*:refs/remotes/origin/*"), "Star is supported only for refs/heads/*")

    assertNotNull(getTargetRefMaster("HEAD:refs/for/master"), "Star can be absent")
    assertNotNull(getTargetRefMaster("+refs/heads/*:refs/remotes/origin/*"), "Stars can be at the last positions of both parts")
    assertNotNull(getTargetRefMaster("*:refs/remotes/origin/*"), "Sole star is allowed")
    assertNotNull(getTargetRefMaster("*:*"), "Sole star is allowed")
  }

  @Test
  fun test_branch_name_substitution() {
    assertEquals("refs/remotes/origin/master", getTargetRefMaster("+refs/heads/*:refs/remotes/origin/*"))
  }

  @Test
  fun test_complex_branch_name_can_be_partially_wildcarded() {
    assertEquals("refs/remotes/origin/qa/ticket1", getTargetRef("qa/ticket1", listOf("refs/heads/qa/*:refs/remotes/origin/qa/*")))
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
    assertNull(getTargetRef("qa/ticket1", listOf("ma*:refs/heads/ma*")))
  }

  private fun getTargetRef(sourceBranch: String, specs: List<String>): String? {
    val myRepo = Mockito.mock(GitRepository::class.java)
    return GitPushSpecParser.getTargetRef(myRepo, GitLocalBranch(sourceBranch), specs)
  }

  private fun getTargetRefMaster(spec: String): String? = getTargetRef("master", listOf(spec))
}