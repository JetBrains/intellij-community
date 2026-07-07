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
package com.intellij.vcs.log.data

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedCommitParser
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.impl.HashImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test


class VcsLogJoinerTest {

  class StringArrayBuilder {
    val result = ArrayList<String>()

    operator fun String.unaryPlus() = result.add(this)

    operator fun Collection<String>.unaryPlus() = result.addAll(this)
  }

  class TestRunner {
    private lateinit var fullLog: List<String>
    private lateinit var recentCommits: List<String>
    private lateinit var oldRefs: List<String>
    private lateinit var newRefs: List<String>
    private lateinit var expected: String
    private var expectedWithParents: String? = null

    private fun build(f: StringArrayBuilder.() -> Unit): List<String> {
      val stringArrayBuilder = StringArrayBuilder()
      stringArrayBuilder.f()
      return stringArrayBuilder.result
    }

    fun fullLog(f: StringArrayBuilder.() -> Unit) {
      fullLog = build(f)
    }

    fun recentCommits(f: StringArrayBuilder.() -> Unit) {
      recentCommits = build(f)
    }

    fun oldRefs(f: StringArrayBuilder.() -> Unit) {
      oldRefs = build(f)
    }

    fun newRefs(f: StringArrayBuilder.() -> Unit) {
      newRefs = build(f)
    }

    fun expected(f: StringArrayBuilder.() -> Unit) {
      expected = build(f).joinToString(separator = "\n")
    }

    fun expectedWithParents(f: StringArrayBuilder.() -> Unit) {
      expectedWithParents = build(f).joinToString(separator = "\n")
    }

    fun run() {
      val vcsFullLog = TimedCommitParser.log(fullLog)
      val vcsRecentCommits = TimedCommitParser.log(recentCommits)
      val vcsOldRefs = oldRefs.map { HashImpl.build(it) }
      val vcsNewRefs = newRefs.map { HashImpl.build(it) }

      val result = VcsLogJoiner<Hash, TimedVcsCommit>().addCommits(vcsFullLog, vcsOldRefs, vcsRecentCommits, vcsNewRefs).getFirst()!!
      val actual = result.joinToString(separator = "\n") { it.id.asString() }
      assertEquals(expected, actual)

      expectedWithParents?.let { expectedParents ->
        val actualWithParents = result.joinToString(separator = "\n") { commit ->
          val parents = commit.parents.joinToString(" ") { it.asString() }
          if (parents.isEmpty()) commit.id.asString() else "${commit.id.asString()}|-${parents}"
        }
        assertEquals(expectedParents, actualWithParents)
      }
    }
  }

  private fun runTest(f: TestRunner.() -> Unit) {
    val testRunner = TestRunner()
    testRunner.f()
    testRunner.run()
  }

  private val BIG_TIME = 100000000

  @Test
  fun simple() {
    runTest {
      fullLog {
        +"4|-a2|-a1"
        +"3|-b1|-a"
        +"2|-a1|-a"
        +"1|-a|-"
      }
      recentCommits {
        +"5|-f|-b1"
        +"6|-e|-a2"
      }
      oldRefs {
        +"a2"
        +"b1"
      }
      newRefs {
        +"f"
        +"e"
      }
      expected {
        +"e"
        +"f"
        +"a2"
        +"b1"
        +"a1"
        +"a"
      }
    }
  }

  @Test
  fun oneNode() {
    runTest {
      fullLog {
        +"3|-a1|-"
      }
      recentCommits {
        +"3|-a1|-"
      }
      oldRefs {
        +"a1"
      }
      newRefs {
        +"a1"
      }
      expected {
        +"a1"
      }
    }
  }

  @Test
  fun oneNodeReset() {
    runTest {
      fullLog {
        +"3|-a1|-a2"
        +"2|-a2|-"
      }
      recentCommits {
        +"2|-a2|-"
      }
      oldRefs {
        +"a2"
        +"a1"
      }
      newRefs {
        +"a2"
      }
      expected {
        +"a2"
      }
    }
  }

  @Test
  fun oneNodeReset2() {
    runTest {
      fullLog {
        +"3|-a1|-a2"
        +"2|-a2|-"
      }
      recentCommits {
        +"2|-a2|-"
      }
      oldRefs {
        +"a1"
      }
      newRefs {
        +"a2"
      }
      expected {
        +"a2"
      }
    }
  }

  @Test
  fun simpleRemoveCommits() {
    runTest {
      fullLog {
        +"4|-a2|-a1"
        +"3|-b1|-a"
        +"2|-a1|-a"
        +"1|-a|-"
      }
      recentCommits {
        +"5|-f|-b1"
        +"6|-e|-a1"
      }
      oldRefs {
        +"a2"
      }
      newRefs {
        +"f"
        +"e"
      }
      expected {
        +"e"
        +"f"
        +"b1"
        +"a1"
        +"a"
      }
    }
  }

