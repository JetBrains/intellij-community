// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.internal.visitChildrenInVfsRecursively
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.indexing.testEntities.ExcludedKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.ExcludedTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
class OnlyIndexableFilesAreLoadedIntoVfsOnDirectoryCreationTest {
  @JvmField
  @RegisterExtension
  val rootDir: TempDirectoryExtension = TempDirectoryExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludedKindFileSetTestContributor(), disposable)
  }

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
      Assertions.assertFalse(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      rootDir.newDirectoryPath("d1/d2/d3")
      delay(1.seconds) // wait for fs events to arrive
      RefreshQueue.getInstance().refresh(true, listOf(rootVirtualFile))
      val filesInVfs = visitChildrenInVfsRecursively(rootVirtualFile).toList()
      assertThat(filesInVfs).hasSize(3)
    }
  }

  @Test
  fun `test indexable by non-recursive files are not loaded into VFS on creation`(): Unit = runBlocking {
    rootDir.newDirectoryPath(".idea") // forces project.basePath to match project root which may affect how VFS files are refreshed
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later
      Assertions.assertFalse(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      project.workspaceModel.update("Add indexable non-recursive root") {
        it.addEntity(NonRecursiveTestEntity(rootVirtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager()), NonPersistentEntitySource))
      }

      Assertions.assertTrue(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      rootDir.newDirectoryPath("d1/d2/d3")
      delay(1.seconds) // wait for fs events to arrive
      RefreshQueue.getInstance().refresh(true, listOf(rootVirtualFile))
      val filesInVfs = visitChildrenInVfsRecursively(rootVirtualFile).toList()
      assertThat(filesInVfs).hasSize(3)
    }
  }

  @Test
  fun `test that VFS loading stops at excluded dir registered via an entity`(): Unit = runBlocking {
    rootDir.newFileNio("pom.xml").writeText(pom)

    ProjectUtil.openOrImportAsync(rootDir.rootPath)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later
      Assertions.assertTrue(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      val excludedUrl = rootDir.rootPath.resolve("d1/excluded").toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
      project.workspaceModel.update("Add excluded dir $excludedUrl") {
        it.addEntity(ExcludedTestEntity(excludedUrl, NonPersistentEntitySource))
      }

      rootDir.newDirectoryPath("d1/excluded/d3")
      delay(1.seconds) // wait for fs events to arrive
      RefreshQueue.getInstance().refresh(true, listOf(rootVirtualFile))
      val filesInVfs = visitChildrenInVfsRecursively(rootVirtualFile).toList()
      assertThat(filesInVfs).noneMatch { it.name == "d3" }
    }
  }

  private val pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                    "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                    "    <modelVersion>4.0.0</modelVersion>\n" +
                    "\n" +
                    "    <groupId>org.example</groupId>\n" +
                    "    <artifactId>main</artifactId>\n" +
                    "    <version>1.0-SNAPSHOT</version>\n" +
                    "</project>"
}
