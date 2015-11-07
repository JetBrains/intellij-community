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
package git4idea.push

import git4idea.push.GitPushNativeResult.Type.*
import git4idea.push.GitPushNativeResultParser.parse
import org.junit.Assert.assertEquals
import org.junit.Test

class GitPushNativeResultParserTest {

  val STANDARD_PREFIX = """
    Counting objects: 20, done.
    Delta compression using up to 8 threads.
    Compressing objects: 100% (14/14), done.
    Writing objects: 100% (20/20), 1.70 KiB | 0 bytes/s, done.
    Total 20 (delta 7), reused 0 (delta 0)
    To /Users/loki/sandbox/git/parent.git
    """.trimIndent()
  val TARGET_PREFIX = "To http://example.com/git/parent.git"
  val PUSH_DEFAULT_WARNING = """
    warning: push.default is unset; its implicit value has changed in
    Git 2.0 from 'matching' to 'simple'. To squelch this message
    and maintain the traditional behavior, use:
    
      git config --global push.default matching
    
    To squelch this message and adopt the new behavior now, use:
    
      git config --global push.default simple
    
    When push.default is set to 'matching', git will push local branches
    to the remote branches that already exist with the same name.
    
    Since Git 2.0, Git defaults to the more conservative 'simple'
    behavior, which only pushes the current branch to the corresponding
    remote branch that 'git pull' uses to update the current branch.
    
    See 'git help config' and search for 'push.default' for further information.
    (the 'simple' mode was introduced in Git 1.7.11. Use the similar mode
    'current' instead of 'simple' if you sometimes use older versions of Git)
    """.trimIndent()
  val SUCCESS_SUFFIX = "Done"
  val REJECT_SUFFIX = """
    Done
    error: failed to push some refs to '/Users/loki/sandbox/git/parent.git'
    hint: Updates were rejected because the tip of your current branch is behind
    hint: its remote counterpart. Integrate the remote changes (e.g.
    hint: 'git pull ...') before pushing again.
    hint: See the 'Note about fast-forwards' in 'git push --help' for details.
    """.trimIndent()

  @Test
  fun success() {
    val output = " \trefs/heads/master:refs/heads/master\t3e62822..a537351"
    val results = parse(listOf(STANDARD_PREFIX, TARGET_PREFIX, output, SUCCESS_SUFFIX))
    assertSingleResult(SUCCESS, "refs/heads/master", "3e62822..a537351", null, results)
  }

  @Test
  fun rejected() {
    val output = "!\trefs/heads/master:refs/heads/master\t[rejected] (non-fast-forward)"
    val results = parse(listOf(STANDARD_PREFIX, TARGET_PREFIX, output, REJECT_SUFFIX))
    assertSingleResult(REJECTED, "refs/heads/master", null, GitPushNativeResult.NO_FF_REJECT_REASON, results)
  }

  @Test
  fun rejected2() {
    val output = "!\trefs/heads/master:refs/heads/master\t[rejected] (fetch first)"
    val results = parse(listOf(STANDARD_PREFIX, TARGET_PREFIX, output, REJECT_SUFFIX))
    assertSingleResult(REJECTED, "refs/heads/master", null, GitPushNativeResult.FETCH_FIRST_REASON, results)
  }

  @Test
  fun forcedUpdate() {
    val output = "+\trefs/heads/master:refs/heads/master\tb9b3235...23760f8 (forced update)"
    val result = parse(listOf(PUSH_DEFAULT_WARNING, TARGET_PREFIX, output))
    assertSingleResult(FORCED_UPDATE, "refs/heads/master", "b9b3235...23760f8", "forced update", result)
  }

  @Test
  fun remoteRejected() {
    val output = "!\trefs/heads/master:refs/heads/master\t[remote rejected] (pre-receive hook declined)"
    val result = parse(listOf(PUSH_DEFAULT_WARNING, TARGET_PREFIX, output))
    assertSingleResult(REJECTED, "refs/heads/master", null, "pre-receive hook declined", result)
  }

  @Test
  fun upToDate() {
    val output = "=\trefs/heads/master:refs/heads/master\t[up to date]"
    val result = parse(listOf(TARGET_PREFIX, output))
    assertSingleResult(UP_TO_DATE, "refs/heads/master", result)
  }

  @Test
  fun newRef() {
    val output = "*\trefs/heads/feature:refs/heads/feature2\t[new branch]"
    val result = parse(listOf(STANDARD_PREFIX, TARGET_PREFIX, output))
    assertSingleResult(NEW_REF, "refs/heads/feature", result)
  }

  @Test
  fun withTags() {
    val output = arrayOf(" \trefs/heads/master:refs/heads/master\t7aabf91..d8369de", "*\trefs/tags/some_tag:refs/tags/some_tag\t[new tag]")
    val results = parse(listOf(*output))
    assertEquals(2, results.size)
    assertResult(SUCCESS, "refs/heads/master", "7aabf91..d8369de", null, results[0])
    assertResult(NEW_REF, "refs/tags/some_tag", null, null, results[1])
  }

  private fun assertResult(expectedType: GitPushNativeResult.Type,
                           expectedSource: String,
                           expectedRange: String?,
                           expectedReason: String? = null,
                           actualResult: GitPushNativeResult) {
    assertEquals(expectedType, actualResult.type)
    assertEquals(expectedSource, actualResult.sourceRef)
    assertEquals(expectedRange, actualResult.range)
    assertEquals(expectedReason, actualResult.reason)
  }

  private fun assertSingleResult(expectedType: GitPushNativeResult.Type,
                                 expectedSource: String,
                                 actualResults: List<GitPushNativeResult>) {
    assertSingleResult(expectedType, expectedSource, null, null, actualResults)
  }

  private fun assertSingleResult(expectedType: GitPushNativeResult.Type,
                                 expectedSource: String,
                                 expectedRange: String?,
                                 expectedReason: String?,
                                 actualResults: List<GitPushNativeResult>) {
    assertEquals(1, actualResults.size)
    val result = actualResults[0]
    assertResult(expectedType, expectedSource, expectedRange, expectedReason, result)
  }
}