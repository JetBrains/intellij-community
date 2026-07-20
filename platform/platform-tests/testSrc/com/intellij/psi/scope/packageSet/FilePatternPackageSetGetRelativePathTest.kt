// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.scope.packageSet

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class FilePatternPackageSetGetRelativePathTest() {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val baseProjectDir: TempDirectory get() = projectModel.baseProjectDir

  @RegisterExtension
  private val tempDir: TempDirectoryExtension = TempDirectoryExtension()

  private val project: Project get() = projectModel.project

  private val projectFileIndex: ProjectFileIndex
    get() = ProjectFileIndex.getInstance(project)

  @Test
  fun `returns path relative to a module content root`(): Unit = runBlocking {
    val contentRoot = createModuleContentRoot("content")
    val file = baseProjectDir.newVirtualFile("content/src/file.txt")

    assertEquals(contentRoot, contentRootForFile(file))
    assertEquals("src/file.txt", getRelativePath(file))
  }

  @Test
  fun `returns null when a custom content root is not an ancestor of the file`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val fileSetRoot = baseProjectDir.newVirtualDirectory("file-set")
    val file = baseProjectDir.newVirtualFile("file-set/src/file.txt")
    val contentRoot = baseProjectDir.newVirtualDirectory("content-root")
    registerFileSet(fileSetRoot, ModuleContentFileSetData(module, contentRoot))

    assertEquals(contentRoot, contentRootForFile(file))
    assertNull(getRelativePath(file))
  }

  @Test
  fun `returns null when the content root is on a different file system and the file has a parent`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val fileSetRoot = baseProjectDir.newVirtualDirectory("file-set")
    val file = baseProjectDir.newVirtualFile("file-set/src/file.txt")
    val contentRoot = tempDir.newEmptyVirtualJarFile("content.jar")
    registerFileSet(fileSetRoot, ModuleContentFileSetData(module, contentRoot))

    assertEquals(contentRoot, contentRootForFile(file))
    assertNull(getRelativePath(file))
  }

  @Test
  fun `uses the parent when a file on a different file system has a parent`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val contentRoot = baseProjectDir.newVirtualDirectory("content-root")
    val parent = baseProjectDir.newVirtualDirectory("content-root/src")
    val file = ParentedLightVirtualFile("file.txt", parent)
    registerFileSet(file, ModuleContentFileSetData(module, contentRoot))

    assertEquals(contentRoot, contentRootForFile(file))
    assertEquals("src/file.txt", getRelativePath(file))
  }

  @Test
  fun `returns null when a file on a different file system has no parent`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val file = tempDir.newEmptyVirtualJarFile("file.jar")
    val contentRoot = baseProjectDir.newVirtualDirectory("content-root")
    registerFileSet(file, ModuleContentFileSetData(module, contentRoot))

    assertEquals(contentRoot, contentRootForFile(file))
    assertNull(getRelativePath(file))
  }

  @Test
  fun `returns the file path for a module file when no project base directory is provided`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val fileSetRoot = baseProjectDir.newVirtualDirectory("module-root")
    val file = baseProjectDir.newVirtualFile("module-root/src/file.txt")
    registerFileSet(fileSetRoot, ModuleFileSetData(module))


    assertNull(contentRootForFile(file))
    assertEquals(module, moduleForFile(file))
    assertEquals(file.path, getRelativePath(file, projectBaseDir = null))
  }

  @Test
  fun `returns the project-relative path for a module file under the project base directory`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val fileSetRoot = baseProjectDir.newVirtualDirectory("module-root")
    val file = baseProjectDir.newVirtualFile("module-root/src/file.kt")
    registerFileSet(fileSetRoot, ModuleFileSetData(module))

    assertEquals("module-root/src/file.kt", getRelativePath(file))
  }

  @Test
  fun `returns the path without the first component for a module file when the full name is not requested`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val fileSetRoot = baseProjectDir.newVirtualDirectory("module-root")
    val file = baseProjectDir.newVirtualFile("module-root/src/file.kt")
    registerFileSet(fileSetRoot, ModuleFileSetData(module))

    assertEquals("src/file.kt", getRelativePath(file, useFQName = false))
  }

  @Test
  fun `returns the file path for a module file outside the project base directory`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val fileSetRoot = tempDir.newVirtualDirectory("module-root")
    val file = tempDir.newVirtualFile("module-root/src/file.kt")
    registerFileSet(fileSetRoot, ModuleFileSetData(module))

    assertEquals(file.path, getRelativePath(file))
  }

  @Test
  fun `returns path relative to a workspace content file set root`() = runBlocking {
    val root = tempDir.newVirtualDirectory("workspace")
    val file = tempDir.newVirtualFile("workspace/src/file.txt")
    registerProjectRoot(project, root.toNioPath())

    assertEquals("src/file.txt", getRelativePath(file, projectBaseDir = null))
  }

  @Test
  fun `returns path relative to a library root`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val libraryRoot = tempDir.newVirtualDirectory("library")
    val file = tempDir.newVirtualFile("library/org/example/Thing.class")
    val library = projectModel.addModuleLevelLibrary(module, "library") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(module, library)

    assertEquals("org/example/Thing.class", getRelativePath(file, projectBaseDir = null))
  }

  @Test
  fun `returns an empty path when the file is not part of a module workspace content or library`(): Unit = runBlocking {
    val file = tempDir.newVirtualFile("outside/file.txt")
    assertEquals("", getRelativePath(file, projectBaseDir = null))
  }

  private fun createModuleContentRoot(name: String): VirtualFile {
    val module = projectModel.createModule(name)
    val contentRoot = baseProjectDir.newVirtualDirectory(name)
    PsiTestUtil.addContentRoot(module, contentRoot)
    return contentRoot
  }

  private suspend fun contentRootForFile(file: VirtualFile): VirtualFile? = readAction { projectFileIndex.getContentRootForFile(file) }

  private suspend fun moduleForFile(file: VirtualFile): Module? = readAction { projectFileIndex.getModuleForFile(file) }

  private suspend fun getRelativePath(
    file: VirtualFile,
    useFQName: Boolean = true,
    projectBaseDir: VirtualFile? = project.baseDir,
  ): String? = readAction { FilePatternPackageSet.getRelativePath(file, projectFileIndex, useFQName, projectBaseDir) }

  private suspend fun registerFileSet(root: VirtualFile, data: WorkspaceFileSetData) {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(TestFileSetContributor(data), projectModel.disposableRule.disposable)
    val workspaceModel = WorkspaceModel.getInstance(project)
    val rootUrl = root.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
    workspaceModel.update { storage ->
      storage.addEntity(IndexingTestEntity(listOf(rootUrl), emptyList(), NonPersistentEntitySource))
    }
  }

  private class TestFileSetContributor(private val data: WorkspaceFileSetData) : WorkspaceFileIndexContributor<IndexingTestEntity> {
    override val entityClass: Class<IndexingTestEntity>
      get() = IndexingTestEntity::class.java

    override fun registerFileSets(entity: IndexingTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      entity.roots.forEach { root ->
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, data)
      }
    }
  }

  private class ModuleFileSetData(override val module: Module) : ModuleRelatedRootData

  private class ModuleContentFileSetData(
    override val module: Module,
    override val customContentRoot: VirtualFile,
  ) : ModuleContentOrSourceRootData

  private class ParentedLightVirtualFile(name: String, private val parent: VirtualFile) : LightVirtualFile(name) {
    override fun getParent(): VirtualFile = parent
  }
}
