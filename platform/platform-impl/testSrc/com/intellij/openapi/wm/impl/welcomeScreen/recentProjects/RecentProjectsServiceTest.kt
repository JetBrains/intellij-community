// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.wm.ex.isIndexingActivitiesSuppressed
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * A recent project without a workspace model cache reads its module files during the load, and the root change asks for the capabilities.
 * The first answer is cached for the life of the project, so the service must mark the project before the load asks.
 */
@TestApplication
internal class RecentProjectsServiceTest {
  @Test
  fun `a directory-based recent project has indexing suppressed`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val projectDir = createModule(tempDir.resolve("recent"))
    projectDir.resolve(Project.DIRECTORY_STORE_FOLDER).createDirectories().resolve("modules.xml").writeText(modulesXml())

    assertThat(onRecentProject(projectDir, action = { isIndexingActivitiesSuppressed(it) })).isTrue()
  }

  /** The recent-project path of an `.ipr` project is the `.ipr` file, and the base path of the project is its parent directory. */
  @Test
  fun `an ipr-based recent project has indexing suppressed`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val projectDir = createModule(tempDir.resolve("recent"))
    val iprFile = projectDir.resolve("recent.ipr")
    iprFile.writeText(modulesXml())

    assertThat(onRecentProject(iprFile, action = { isIndexingActivitiesSuppressed(it) })).isTrue()
  }

  /** The service must reuse an open project, because a second instance for the same location collides on the project storages. */
  @Test
  fun `an open ipr-based project is reused`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val iprFile = createModule(tempDir.resolve("recent")).resolve("recent.ipr")
    iprFile.writeText(modulesXml())
    TrustedProjects.setProjectTrusted(iprFile, isTrusted = true)
    val options = createTestOpenProjectOptions(runPostStartUpActivities = false)

    checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(iprFile, options)).useProjectAsync { openProject ->
      assertThat(onRecentProject(iprFile, action = { it })).isSameAs(openProject)
    }
  }

  // Runs [action] on the project that the service gives the operations for [projectPath].
  private suspend fun <T> onRecentProject(projectPath: Path, action: suspend (Project) -> T): T {
    TrustedProjects.setProjectTrusted(projectPath, isTrusted = true)
    val service = RecentProjectsService.getInstance()
    val result = CompletableDeferred<T>()
    try {
      service.runOnRecentProject(projectPath, operation = { project -> result.complete(action(project)) })
      return result.await()
    }
    finally {
      service.disposeHeldProjects()
    }
  }

  // A module with a source root, so the load has roots to read.
  private fun createModule(projectDir: Path): Path {
    projectDir.resolve("src").createDirectories()
    projectDir.resolve("recent.iml").writeText($$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="EMPTY_MODULE" version="4">
        <component name="NewModuleRootManager">
          <content url="file://$MODULE_DIR$">
            <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />
          </content>
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>
    """.trimIndent())
    return projectDir
  }

  private fun modulesXml(): String = $$"""
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="ProjectModuleManager">
        <modules>
          <module fileurl="file://$PROJECT_DIR$/recent.iml" filepath="$PROJECT_DIR$/recent.iml" />
        </modules>
      </component>
    </project>
  """.trimIndent()
}
