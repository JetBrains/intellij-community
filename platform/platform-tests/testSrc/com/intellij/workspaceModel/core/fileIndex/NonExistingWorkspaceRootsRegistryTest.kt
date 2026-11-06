// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generate
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestApplication
class NonExistingWorkspaceRootsRegistryTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  private val fileIndex get() = WorkspaceFileIndex.getInstance(projectModel.project)
  private val urlManager
    get() = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager() as VirtualFileUrlManagerImpl

  @Test
  fun `unrelated VFS events do not create URL nodes`(): Unit = timeoutRunBlocking {
    val parent = projectModel.baseProjectDir.newVirtualDirectory("parent")
    val destination = projectModel.baseProjectDir.newVirtualDirectory("destination")
    val file = projectModel.baseProjectDir.newVirtualFile("parent/file.txt")

    readAction {
      fileIndex.isInWorkspace(parent)
      val registeredUrls = urlManager.getCachedVirtualFileUrls().toSet()
      val events = listOf(
        VFileCreateEvent(null, parent, "unrelated", true, null, null, null),
        VFileCreateEvent(null, parent, "unrelated.txt", false, null, null, null),
        VFileCreateEvent(null, parent, "unrelated.jar", false, null, null, null),
        VFileCopyEvent(null, file, destination, "copy.txt"),
        VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, file.name, "renamed.txt"),
        VFileMoveEvent(null, file, destination),
        VFileDeleteEvent(null, file),
      )
      assertNull((fileIndex as WorkspaceFileIndexImpl).indexData.analyzeVfsChanges(events))
      assertEquals(registeredUrls, urlManager.getCachedVirtualFileUrls().toSet())
      val urls = listOf(
        "${parent.url}/unrelated",
        "${parent.url}/unrelated.txt",
        "${parent.url}/unrelated.jar",
        "${destination.url}/copy.txt",
        "${parent.url}/renamed.txt",
        "${destination.url}/${file.name}",
        file.url,
        "${parent.url.replaceFirst("file://", "jar://")}/unrelated",
        "${parent.url.replaceFirst("file://", "jar://")}/unrelated.jar!/",
      )
      for (url in urls) {
        assertNull(urlManager.findNode(url), "The VFS event created a URL node for $url")
      }
    }
  }

  @Test
  fun `creating an ancestor activates a missing content root`(): Unit = timeoutRunBlocking {
    val module = projectModel.createModule()
    val parent = projectModel.baseProjectDir.newVirtualDirectory("parent")
    val ancestorUrl = "${parent.url}/missing"
    val rootUrl = "$ancestorUrl/content"
    ModuleRootModificationUtil.updateModel(module) { it.addContentEntry(rootUrl) }
    readAction { fileIndex.isInWorkspace(parent) }
    assertNotNull(urlManager.findNode(ancestorUrl))
    assertNull(urlManager.get(ancestorUrl))
    readAction {
      val event = VFileCreateEvent(null, parent, "missing", true, null, null, null)
      assertNotNull((fileIndex as WorkspaceFileIndexImpl).indexData.analyzeVfsChanges(listOf(event)))
    }

    directoryContent {
      dir("missing") {
        dir("content") { file("file.txt") }
      }
    }.generate(parent)

    readAction {
      val contentRoot = assertNotNull(parent.findFileByRelativePath("missing/content"))
      assertTrue(fileIndex.isInContent(contentRoot))
      assertTrue(fileIndex.isInContent(assertNotNull(contentRoot.findChild("file.txt"))))
    }
    assertNull(urlManager.get(ancestorUrl), "The ancestor must remain unregistered")
  }

  @Test
  fun `new JARs under an unknown descendant update a recursive library`(): Unit = timeoutRunBlocking {
    val module = projectModel.createModule()
    val parent = projectModel.baseProjectDir.newVirtualDirectory("library")
    val library = projectModel.addProjectLevelLibrary("library") {
      it.addJarDirectory(parent, true, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(module, library)
    readAction { fileIndex.isInWorkspace(parent) }
    assertNull(urlManager.findNode("${parent.url}/nested"))
    readAction {
      val event = VFileCreateEvent(null, parent, "nested", true, null, null, null)
      assertNotNull((fileIndex as WorkspaceFileIndexImpl).indexData.analyzeVfsChanges(listOf(event)))
    }

    directoryContent {
      dir("nested") {
        dir("deeper") {
          zip("library.jar") { file("file.txt") }
        }
      }
    }.generate(parent)

    readAction {
      val jar = assertNotNull(parent.findFileByRelativePath("nested/deeper/library.jar"))
      val jarRoot = assertNotNull(JarFileSystem.getInstance().getJarRootForLocalFile(jar))
      assertTrue(ProjectFileIndex.getInstance(projectModel.project).isInLibraryClasses(jarRoot))
      assertTrue(fileIndex.isInWorkspace(assertNotNull(jarRoot.findChild("file.txt"))))
    }
  }
}
