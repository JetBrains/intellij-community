// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class ProjectStoreDirectoryFileSetTest {
  @Test
  fun `project store files are indexable outside module roots`(@TempDir projectDir: Path): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    val subdirectory = projectDir.resolve(".idea/extra").createDirectories()
    val filePath = subdirectory.resolve("settings.txt")
    filePath.writeText("settings")

    val project = checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(projectDir, OpenProjectTask { createModule = false }))
    project.useProjectAsync {
      // WorkspaceEntitiesLifecycleActivity does not run in a unit test mode, so the same check runs here
      WorkspaceEntityLifecycleSupporterUtils.ensureAllEntitiesInWorkspaceAreAsProvidersDefined(project)

      val file = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath))
      readAction {
        val workspaceFileIndex = WorkspaceFileIndex.getInstance(project)
        assertTrue(workspaceFileIndex.isInWorkspace(file))
        assertTrue(workspaceFileIndex.isIndexable(file))
        assertFalse(ProjectFileIndex.getInstance(project).isInProject(file))
      }
    }
  }
}
