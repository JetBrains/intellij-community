// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.internal.visitChildrenInVfsRecursively
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

@TestApplication
class OnlyIndexableFilesAreLoadedIntoVfsOnDirectoryCreationTest {
  @JvmField
  @RegisterExtension
  val rootDir: TempDirectoryExtension = TempDirectoryExtension()

  private fun findVirtualFile(path: Path): VirtualFile {
    return checkNotNull(VfsTestUtil.findFileByCaseSensitivePath(path.pathString)) {
      "VirtualFile not found for path: $path"
    }
  }

  @Test
  fun `test non-indexable files are not loaded into VFS on creation`(): Unit = runBlocking {
    rootDir.newDirectoryPath(".idea") // forces project.basePath to match project root which may affect how VFS files are refreshed
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later
      val isIndexable = readAction {
        WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile)
      }
      Assertions.assertFalse(isIndexable)

      rootDir.newDirectoryPath("d1/d2/d3")
      delay(1.seconds) // wait for fs events to arrive
      RefreshQueue.getInstance().refresh(true, listOf(rootVirtualFile))
      val filesInVfs = visitChildrenInVfsRecursively(rootVirtualFile).toList()
      assertThat(filesInVfs).hasSize(3)
    }
  }
}