  @Test
  fun removeCommits() {
    runTest {
      fullLog {
        +"5|-a5|-a4"
        +"4|-a4|-a2 a3"
        +"3|-a3|-a1"
        +"2|-a2|-a1"
        +"1|-a1|-"
      }
      recentCommits {
        +"6|-a6|-a3"
      }
      oldRefs {
        +"a5"
      }
      newRefs {
        +"a6"
      }
      expected {
        +"a6"
        +"a3"
        +"a1"
      }
    }
  }

  @Test
  fun removeCommits2() {
    runTest {
      fullLog {
        +"2|-a2|-a1"
        +"1|-a1|-"
      }
      recentCommits {
        +"5|-a5|-a4"
        +"3|-a3|-a2"
        +"4|-a4|-a3"
      }
      oldRefs {
        +"a2"
      }
      newRefs {
        +"a5"
      }
      expected {
        +"a5"
        +"a4"
        +"a3"
        +"a2"
        +"a1"
      }
    }
  }

  @Test
  fun removeCommits3() {
    runTest {
      fullLog {
        +"3|-a3|-a2"
        +"2|-a2|-a1"
        +"1|-a1|-"
      }
      recentCommits {
        +"2|-a2|-a1"
      }
      oldRefs {
        +"a3"
      }
      newRefs {
        +"a2"
      }
      expected {
        +"a2"
        +"a1"
      }
    }
  }

  @Test
  fun removeOldBranch() {
    runTest {
      fullLog {
        +"100|-e1|-e10"
        +(10..100000).map { "${BIG_TIME - it}|-e${it}|-e${it + 1}" }
        +"5|-e100001|-a1"
        +"4|-b2|-b1"
        +"3|-b1|-a1"
        +"1|-a1|-"
      }
      recentCommits {
        +"100|-e1|-e10"
      }
      oldRefs {
        +"e1"
        +"b2"
      }
      newRefs {
        +"e1"
      }
      expected {
        +"e1"
        +(10..100000).map { "e$it" }
        +"e100001"
        +"a1"
      }
    }
  }

  @Test
  fun addToOldBranch() {
    runTest {
      fullLog {
        +"100|-e1|-e10"
        +(10..100000).map { "${BIG_TIME - it}|-e${it}|-e${it + 1}" }
        +"5|-e100001|-a1"
        +"4|-b2|-b1"
        +"3|-b1|-a1"
        +"1|-a1|-"
      }
      recentCommits {
        +"50|-b4|-b3"
        +"49|-b3|-b2"
      }
      oldRefs {
        +"e1"
        +"b2"
      }
      newRefs {
        +"e1"
        +"b4"
      }
      expected {
        +"e1"
        +(10..100000).map { "e$it" }
        +"b4"
        +"b3"
        +"e100001"
        +"b2"
        +"b1"
        +"a1"
      }
    }
  }

  @Test
  fun removeLongBranch() {
    runTest {
      fullLog {
        +"100|-e1|-e10"
        +(10..100000).map { "${BIG_TIME - it}|-e${it}|-e${it + 1}" }
        +"5|-e100001|-a1"
        +"4|-b2|-b1"
        +"3|-b1|-a1"
        +"1|-a1|-"
      }
      recentCommits {
        +"50|-b4|-b3"
        +"49|-b3|-b2"
      }
      oldRefs {
        +"e1"
        +"b2"
      }
      newRefs {
        +"b4"
      }
      expected {
        +"b4"
        +"b3"
        +"b2"
        +"b1"
        +"a1"
      }
    }
  }

  @Test
  fun notEnoughDataExceptionTest() {
    try {
      runTest {
        fullLog {
          +"1|-a1|-"
        }
        recentCommits {
          +"3|-a3|-a2"
        }
        oldRefs {
          +"a1"
        }
        newRefs {
          +"a3"
        }
      }
    }
    catch (e: VcsLogRefreshNotEnoughDataException) {
      return
    }
    fail()
  }

  @Test
  fun illegalStateExceptionTest() {
    try {
      runTest {
        fullLog {
          +"1|-a1|-"
        }
        recentCommits {
          +"1|-a1|-"
        }
        oldRefs {
          +"a1"
          +"a2"
        }
        newRefs {
          +"a1"
        }
      }
    }
    catch (e: IllegalStateException) {
      return
    }
    fail()
  }

  @Test
  fun illegalStateExceptionTest2() {
    try {
      runTest {
        fullLog {
          +"2|-a2|-a1"
          +"1|-a1|-"
        }
        recentCommits {
          +"3|-a3|-a1"
          +"1|-a1|-"
        }
        oldRefs {
        }
        newRefs {
          +"a3"
        }
      }
    }
    catch (e: IllegalStateException) {
      return
    }
    fail()
  }

  @Test
  fun removeParallelBranch() {
    runTest {
      fullLog {
        +"4|-a4|-a1"
        +"3|-a3|-a2"
        +"2|-a2|-"
        +"1|-a1|-"
      }
      recentCommits {

      }
      oldRefs {
        +"a4"
        +"a3"
      }
      newRefs {
        +"a3"
      }
      expected {
        +"a3"
        +"a2"
      }
    }
  }

