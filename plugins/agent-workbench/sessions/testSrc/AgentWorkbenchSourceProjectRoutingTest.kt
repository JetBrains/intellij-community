// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.SourceProjectRouter
import com.intellij.agent.workbench.sessions.service.normalizeOpenableSourceProjectPath
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class AgentWorkbenchSourceProjectRoutingTest {
  @Test
  fun canonicalManagedPathIsUsedForDirectOpenProjectReuse() {
    val router = testRouter(
      managedProjectPath = { path ->
        if (path == Path.of("/repo")) "/repo/sample.sln" else path.toString()
      },
      openProjects = listOf("open-project"),
      projectIdentityPath = { "/repo/sample.sln" },
      isPathEquivalent = { _, _ -> false },
      openProjectByPath = { _, _ -> error("should reuse the already open project") },
    )

    val project = router.findOpenProject("/repo")

    assertThat(project).isEqualTo("open-project")
  }

  @Test
  fun pathEquivalenceFallbackReusesOpenProjectWhenManagedPathDiffers() {
    val router = testRouter(
      managedProjectPath = { _ -> "/repo/from-manager.sln" },
      openProjects = listOf("open-project"),
      projectIdentityPath = { "/repo/other.sln" },
      isPathEquivalent = { _, path -> path == Path.of("/repo") },
      openProjectByPath = { _, _ -> error("should reuse the already open project") },
    )

    val project = router.findOpenProject("/repo")

    assertThat(project).isEqualTo("open-project")
  }

  @Test
  fun canonicalManagedPathIsUsedWhenOpeningClosedProject() {
    runBlocking(Dispatchers.Default) {
      val openedPath = AtomicReference<Path?>(null)
      val router = testRouter(
        managedProjectPath = { path ->
          if (path == Path.of("/repo")) "/repo/sample.sln" else path.toString()
        },
        openProjects = emptyList(),
        projectIdentityPath = { error("no open projects expected") },
        isPathEquivalent = { _, _ -> false },
        openProjectByPath = { path, _ ->
          openedPath.set(path)
          "opened-project"
        },
      )

      val project = router.openOrReuseProject("/repo")

      assertThat(project).isEqualTo("opened-project")
      assertThat(openedPath.get()).isEqualTo(Path.of("/repo/sample.sln"))
    }
  }

  @Test
  fun normalizeOpenableSourceProjectPathRejectsBlankAndDedicatedPaths() {
    assertThat(normalizeOpenableSourceProjectPath("   ")).isNull()
    assertThat(normalizeOpenableSourceProjectPath(AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath())).isNull()
  }

  @Test
  fun normalizeOpenableSourceProjectPathNormalizesOpenablePaths() {
    assertThat(normalizeOpenableSourceProjectPath("/repo/child/..//sample"))
      .isEqualTo("/repo/sample")
  }
}

private fun testRouter(
  managedProjectPath: (Path) -> String?,
  openProjects: List<String>,
  projectIdentityPath: (String) -> String?,
  isPathEquivalent: (String, Path) -> Boolean,
  openProjectByPath: suspend (Path, OpenProjectTask) -> String?,
): SourceProjectRouter<String> {
  return SourceProjectRouter(
    parsePath = { normalizedPath -> Path.of(normalizedPath) },
    normalizePath = { it },
    resolveManagedPath = managedProjectPath,
    openProjectsProvider = { openProjects },
    projectIdentityPath = projectIdentityPath,
    isPathEquivalent = isPathEquivalent,
    openProjectByPath = openProjectByPath,
  )
}
