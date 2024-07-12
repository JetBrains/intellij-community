// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.coverage

import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.graph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal class CurrentFeatureBranchBaseDetectorTest {
  @Test
  fun `test linear history`() {
    // 0 <- HEAD
    // 1
    // 2 <- master
    // 3
    val graph = graph {
      0(1)
      1(2)
      2(3)
      3()
    }
    assertCommitFound(graph, 2, 2, setOf(2))
  }

  @Test
  fun `test master ahead`() {
    // 0 <- HEAD
    // 1    2 <- master
    // |    3
    // 4  /
    val graph = graph {
      0(1)
      1(4)
      2(3)
      3(4)
      4()
    }
    assertCommitFound(graph, 4, 2, setOf(2))
  }

  @Test
  fun `test merge with master`() {
    // 0 <- HEAD
    // 1 (merge)
    // |  \
    // 2   3 <- master
    // 4 /
    val graph = graph {
      0(1)
      1(2, 3)
      2(4)
      3(4)
      4()
    }
    assertCommitFound(graph, 3, 3, setOf(3))
  }

  @Test
  fun `test master is ahead after merge`() {
    // 0 <- HEAD
    // 1 (merge) 3 <- master
    // |  \    /
    // 2    4
    // 5 /
    val graph = graph {
      0(1)
      1(2, 4)
      2(5)
      3(4)
      4(5)
      5()
    }
    assertCommitFound(graph, 4, 3, setOf(3))
  }

  @Test
  fun `test merge with other protected branch`() {
    // Here
    // 0 <- HEAD
    // 1 (merge)
    // |  \
    // 2    4 <- protected
    // 3
    // 5 <- master
    val graph = graph {
      0(1)
      1(2, 4)
      2(3)
      3(5)
      4()
      5()
    }
    val expected = listOf(CurrentFeatureBranchBaseDetector.BaseCommit(4, 4), CurrentFeatureBranchBaseDetector.BaseCommit(5, 5))
    assertCommitFound(graph, expected, setOf(4, 5))
  }

  @Test
  fun `test commit in protected branch`() {
    // 0 <- master
    // 1
    // 2 <- HEAD
    // 3
    val graph = graph {
      0(1)
      1(2)
      2(3)
      3()
    }
    assertEquals(CurrentFeatureBranchBaseDetector.Status.HeadInProtectedBranch, findBaseCommit(graph, 2, setOf(0)))
  }

  @Test
  fun `test protected branch in inaccessable`() {
    // 0 <- HEAD
    // 1
    // 2
    // 3
    val graph = graph {
      0(1)
      1(2)
      2(3)
      3()
    }
    assertEquals(CurrentFeatureBranchBaseDetector.Status.CommitHasNoProtectedParents, findBaseCommit(graph, 0, emptySet()))
  }

  @Test
  fun `test long git log`() {
    val size = 200
    val graph = graph {
      repeat(size) {
        it(it + 1)
      }
      size()
    }
    assertEquals(CurrentFeatureBranchBaseDetector.Status.SearchLimitReached, findBaseCommit(graph, 0, emptySet()))
  }

  @Test
  fun `test long git log with protected branch`() {
    val size = 200
    val graph = graph {
      0(1, 201)
      for (i in 1..<size) {
        i(i + 1)
      }
      size()
      201(201)
    }
    assertCommitFound(graph, 201, 201, setOf(200, 201))
  }

  private fun assertCommitFound(graph: LinearGraph, expectedCommitId: Int, expectedProtectedId: Int, protectedNodeIds: Set<Int>) {
    assertCommitFound(graph, listOf(CurrentFeatureBranchBaseDetector.BaseCommit(expectedCommitId, expectedProtectedId)), protectedNodeIds)
  }

  private fun assertCommitFound(graph: LinearGraph, commits: List<CurrentFeatureBranchBaseDetector.BaseCommit>, protectedNodeIds: Set<Int>) {
    val baseCommit = findBaseCommit(graph, 0, protectedNodeIds)
    assertEquals(CurrentFeatureBranchBaseDetector.Status.InternalSuccess(commits), baseCommit)
  }
}
