// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.git.GitWorktreeInfo
import com.intellij.agent.workbench.sessions.model.ProjectBuildSystemBadge
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.model.WorktreeEntry
import com.intellij.agent.workbench.sessions.service.ProjectBuildSystemBadgeCatalogCache
import com.intellij.agent.workbench.sessions.service.ProjectOpenProcessorSnapshot
import com.intellij.agent.workbench.sessions.service.buildAgentSessionProjectPathCandidates
import com.intellij.agent.workbench.sessions.service.buildRepoProjectEntry
import com.intellij.agent.workbench.sessions.service.detectProjectBuildSystemBadge
import com.intellij.agent.workbench.sessions.service.resolveAgentSessionProjectDisplayName
import com.intellij.agent.workbench.sessions.service.resolveOpenProjectPath
import com.intellij.agent.workbench.sessions.service.resolveRecentPathCandidate
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon

class AgentSessionProjectCatalogTest {
  @Test
  fun resolveAgentSessionProjectDisplayNamePrefersRecentDisplayName() {
    assertThat(
      resolveAgentSessionProjectDisplayName(
        path = "/work/project-a",
        project = null,
        recentProjectDisplayName = { "Workspace Project A" },
        recentProjectName = { "Project A" },
      )
    ).isEqualTo("Workspace Project A")
  }

  @Test
  fun resolveAgentSessionProjectDisplayNameFallsBackToRecentProjectName() {
    assertThat(
      resolveAgentSessionProjectDisplayName(
        path = "/work/project-a",
        project = null,
        recentProjectDisplayName = { null },
        recentProjectName = { "Project A" },
      )
    ).isEqualTo("Project A")
  }

  @Test
  fun resolveAgentSessionProjectDisplayNameFallsBackToPathName() {
    assertThat(
      resolveAgentSessionProjectDisplayName(
        path = "/work/project-a",
        project = null,
        recentProjectDisplayName = { null },
        recentProjectName = { "" },
      )
    ).isEqualTo("project-a")
  }

  @Test
  fun buildAgentSessionProjectPathCandidatesFallsBackToPathsForDuplicateLabels() {
    assertThat(
      buildAgentSessionProjectPathCandidates(
        paths = listOf("/work/repo-a", "/tmp/repo-a"),
        resolveDisplayName = { "Repo A" },
      )
    ).containsExactly(
      AgentPromptProjectPathCandidate(path = "/work/repo-a", displayName = "/work/repo-a"),
      AgentPromptProjectPathCandidate(path = "/tmp/repo-a", displayName = "/tmp/repo-a"),
    )
  }

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
    data class Candidate(@JvmField val id: String, @JvmField val path: String?)

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
    data class Candidate(@JvmField val id: String, @JvmField val path: String?)

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
    data class Candidate(@JvmField val id: String, @JvmField val path: String?)

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

  @Test
  fun buildRepoProjectEntryAssignsMainBranchToStandaloneRepo() {
    val mainRaw = ProjectEntry(path = "/work/project-a", name = "Project A", project = null)

    val entry = buildRepoProjectEntry(
      mainRaw = mainRaw,
      repoRoot = mainRaw.path,
      worktreeEntries = emptyList(),
      discoveredWorktrees = listOf(
        GitWorktreeInfo(path = mainRaw.path, branch = "refs/heads/feature-x", isMain = true)
      ),
    )

    assertThat(entry).isEqualTo(mainRaw.copy(branch = "feature-x"))
  }

