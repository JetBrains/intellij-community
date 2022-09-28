// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertFalse
import kotlin.test.assertNull

@TestApplication
@RunInEdt
class ModuleRootsInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private lateinit var module: Module
  private lateinit var moduleDir: VirtualFile

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @BeforeEach
  internal fun setUp() {
    module = projectModel.createModule()
    moduleDir = projectModel.baseProjectDir.newVirtualDirectory("module")
  }

  @Test
  fun `add remove content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    assertNotInModule(moduleDir)
    assertNotInModule(file)
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(moduleDir)
    assertInModule(file)
    PsiTestUtil.removeContentEntry(module, moduleDir)
    assertNotInModule(moduleDir)
    assertNotInModule(file)
  }

  @Test
  fun `add remove excluded root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("module/excluded")
    val excludedFile = projectModel.baseProjectDir.newVirtualFile("module/excluded/excluded.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)
    assertInModule(excludedDir)
    assertInModule(excludedFile)
    
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertInModule(file)
    assertNotInModule(excludedDir)
    assertNotInModule(excludedFile)

    PsiTestUtil.removeExcludedRoot(module, excludedDir)
    assertInModule(file)
    assertInModule(excludedDir)
    assertInModule(excludedFile)
  }

  @Test
  fun `add remove source root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/src/A.java")
    val srcDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertFalse(fileIndex.isInSource(file))
    
    PsiTestUtil.addSourceRoot(module, srcDir)
    assertInModule(file)
    assertTrue(fileIndex.isInSource(file))

    PsiTestUtil.removeSourceRoot(module, srcDir)
    assertInModule(file)
    assertFalse(fileIndex.isInSource(file))
  }

  @Test
  fun `source root types`() {
    val srcDir = projectModel.baseProjectDir.newVirtualFile("module/main/java")
    val testDir = projectModel.baseProjectDir.newVirtualFile("module/test/java")
    val resourceDir = projectModel.baseProjectDir.newVirtualFile("module/main/resources")
    val testResourceDir = projectModel.baseProjectDir.newVirtualFile("module/test/resources")
    PsiTestUtil.addContentRoot(module, srcDir.parent.parent)
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addSourceRoot(module, testDir, true)
    PsiTestUtil.addSourceRoot(module, resourceDir, JavaResourceRootType.RESOURCE)
    PsiTestUtil.addSourceRoot(module, testResourceDir, JavaResourceRootType.TEST_RESOURCE)
    
    val roots = listOf(srcDir, testDir, resourceDir, testResourceDir)
    roots.forEach { dir ->
      val isTest = dir.parent.name == "test"
      val isResources = dir.name == "resources"
      assertTrue(fileIndex.isInSource(dir))
      assertEquals(isTest, fileIndex.isInTestSourceContent(dir))
      assertEquals(isTest, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.TESTS))
      assertEquals(!isTest, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.PRODUCTION))
      assertEquals(isResources, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.RESOURCES))
      assertEquals(!isResources, fileIndex.isUnderSourceRootOfType(dir, JavaModuleSourceRootTypes.SOURCES))
    }
  }

  @Test
  fun `add remove source root under excluded`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded/src/A.java")
    val srcDir = file.parent
    val excludedDir = srcDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertNotInModule(file)
    
    PsiTestUtil.addSourceRoot(module, srcDir)
    assertInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(srcDir, file), listOf(excludedDir))

    PsiTestUtil.removeSourceRoot(module, srcDir)
    assertNotInModule(file)
  }
  
  @Test
  fun `add remove content root under source root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/src/content/A.java")
    val contentDir = file.parent
    val srcDir = contentDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addSourceRoot(module, srcDir)
    assertInModule(file)
    assertTrue(fileIndex.isInSource(file))
    
    PsiTestUtil.addContentRoot(module, contentDir)
    assertInModule(file)
    assertFalse(fileIndex.isInSource(file))

    PsiTestUtil.removeContentEntry(module, contentDir)
    assertTrue(fileIndex.isInSource(file))
  }
  
  @Test
  fun `add remove source content root under excluded`() { 
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded/content-src/A.java")
    val excludedFile = projectModel.baseProjectDir.newVirtualFile("module/excluded/excluded.txt")
    val contentSourceDir = file.parent
    val excludedDir = contentSourceDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertNotInModule(file)

    PsiTestUtil.addContentRoot(module, contentSourceDir)
    PsiTestUtil.addSourceRoot(module, contentSourceDir)
    assertInModule(file)
    assertNotInModule(excludedFile)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file, contentSourceDir), listOf(excludedDir, excludedFile))

    PsiTestUtil.removeSourceRoot(module, contentSourceDir)
    PsiTestUtil.removeContentEntry(module, contentSourceDir)
    assertNotInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, emptyList(), listOf(file, contentSourceDir, excludedDir, excludedFile))
  }
  @Test
  fun `add remove excluded source root under excluded`() { 
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded/excluded-src/A.java")
    val excludedSourceDir = file.parent
    val excludedDir = excludedSourceDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertNotInModule(file)

    PsiTestUtil.addSourceRoot(module, excludedSourceDir)
    assertInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file, excludedSourceDir), listOf(excludedDir))
    
    PsiTestUtil.addExcludedRoot(module, excludedSourceDir)
    assertNotInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, emptyList(), listOf(file, excludedSourceDir, excludedDir))

    PsiTestUtil.removeExcludedRoot(module, excludedSourceDir)
    assertInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file, excludedSourceDir), listOf(excludedDir))
  }

  @Test
  fun `excluded content root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, moduleDir)
    assertNotInModule(moduleDir)
    val file = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    assertNotInModule(file)
  }

  @Test
  fun `excluded source root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = projectModel.baseProjectDir.newVirtualFile("module/src/file.txt")
    val srcDir = file.parent
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addExcludedRoot(module, srcDir)
    assertInModule(moduleDir)
    assertNotInModule(srcDir)
    assertNotInModule(file)
  }

  @Test
  fun `excluded source root under excluded`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded/src/file.txt")
    val srcDir = file.parent
    val excludedDir = srcDir.parent
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addExcludedRoot(module, srcDir)
    assertInModule(moduleDir)
    assertNotInModule(srcDir)
    assertNotInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(moduleDir), listOf(file, srcDir, excludedDir))
  }

  @Test
  fun `file as content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("content.txt")
    assertNotInModule(file)

    PsiTestUtil.addContentRoot(module, file)
    assertInModule(file)
    
    PsiTestUtil.removeContentEntry(module, file)
    assertNotInModule(file)
  }
  
  @Test
  fun `file as source root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/source.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)
    assertFalse(fileIndex.isInSource(file))
    
    PsiTestUtil.addSourceRoot(module, file)
    assertTrue(fileIndex.isInSource(file))
    
    PsiTestUtil.removeSourceRoot(module, file)
    assertFalse(fileIndex.isInSource(file))
  }

  @Test
  fun `file as excluded root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertInModule(file)
    
    PsiTestUtil.addExcludedRoot(module, file)
    assertNotInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(moduleDir), listOf(file))
    
    PsiTestUtil.removeExcludedRoot(module, file)
    assertInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(moduleDir, file), emptyList())
  }
  
  @Test
  fun `file as excluded content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded.txt")
    PsiTestUtil.addContentRoot(module, file)
    assertInModule(file)
    
    PsiTestUtil.addExcludedRoot(module, file)
    assertNotInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, emptyList(), listOf(file))
    
    PsiTestUtil.removeExcludedRoot(module, file)
    assertInModule(file)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file), emptyList())
  }

  @Test
  fun `ignored file`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = projectModel.baseProjectDir.newVirtualFile("module/CVS")
    assertTrue(FileTypeManager.getInstance().isFileIgnored(file))
    assertIgnored(file)
  }

  @Test
  fun `content root under ignored dir`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/.git/content/file.txt")
    val contentDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertTrue(FileTypeManager.getInstance().isFileIgnored(contentDir.parent))
    assertFalse(FileTypeManager.getInstance().isFileIgnored(file))
    assertIgnored(file)

    PsiTestUtil.addContentRoot(module, contentDir)
    assertInModule(file)
    assertFalse(fileIndex.isUnderIgnored(file))
  }

  @Test
  fun `ignored dir as content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/.git/file.txt")
    val ignoredContentDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertTrue(FileTypeManager.getInstance().isFileIgnored(ignoredContentDir))
    assertFalse(FileTypeManager.getInstance().isFileIgnored(file))
    assertIgnored(file)

    PsiTestUtil.addContentRoot(module, ignoredContentDir)
    assertInModule(file)
    assertFalse(fileIndex.isUnderIgnored(file))
  }
  
  private fun assertInModule(file: VirtualFile) {
    assertTrue(fileIndex.isInProject(file))
    assertTrue(fileIndex.isInContent(file))
    assertEquals(module, fileIndex.getModuleForFile(file))
    assertFalse(fileIndex.isInLibrary(file))
    assertFalse(fileIndex.isExcluded(file))
  }

  private fun assertNotInModule(file: VirtualFile) {
    assertFalse(fileIndex.isInProject(file))
    assertFalse(fileIndex.isInContent(file))
    assertNull(fileIndex.getModuleForFile(file))
    assertFalse(fileIndex.isInLibrary(file))
  }

  private fun assertIgnored(ignoredFile: VirtualFile) {
    assertTrue(fileIndex.isUnderIgnored(ignoredFile))
    assertTrue(fileIndex.isExcluded(ignoredFile))
    assertTrue(fileIndex.isUnderIgnored(ignoredFile))
    assertNull(fileIndex.getContentRootForFile(ignoredFile, false))
    assertNull(fileIndex.getModuleForFile(ignoredFile, false))
  }
}