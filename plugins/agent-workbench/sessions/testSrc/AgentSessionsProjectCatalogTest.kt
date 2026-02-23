// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionsProjectCatalogTest {
  @Test
  fun resolveOpenProjectPathNormalizesManagerPath() {
    assertThat(resolveOpenProjectPath(managerProjectPath = "/work/project-a/", projectBasePath = null))
      .isEqualTo("/work/project-a")
  }

  @Test
  fun resolveOpenProjectPathFallsBackToBasePath() {
    assertThat(resolveOpenProjectPath(managerProjectPath = null, projectBasePath = "/work/project-a/"))
      .isEqualTo("/work/project-a")
  }

  @Test
  fun resolveOpenProjectPathReturnsNullWhenPathsMissing() {
    assertThat(resolveOpenProjectPath(managerProjectPath = null, projectBasePath = null)).isNull()
  }

  @Test
  fun resolveRecentPathCandidatePrefersDirectPathMatch() {
    data class Candidate(val id: String, val path: String?)

    val candidates = listOf(
      Candidate(id = "a", path = "/work/project-a"),
      Candidate(id = "b", path = "/work/project-b"),
    )

    val matched = resolveRecentPathCandidate(
      normalizedRecentPath = "/work/project-b",
      candidates = candidates,
      consumedCandidates = emptySet(),
      candidateProject = { it.id },
      candidatePath = { it.path },
      isPathEquivalent = { false },
    )

    assertThat(matched?.id).isEqualTo("b")
  }

  @Test
  fun resolveRecentPathCandidateFallsBackToIdentityMatcher() {
    data class Candidate(val id: String, val path: String?)

    val candidates = listOf(
      Candidate(id = "a", path = "/other/location"),
      Candidate(id = "b", path = "/work/project-b"),
    )

    val matched = resolveRecentPathCandidate(
      normalizedRecentPath = "/work/project-a",
      candidates = candidates,
      consumedCandidates = emptySet(),
      candidateProject = { it.id },
      candidatePath = { it.path },
      isPathEquivalent = { it.id == "a" },
    )

    assertThat(matched?.id).isEqualTo("a")
  }

  @Test
  fun resolveRecentPathCandidateSkipsConsumedCandidate() {
    data class Candidate(val id: String, val path: String?)

    val candidates = listOf(
      Candidate(id = "a", path = "/work/project-a"),
      Candidate(id = "b", path = "/work/project-a"),
    )

    val matched = resolveRecentPathCandidate(
      normalizedRecentPath = "/work/project-a",
      candidates = candidates,
      consumedCandidates = setOf("a"),
      candidateProject = { it.id },
      candidatePath = { it.path },
      isPathEquivalent = { false },
    )

    assertThat(matched?.id).isEqualTo("b")
  }
}