  @Test
  fun removeAll() {
    runTest {
      fullLog {
        +"4|-a4|-a1"
        +"3|-a3|-a2"
        +"2|-a2|-"
        +"1|-a1|-"
      }
      recentCommits {

      }
      oldRefs {
        +"a4"
        +"a3"
      }
      newRefs {

      }
      expected {

      }
    }
  }

  @Test
  fun staleParentPropagation() {
    // Scenario from IJPL-248963: commit C's parents changed from [B, D] to [B].
    // D had a ref "obsolete" which is now gone. D should be removed (red).
    // Bug: walking savedLog, C is green → old parents {B, D} are propagated as green
    //   → D becomes green → D survives instead of being removed.
    runTest {
      fullLog {
        +"4|-c|-b d"
        +"3|-b|-a"
        +"2|-d|-a"
        +"1|-a|-"
      }
      recentCommits {
        // C now only has parent B (D was removed)
        +"4|-c|-b"
      }
      oldRefs {
        +"c"
        +"d"
      }
      newRefs {
        // ref to D is gone
        +"c"
      }
      expected {
        +"c"
        +"b"
        +"a"
      }
    }
  }

  @Test
  fun staleParentPropagation_parentsUpdated() {
    // Verify that the returned commit C has updated parents from firstBlock,
    // not stale parents from savedLog.
    runTest {
      fullLog {
        +"4|-c|-b d"
        +"3|-b|-a"
        +"2|-d|-a"
        +"1|-a|-"
      }
      recentCommits {
        +"4|-c|-b"
      }
      oldRefs {
        +"c"
        +"d"
      }
      newRefs {
        +"c"
      }
      expected {
        +"c"
        +"b"
        +"a"
      }
      expectedWithParents {
        // C should have parent [b] only, not [b, d]
        +"c|-b"
        +"b|-a"
        +"a"
      }
    }
  }

  @Test
  fun staleParentPropagation_multipleChangedParents() {
    // Multiple commits have changed parents. Verify both are updated.
    runTest {
      fullLog {
        +"5|-e|-c d"
        +"4|-c|-b d"
        +"3|-b|-a"
        +"2|-d|-a"
        +"1|-a|-"
      }
      recentCommits {
        // E now only has parent C, C now only has parent B (D removed from both)
        +"5|-e|-c"
        +"4|-c|-b"
      }
      oldRefs {
        +"e"
        +"d"
      }
      newRefs {
        +"e"
      }
      expected {
        +"e"
        +"c"
        +"b"
        +"a"
      }
      expectedWithParents {
        +"e|-c"
        +"c|-b"
        +"b|-a"
        +"a"
      }
    }
  }

  @Test
  fun parentReplacedSameCommitHash() {
    // A→B→C becomes A→B'→C: commit A keeps the same hash but its parent
    // changed from B to B'. B is gone, B' is new.
    // A should connect to B' in the result, old B should be removed.
    runTest {
      fullLog {
        +"3|-a|-b"
        +"2|-b|-c"
        +"1|-c|-"
      }
      recentCommits {
        +"3|-a|-b2"
        +"2|-b2|-c"
      }
      oldRefs {
        +"a"
      }
      newRefs {
        +"a"
      }
      expected {
        +"a"
        +"b2"
        +"c"
      }
      expectedWithParents {
        +"a|-b2"
        +"b2|-c"
        +"c"
      }
    }
  }

  @Test
  fun sameDataRefresh() {
    // Refreshing with the exact same data should not change the log.
    runTest {
      fullLog {
        +"3|-a|-b"
        +"2|-b|-c"
        +"1|-c|-"
      }
      recentCommits {
        +"3|-a|-b"
        +"2|-b|-c"
        +"1|-c|-"
      }
      oldRefs {
        +"a"
      }
      newRefs {
        +"a"
      }
      expected {
        +"a"
        +"b"
        +"c"
      }
      expectedWithParents {
        +"a|-b"
        +"b|-c"
        +"c"
      }
    }
  }

  @Test
  fun sameDataRefreshWithIslands() {
    // Same-data refresh with disconnected islands should not change the log.
    runTest {
      fullLog {
        +"3|-a|-b"
        +"2|-b|-"
        +"2|-x|-y"
        +"1|-y|-"
      }
      recentCommits {
        +"3|-a|-b"
        +"2|-b|-"
        +"2|-x|-y"
        +"1|-y|-"
      }
      oldRefs {
        +"a"
        +"x"
      }
      newRefs {
        +"a"
        +"x"
      }
      expected {
        +"a"
        +"b"
        +"x"
        +"y"
      }
    }
  }

  @Test
  fun newCommitOnExistingChain() {
    // A new commit is added on top of an existing chain.
    runTest {
      fullLog {
        +"2|-b|-c"
        +"1|-c|-"
      }
      recentCommits {
        +"3|-a|-b"
        +"2|-b|-c"
      }
      oldRefs {
        +"b"
      }
      newRefs {
        +"a"
      }
      expected {
        +"a"
        +"b"
        +"c"
      }
      expectedWithParents {
        +"a|-b"
        +"b|-c"
        +"c"
      }
    }
  }
}