  @Test
  fun buildRepoProjectEntryKeepsWorktreesAndMainBranchForRepoGroups() {
    val mainRaw = ProjectEntry(path = "/work/project-a", name = "Project A", project = null)
    val worktreeEntries = listOf(
      WorktreeEntry(
        path = "/work/project-a-feature",
        name = "project-a-feature",
        branch = "feature-x",
        project = null,
      )
    )

    val entry = buildRepoProjectEntry(
      mainRaw = mainRaw,
      repoRoot = mainRaw.path,
      worktreeEntries = worktreeEntries,
      discoveredWorktrees = listOf(
        GitWorktreeInfo(path = mainRaw.path, branch = "refs/heads/main", isMain = true),
        GitWorktreeInfo(path = "/work/project-a-feature", branch = "refs/heads/feature-x", isMain = false),
      ),
    )

    assertThat(entry).isEqualTo(mainRaw.copy(branch = "main", worktreeEntries = worktreeEntries))
  }

  @Test
  fun detectProjectBuildSystemBadgeUsesSingleIconfulProcessorMatch() {
    val icon = EmptyIcon.create(16, 16)

    val badge = detectProjectBuildSystemBadge(
      file = LightVirtualFile("project"),
      processors = listOf(MatchingGradleProcessor(icon = icon), MatchingNullIconProcessor()),
    )

    assertThat(badge).isNotNull()
    assertThat(badge?.id).isEqualTo(MatchingGradleProcessor::class.java.name)
    assertThat(badge?.icon).isSameAs(icon)
  }

  @Test
  fun detectProjectBuildSystemBadgeFallsBackToGenericWhenMultipleProcessorsMatch() {
    val badge = detectProjectBuildSystemBadge(
      file = LightVirtualFile("project"),
      processors = listOf(
        MatchingGradleProcessor(icon = EmptyIcon.create(16, 16)),
        MatchingMavenProcessor(icon = EmptyIcon.create(16, 16)),
      ),
    )

    assertThat(badge).isNull()
  }

