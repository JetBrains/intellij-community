// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_CONTENT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_TEST_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.NOT_IN_PROJECT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.UNDER_IGNORED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.util.ThreeState
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertFalse

@TestApplication
@RunInEdt(writeIntent = true)
class ModuleRootsInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val baseModuleDir: TempDirectoryExtension = TempDirectoryExtension()

  private lateinit var module: Module
  private lateinit var moduleDir: VirtualFile

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @BeforeEach
  internal fun setUp() {
    module = projectModel.createModule(moduleBaseDir = baseModuleDir.root.toPath())
    moduleDir = baseModuleDir.newVirtualDirectory("module")
  }

  @Test
  fun `add remove content root`() {
    val file = baseModuleDir.newVirtualFile("module/file.txt")
    fileIndex.assertScope(moduleDir, NOT_IN_PROJECT)
    fileIndex.assertScope(file, NOT_IN_PROJECT)
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(moduleDir)
    assertInModule(file)
    PsiTestUtil.removeContentEntry(module, moduleDir)
    fileIndex.assertScope(moduleDir, NOT_IN_PROJECT)
    fileIndex.assertScope(file, NOT_IN_PROJECT)
  }

  @Test
  fun `add remove excluded root`() {
    val file = baseModuleDir.newVirtualFile("module/file.txt")
    val excludedDir = baseModuleDir.newVirtualDirectory("module/excluded")
    val excludedFile = baseModuleDir.newVirtualFile("module/excluded/excluded.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)
    assertInModule(excludedDir)
    assertInModule(excludedFile)

    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertInModule(file)
    assertExcludedFromModule(excludedDir)
    assertExcludedFromModule(excludedFile)

    PsiTestUtil.removeExcludedRoot(module, excludedDir)
    assertInModule(file)
    assertInModule(excludedDir)
    assertInModule(excludedFile)
  }

  @Test
  fun `add remove source root`() {
    val file = baseModuleDir.newVirtualFile("module/src/A.java")
    val srcDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)
    assertNull(fileIndex.getSourceRootForFile(file))

    PsiTestUtil.addSourceRoot(module, srcDir)
    assertInContentSource(file)
    assertEquals(srcDir, fileIndex.getSourceRootForFile(file))
    assertNull(fileIndex.getClassRootForFile(file))

    PsiTestUtil.removeSourceRoot(module, srcDir)
    assertInModule(file)
    assertNull(fileIndex.getSourceRootForFile(file))
  }

  @Test
  fun `source root types`() {
    val srcDir = baseModuleDir.newVirtualFile("module/main/java")
    val testDir = baseModuleDir.newVirtualFile("module/test/java")
    val resourceDir = baseModuleDir.newVirtualFile("module/main/resources")
    val testResourceDir = baseModuleDir.newVirtualFile("module/test/resources")
    val contentRoot = srcDir.parent.parent
    PsiTestUtil.addContentRoot(module, contentRoot)
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addSourceRoot(module, testDir, true)
    PsiTestUtil.addSourceRoot(module, resourceDir, JavaResourceRootType.RESOURCE)
    PsiTestUtil.addSourceRoot(module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)

    val roots = listOf(srcDir, testDir, resourceDir, testResourceDir)
    roots.forEach { dir ->
      val isTest = dir.parent.name == "test"
      val isResources = dir.name == "resources"
      fileIndex.assertInModule(dir, module, contentRoot, if (isTest) IN_CONTENT or IN_SOURCE or IN_TEST_SOURCE else IN_CONTENT or IN_SOURCE)
      assertEquals(isTest, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.TESTS))
      assertEquals(!isTest, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.PRODUCTION))
      assertEquals(isResources, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.RESOURCES))
      assertEquals(!isResources, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.SOURCES))
    }

    assertNull(fileIndex.getContainingSourceRootType(contentRoot))
    assertEquals(JavaSourceRootType.SOURCE, fileIndex.getContainingSourceRootType(srcDir))
    assertEquals(JavaSourceRootType.TEST_SOURCE, fileIndex.getContainingSourceRootType(testDir))
    assertEquals(JavaResourceRootType.RESOURCE, fileIndex.getContainingSourceRootType(resourceDir))
    assertEquals(JavaResourceRootType.TEST_RESOURCE, fileIndex.getContainingSourceRootType(testResourceDir))
  }

  @Test
  fun `source root for generated sources`() {
    val srcDir = baseModuleDir.newVirtualFile("module/src")
    val genDir = baseModuleDir.newVirtualFile("module/gen")
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addSourceRoot(module, genDir, JavaSourceRootType.SOURCE, JavaSourceRootProperties("", true))
    assertFalse(fileIndex.isInGeneratedSources(srcDir))
    assertTrue(fileIndex.isInGeneratedSources(genDir))
  }

  @Test
  fun `add remove source root under excluded`() {
    val file = baseModuleDir.newVirtualFile("module/excluded/src/A.java")
    val srcDir = file.parent
    val excludedDir = srcDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertExcludedFromModule(file)

    PsiTestUtil.addSourceRoot(module, srcDir)
    assertInContentSource(file)
    assertIteratedContent(module, listOf(srcDir, file), listOf(excludedDir))

    PsiTestUtil.removeSourceRoot(module, srcDir)
    assertExcludedFromModule(file)
  }

  @Test
  fun `add remove content root under source root`() {
    val file = baseModuleDir.newVirtualFile("module/src/content/A.java")
    val contentDir = file.parent
    val srcDir = contentDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addSourceRoot(module, srcDir)
    assertInContentSource(file)

    PsiTestUtil.addContentRoot(module, contentDir)
    fileIndex.assertInModule(file, module, contentDir)

    PsiTestUtil.removeContentEntry(module, contentDir)
    assertInContentSource(file)
  }

  @Test
  fun `add remove source content root under excluded`() {
    val file = baseModuleDir.newVirtualFile("module/excluded/content-src/A.java")
    val excludedFile = baseModuleDir.newVirtualFile("module/excluded/excluded.txt")
    val contentSourceDir = file.parent
    val excludedDir = contentSourceDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertExcludedFromModule(file)

    PsiTestUtil.addContentRoot(module, contentSourceDir)
    PsiTestUtil.addSourceRoot(module, contentSourceDir)
    fileIndex.assertInModule(file, module, contentSourceDir, IN_CONTENT or IN_SOURCE)
    assertExcludedFromModule(excludedFile)
    assertIteratedContent(module, listOf(file, contentSourceDir), listOf(excludedDir, excludedFile))

    PsiTestUtil.removeSourceRoot(module, contentSourceDir)
    PsiTestUtil.removeContentEntry(module, contentSourceDir)
    assertExcludedFromModule(file)
    assertIteratedContent(module, emptyList(), listOf(file, contentSourceDir, excludedDir, excludedFile))
  }

  @Test
  fun `add remove excluded source root under excluded`() {
    val file = baseModuleDir.newVirtualFile("module/excluded/excluded-src/A.java")
    val excludedSourceDir = file.parent
    val excludedDir = excludedSourceDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertExcludedFromModule(file)

    PsiTestUtil.addSourceRoot(module, excludedSourceDir)
    assertInContentSource(file)
    assertIteratedContent(module, listOf(file, excludedSourceDir), listOf(excludedDir))

    PsiTestUtil.addExcludedRoot(module, excludedSourceDir)
    assertExcludedFromModule(file)
    assertIteratedContent(module, emptyList(), listOf(file, excludedSourceDir, excludedDir))

    PsiTestUtil.removeExcludedRoot(module, excludedSourceDir)
    assertInContentSource(file)
    assertIteratedContent(module, listOf(file, excludedSourceDir), listOf(excludedDir))
  }

  @Test
  fun `excluded content root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, moduleDir)
    assertExcludedFromModule(moduleDir)
    val file = baseModuleDir.newVirtualFile("module/file.txt")
    assertExcludedFromModule(file)
  }

  @Test
  fun `excluded source root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = baseModuleDir.newVirtualFile("module/src/file.txt")
    val srcDir = file.parent
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addExcludedRoot(module, srcDir)
    assertInModule(moduleDir)
    assertExcludedFromModule(srcDir)
    assertExcludedFromModule(file)
  }

  @Test
  fun `excluded source root under excluded`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = baseModuleDir.newVirtualFile("module/excluded/src/file.txt")
    val srcDir = file.parent
    val excludedDir = srcDir.parent
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addExcludedRoot(module, srcDir)
    assertInModule(moduleDir)
    assertExcludedFromModule(srcDir)
    assertExcludedFromModule(file)
    assertIteratedContent(module, listOf(moduleDir), listOf(file, srcDir, excludedDir))
  }

  @Test
  fun `file as content root`() {
    val file = baseModuleDir.newVirtualFile("content.txt")
    fileIndex.assertScope(file, NOT_IN_PROJECT)

    PsiTestUtil.addContentRoot(module, file)
    fileIndex.assertInModule(file, module, file)

    PsiTestUtil.removeContentEntry(module, file)
    fileIndex.assertScope(file, NOT_IN_PROJECT)
  }

  @Test
  fun `file as source root`() {
    val file = baseModuleDir.newVirtualFile("module/source.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)

    PsiTestUtil.addSourceRoot(module, file)
    assertInContentSource(file)

    PsiTestUtil.removeSourceRoot(module, file)
    assertInModule(file)
  }

  @Test
  fun `file as test source root`() {
    val file = baseModuleDir.newVirtualFile("module/source.txt")
    PsiTestUtil.addSourceRoot(module, file, true)
    fileIndex.assertInModule(file, module, file, IN_CONTENT or IN_SOURCE or IN_TEST_SOURCE)

    PsiTestUtil.removeSourceRoot(module, file)
    fileIndex.assertInModule(file, module, file)
  }

  @Test
  fun `file as excluded root`() {
    val file = baseModuleDir.newVirtualFile("module/excluded.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)

    PsiTestUtil.addExcludedRoot(module, file)
    assertExcludedFromModule(file)
    assertIteratedContent(module, listOf(moduleDir), listOf(file))

    PsiTestUtil.removeExcludedRoot(module, file)
    assertInModule(file)
    assertIteratedContent(module, listOf(moduleDir, file), emptyList())
  }

  @Test
  fun `file as excluded content root`() {
    val file = baseModuleDir.newVirtualFile("module/excluded.txt")
    PsiTestUtil.addContentRoot(module, file)
    fileIndex.assertInModule(file, module, file)

    PsiTestUtil.addExcludedRoot(module, file)
    fileIndex.assertInModule(file, module, file, EXCLUDED)
    assertIteratedContent(module, emptyList(), listOf(file))

    PsiTestUtil.removeExcludedRoot(module, file)
    fileIndex.assertInModule(file, module, file)
    assertIteratedContent(module, listOf(file), emptyList())
  }

  @Test
  fun `ignored file`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = baseModuleDir.newVirtualFile("module/CVS")
    assertTrue(FileTypeManager.getInstance().isFileIgnored(file))
    fileIndex.assertScope(file, UNDER_IGNORED)
  }

  @Test
  fun `content root under ignored dir`() {
    val file = baseModuleDir.newVirtualFile("module/.git/content/file.txt")
    val contentDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertTrue(FileTypeManager.getInstance().isFileIgnored(contentDir.parent))
    assertFalse(FileTypeManager.getInstance().isFileIgnored(file))
    fileIndex.assertScope(file, UNDER_IGNORED)

    PsiTestUtil.addContentRoot(module, contentDir)
    fileIndex.assertInModule(file, module, contentDir)
  }

  @Test
  fun `ignored dir as content root`() {
    val file = baseModuleDir.newVirtualFile("module/.git/file.txt")
    val ignoredContentDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertTrue(FileTypeManager.getInstance().isFileIgnored(ignoredContentDir))
    assertFalse(FileTypeManager.getInstance().isFileIgnored(file))
    fileIndex.assertScope(file, UNDER_IGNORED)

    PsiTestUtil.addContentRoot(module, ignoredContentDir)
    fileIndex.assertInModule(file, module, ignoredContentDir)
  }

  @Test
  fun `change ignored files list`() {
    val file = baseModuleDir.newVirtualFile("module/newDir/file.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)

    val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerEx
    val oldValue = fileTypeManager.ignoredFilesList
    try {
      runWriteAction { fileTypeManager.ignoredFilesList = "$oldValue;newDir" }
      fileIndex.assertScope(file, UNDER_IGNORED)
    }
    finally {
      runWriteAction { fileTypeManager.ignoredFilesList = oldValue }
      assertInModule(file)
    }
  }

  @Test
  fun `is in content by url for existing file`() {
    val file = baseModuleDir.newVirtualFile("module/file.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertEquals(ThreeState.YES, WorkspaceFileIndex.getInstance(projectModel.project).isUrlInContent(file.url))
  }

  @Test
  fun `is url in content for non existing file`() {
    val moduleUrl = moduleDir.url
    val rootUrl = "$moduleUrl/root"
    val excludedUrl = "$moduleUrl/root/excluded"
    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    runWriteActionAndWait {
      workspaceModel.updateProjectModel {
        val module = it.resolve(ModuleId(module.name))!!
        it.modifyModuleEntity(module) {
          this.contentRoots += ContentRootEntity(urlManager.getOrCreateFromUrl(rootUrl),
                                                 emptyList<@NlsSafe String>(),
                                                 module.entitySource) {
            excludedUrls = listOf(urlManager.getOrCreateFromUrl(excludedUrl)).map {
              ExcludeUrlEntity(it, module.entitySource)
            }
          }
        }
      }
    }
    val workspaceFileIndex = WorkspaceFileIndex.getInstance(projectModel.project)
    assertEquals(ThreeState.YES, workspaceFileIndex.isUrlInContent("$rootUrl/file.txt"))
    assertEquals(ThreeState.NO, workspaceFileIndex.isUrlInContent("$excludedUrl/file.txt"))
    assertEquals(ThreeState.NO, workspaceFileIndex.isUrlInContent("$moduleUrl/file.txt"))
  }

  private fun assertInModule(file: VirtualFile) {
    fileIndex.assertInModule(file, module, moduleDir)
  }

  private fun assertExcludedFromModule(file: VirtualFile) {
    fileIndex.assertInModule(file, module, moduleDir, EXCLUDED)
  }

  private fun assertInContentSource(file: VirtualFile) {
    fileIndex.assertInModule(file, module, moduleDir, IN_CONTENT or IN_SOURCE)
  }
}