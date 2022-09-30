// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteAction
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
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
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
    fileIndex.assertScope(moduleDir, NOT_IN_PROJECT)
    fileIndex.assertScope(file, NOT_IN_PROJECT)
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertScope(moduleDir, IN_CONTENT, module)
    fileIndex.assertScope(file, IN_CONTENT, module)
    PsiTestUtil.removeContentEntry(module, moduleDir)
    fileIndex.assertScope(moduleDir, NOT_IN_PROJECT)
    fileIndex.assertScope(file, NOT_IN_PROJECT)
  }

  @Test
  fun `add remove excluded root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("module/excluded")
    val excludedFile = projectModel.baseProjectDir.newVirtualFile("module/excluded/excluded.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertScope(file, IN_CONTENT, module)
    fileIndex.assertScope(excludedDir, IN_CONTENT, module)
    fileIndex.assertScope(excludedFile, IN_CONTENT, module)

    PsiTestUtil.addExcludedRoot(module, excludedDir)
    fileIndex.assertScope(file, IN_CONTENT, module)
    fileIndex.assertScope(excludedDir, EXCLUDED, module)
    fileIndex.assertScope(excludedFile, EXCLUDED, module)

    PsiTestUtil.removeExcludedRoot(module, excludedDir)
    fileIndex.assertScope(file, IN_CONTENT, module)
    fileIndex.assertScope(excludedDir, IN_CONTENT, module)
    fileIndex.assertScope(excludedFile, IN_CONTENT, module)
  }

  @Test
  fun `add remove source root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/src/A.java")
    val srcDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertScope(file, IN_CONTENT, module)

    PsiTestUtil.addSourceRoot(module, srcDir)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)

    PsiTestUtil.removeSourceRoot(module, srcDir)
    fileIndex.assertScope(file, IN_CONTENT, module)
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
      fileIndex.assertScope(dir, if (isTest) IN_CONTENT or IN_SOURCE or IN_TEST_SOURCE else IN_CONTENT or IN_SOURCE, module)
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
    fileIndex.assertScope(file, EXCLUDED, module)

    PsiTestUtil.addSourceRoot(module, srcDir)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(srcDir, file), listOf(excludedDir))

    PsiTestUtil.removeSourceRoot(module, srcDir)
    fileIndex.assertScope(file, EXCLUDED, module)
  }

  @Test
  fun `add remove content root under source root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/src/content/A.java")
    val contentDir = file.parent
    val srcDir = contentDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addSourceRoot(module, srcDir)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)

    PsiTestUtil.addContentRoot(module, contentDir)
    fileIndex.assertScope(file, IN_CONTENT, module)

    PsiTestUtil.removeContentEntry(module, contentDir)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)
  }

  @Test
  fun `add remove source content root under excluded`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded/content-src/A.java")
    val excludedFile = projectModel.baseProjectDir.newVirtualFile("module/excluded/excluded.txt")
    val contentSourceDir = file.parent
    val excludedDir = contentSourceDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    fileIndex.assertScope(file, EXCLUDED, module)

    PsiTestUtil.addContentRoot(module, contentSourceDir)
    PsiTestUtil.addSourceRoot(module, contentSourceDir)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)
    fileIndex.assertScope(excludedFile, EXCLUDED, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file, contentSourceDir), listOf(excludedDir, excludedFile))

    PsiTestUtil.removeSourceRoot(module, contentSourceDir)
    PsiTestUtil.removeContentEntry(module, contentSourceDir)
    fileIndex.assertScope(file, EXCLUDED, module)
    DirectoryIndexTestCase.assertIteratedContent(module, emptyList(), listOf(file, contentSourceDir, excludedDir, excludedFile))
  }

  @Test
  fun `add remove excluded source root under excluded`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded/excluded-src/A.java")
    val excludedSourceDir = file.parent
    val excludedDir = excludedSourceDir.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    fileIndex.assertScope(file, EXCLUDED, module)

    PsiTestUtil.addSourceRoot(module, excludedSourceDir)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file, excludedSourceDir), listOf(excludedDir))

    PsiTestUtil.addExcludedRoot(module, excludedSourceDir)
    fileIndex.assertScope(file, EXCLUDED, module)
    DirectoryIndexTestCase.assertIteratedContent(module, emptyList(), listOf(file, excludedSourceDir, excludedDir))

    PsiTestUtil.removeExcludedRoot(module, excludedSourceDir)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file, excludedSourceDir), listOf(excludedDir))
  }

  @Test
  fun `excluded content root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addExcludedRoot(module, moduleDir)
    fileIndex.assertScope(moduleDir, EXCLUDED, module)
    val file = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    fileIndex.assertScope(file, EXCLUDED, module)
  }

  @Test
  fun `excluded source root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = projectModel.baseProjectDir.newVirtualFile("module/src/file.txt")
    val srcDir = file.parent
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addExcludedRoot(module, srcDir)
    fileIndex.assertScope(moduleDir, IN_CONTENT, module)
    fileIndex.assertScope(srcDir, EXCLUDED, module)
    fileIndex.assertScope(file, EXCLUDED, module)
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
    fileIndex.assertScope(moduleDir, IN_CONTENT, module)
    fileIndex.assertScope(srcDir, EXCLUDED, module)
    fileIndex.assertScope(file, EXCLUDED, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(moduleDir), listOf(file, srcDir, excludedDir))
  }

  @Test
  fun `file as content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("content.txt")
    fileIndex.assertScope(file, NOT_IN_PROJECT)

    PsiTestUtil.addContentRoot(module, file)
    fileIndex.assertScope(file, IN_CONTENT, module)

    PsiTestUtil.removeContentEntry(module, file)
    fileIndex.assertScope(file, NOT_IN_PROJECT)
  }

  @Test
  fun `file as source root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/source.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertScope(file, IN_CONTENT, module)

    PsiTestUtil.addSourceRoot(module, file)
    fileIndex.assertScope(file, IN_CONTENT or IN_SOURCE, module)

    PsiTestUtil.removeSourceRoot(module, file)
    fileIndex.assertScope(file, IN_CONTENT, module)
  }

  @Test
  fun `file as excluded root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertScope(file, IN_CONTENT, module)

    PsiTestUtil.addExcludedRoot(module, file)
    fileIndex.assertScope(file, EXCLUDED, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(moduleDir), listOf(file))

    PsiTestUtil.removeExcludedRoot(module, file)
    fileIndex.assertScope(file, IN_CONTENT, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(moduleDir, file), emptyList())
  }

  @Test
  fun `file as excluded content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/excluded.txt")
    PsiTestUtil.addContentRoot(module, file)
    fileIndex.assertScope(file, IN_CONTENT, module)

    PsiTestUtil.addExcludedRoot(module, file)
    fileIndex.assertScope(file, EXCLUDED, module)
    DirectoryIndexTestCase.assertIteratedContent(module, emptyList(), listOf(file))

    PsiTestUtil.removeExcludedRoot(module, file)
    fileIndex.assertScope(file, IN_CONTENT, module)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(file), emptyList())
  }

  @Test
  fun `ignored file`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val file = projectModel.baseProjectDir.newVirtualFile("module/CVS")
    assertTrue(FileTypeManager.getInstance().isFileIgnored(file))
    fileIndex.assertScope(file, UNDER_IGNORED)
  }

  @Test
  fun `content root under ignored dir`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/.git/content/file.txt")
    val contentDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertTrue(FileTypeManager.getInstance().isFileIgnored(contentDir.parent))
    assertFalse(FileTypeManager.getInstance().isFileIgnored(file))
    fileIndex.assertScope(file, UNDER_IGNORED)

    PsiTestUtil.addContentRoot(module, contentDir)
    fileIndex.assertScope(file, IN_CONTENT, module)
  }

  @Test
  fun `ignored dir as content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/.git/file.txt")
    val ignoredContentDir = file.parent
    PsiTestUtil.addContentRoot(module, moduleDir)
    assertTrue(FileTypeManager.getInstance().isFileIgnored(ignoredContentDir))
    assertFalse(FileTypeManager.getInstance().isFileIgnored(file))
    fileIndex.assertScope(file, UNDER_IGNORED)

    PsiTestUtil.addContentRoot(module, ignoredContentDir)
    fileIndex.assertScope(file, IN_CONTENT, module)
  }

  @Test
  fun `change ignored files list`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/newDir/file.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertScope(file, IN_CONTENT, module)

    val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerEx
    val oldValue = fileTypeManager.ignoredFilesList
    try {
      runWriteAction { fileTypeManager.ignoredFilesList = "$oldValue;newDir" }
      fileIndex.assertScope(file, UNDER_IGNORED)
    }
    finally {
      runWriteAction { fileTypeManager.ignoredFilesList = oldValue }
      fileIndex.assertScope(file, IN_CONTENT, module)
    }
  }

}