  @Test
  fun buildSystemBadgeCacheReusesDetectedBadgeForResolvedPath() {
    val cache = ProjectBuildSystemBadgeCatalogCache()
    val snapshot = ProjectOpenProcessorSnapshot(emptyList())
    val file = LightVirtualFile("project")
    val badge = ProjectBuildSystemBadge("gradle", EmptyIcon.create(16, 16))
    var resolveCalls = 0
    var detectCalls = 0

    val first = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = snapshot,
      fileResolver = {
        resolveCalls++
        file
      },
      detector = { _, _ ->
        detectCalls++
        badge
      },
    )
    val second = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = snapshot,
      fileResolver = {
        resolveCalls++
        file
      },
      detector = { _, _ ->
        detectCalls++
        error("detector should not be called after cache hit")
      },
    )

    assertThat(first).isSameAs(badge)
    assertThat(second).isSameAs(badge)
    assertThat(resolveCalls).isEqualTo(1)
    assertThat(detectCalls).isEqualTo(1)
  }

  @Test
  fun buildSystemBadgeCacheReusesNegativeResultForResolvedPath() {
    val cache = ProjectBuildSystemBadgeCatalogCache()
    val snapshot = ProjectOpenProcessorSnapshot(emptyList())
    val file = LightVirtualFile("project")
    var resolveCalls = 0
    var detectCalls = 0

    val first = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = snapshot,
      fileResolver = {
        resolveCalls++
        file
      },
      detector = { _, _ ->
        detectCalls++
        null
      },
    )
    val second = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = snapshot,
      fileResolver = {
        resolveCalls++
        file
      },
      detector = { _, _ ->
        detectCalls++
        error("detector should not be called after negative cache hit")
      },
    )

    assertThat(first).isNull()
    assertThat(second).isNull()
    assertThat(resolveCalls).isEqualTo(1)
    assertThat(detectCalls).isEqualTo(1)
  }

  @Test
  fun buildSystemBadgeCacheDoesNotCacheMissingVfsPath() {
    val cache = ProjectBuildSystemBadgeCatalogCache()
    val snapshot = ProjectOpenProcessorSnapshot(emptyList())
    val file = LightVirtualFile("project")
    var resolveCalls = 0
    var detectCalls = 0
    var fileAvailable = false
    val badge = ProjectBuildSystemBadge("gradle", EmptyIcon.create(16, 16))

    val first = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = snapshot,
      fileResolver = {
        resolveCalls++
        if (fileAvailable) file else null
      },
      detector = { _, _ ->
        detectCalls++
        badge
      },
    )
    fileAvailable = true
    val second = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = snapshot,
      fileResolver = {
        resolveCalls++
        if (fileAvailable) file else null
      },
      detector = { _, _ ->
        detectCalls++
        badge
      },
    )

    assertThat(first).isNull()
    assertThat(second).isSameAs(badge)
    assertThat(resolveCalls).isEqualTo(2)
    assertThat(detectCalls).isEqualTo(1)
  }

  @Test
  fun buildSystemBadgeCacheClearsWhenProcessorSnapshotChanges() {
    val cache = ProjectBuildSystemBadgeCatalogCache()
    val firstSnapshot = ProjectOpenProcessorSnapshot(emptyList())
    val secondSnapshot = ProjectOpenProcessorSnapshot(emptyList())
    val file = LightVirtualFile("project")
    var detectCalls = 0
    val firstBadge = ProjectBuildSystemBadge("gradle", EmptyIcon.create(16, 16))
    val secondBadge = ProjectBuildSystemBadge("maven", EmptyIcon.create(16, 16))

    val first = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = firstSnapshot,
      fileResolver = { file },
      detector = { _, _ ->
        detectCalls++
        firstBadge
      },
    )
    val second = cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = secondSnapshot,
      fileResolver = { file },
      detector = { _, _ ->
        detectCalls++
        secondBadge
      },
    )

    assertThat(first).isSameAs(firstBadge)
    assertThat(second).isSameAs(secondBadge)
    assertThat(detectCalls).isEqualTo(2)
  }

  @Test
  fun buildSystemBadgeCachePrunesPathsOutsideActiveCatalogEntries() {
    val cache = ProjectBuildSystemBadgeCatalogCache()
    val snapshot = ProjectOpenProcessorSnapshot(emptyList())
    val file = LightVirtualFile("project")
    var removedPathDetectCalls = 0

    cache.getOrDetect(
      normalizedPath = "/work/project-a",
      snapshot = snapshot,
      fileResolver = { file },
      detector = { _, _ -> ProjectBuildSystemBadge("gradle", EmptyIcon.create(16, 16)) },
    )
    cache.getOrDetect(
      normalizedPath = "/work/project-b",
      snapshot = snapshot,
      fileResolver = { file },
      detector = { _, _ ->
        removedPathDetectCalls++
        ProjectBuildSystemBadge("maven", EmptyIcon.create(16, 16))
      },
    )

    cache.prune(setOf("/work/project-a"))
    cache.getOrDetect(
      normalizedPath = "/work/project-b",
      snapshot = snapshot,
      fileResolver = { file },
      detector = { _, _ ->
        removedPathDetectCalls++
        ProjectBuildSystemBadge("maven", EmptyIcon.create(16, 16))
      },
    )

    assertThat(removedPathDetectCalls).isEqualTo(2)
  }

  private abstract class TestProjectOpenProcessor(
    private val processorName: String,
    private val matches: Boolean,
    private val processorIcon: Icon?,
  ) : ProjectOpenProcessor() {
    override val name: String
      get() = processorName

    override fun canOpenProject(file: VirtualFile): Boolean = matches

    override fun getIcon(file: VirtualFile): Icon? = processorIcon

    override suspend fun openProjectAsync(
      virtualFile: VirtualFile,
      projectToClose: Project?,
      forceOpenInNewFrame: Boolean,
    ): Project? = null
  }

  private class MatchingGradleProcessor(icon: Icon?) : TestProjectOpenProcessor("Gradle", true, icon)

  private class MatchingMavenProcessor(icon: Icon?) : TestProjectOpenProcessor("Maven", true, icon)

  private class MatchingNullIconProcessor : TestProjectOpenProcessor("Generic", true, null)
}
