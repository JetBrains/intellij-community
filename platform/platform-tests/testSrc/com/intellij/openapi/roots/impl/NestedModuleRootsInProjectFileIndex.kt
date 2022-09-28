// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
class NestedModuleRootsInProjectFileIndex {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()
  
  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @Test
  fun `add remove nested content root`() {
    val outerModule = projectModel.createModule("outer")
    val innerModule = projectModel.createModule("inner")
    val innerFile = projectModel.baseProjectDir.newVirtualDirectory("outer/inner/inner.txt")
    val outerFile = projectModel.baseProjectDir.newVirtualDirectory("outer/outer.txt")
    PsiTestUtil.addContentRoot(outerModule, outerFile.parent)
    assertEquals(outerModule, fileIndex.getModuleForFile(outerFile))
    assertEquals(outerModule, fileIndex.getModuleForFile(innerFile))
    
    PsiTestUtil.addContentRoot(innerModule, innerFile.parent)
    assertEquals(outerModule, fileIndex.getModuleForFile(outerFile))
    assertEquals(innerModule, fileIndex.getModuleForFile(innerFile))
    
    PsiTestUtil.removeContentEntry(innerModule, innerFile.parent)
    assertEquals(outerModule, fileIndex.getModuleForFile(innerFile))
  }

  @Test
  fun `add remove explicitly exclusion for inner module root`() {
    val outerModule = projectModel.createModule("outer")
    val innerModule = projectModel.createModule("inner")
    val innerFile = projectModel.baseProjectDir.newVirtualDirectory("outer/inner/inner.txt")
    val outerDir = innerFile.parent.parent
    PsiTestUtil.addContentRoot(outerModule, outerDir)
    PsiTestUtil.addExcludedRoot(outerModule, innerFile.parent)
    assertFalse(fileIndex.isInProject(innerFile))
    assertEquals(outerModule, fileIndex.getModuleForFile(innerFile, false))

    PsiTestUtil.addContentRoot(innerModule, innerFile.parent)
    assertTrue(fileIndex.isInProject(innerFile))
    assertEquals(innerModule, fileIndex.getModuleForFile(innerFile))

    PsiTestUtil.removeContentEntry(innerModule, innerFile.parent)
    assertFalse(fileIndex.isInProject(innerFile))
    assertEquals(outerModule, fileIndex.getModuleForFile(innerFile, false))
  }

  @Test
  fun `iteration of content of outer module should skip files from inner module`() {
    val outerModule = projectModel.createModule("outer")
    val innerModule = projectModel.createModule("inner")
    val innerFile = projectModel.baseProjectDir.newVirtualDirectory("outer/inner/inner.txt")
    val outerFile = projectModel.baseProjectDir.newVirtualDirectory("outer/outer.txt")
    PsiTestUtil.addContentRoot(outerModule, outerFile.parent)
    PsiTestUtil.addContentRoot(innerModule, innerFile.parent)
    val moduleFileIndex = ModuleRootManager.getInstance(outerModule).fileIndex
    DirectoryIndexTestCase.assertIteratedContent(moduleFileIndex, outerFile.parent, 
                                                 listOf(outerFile, outerFile.parent), listOf(innerFile, innerFile.parent))
  }

  /**
   * We don't allow such configuration in IDE, but ProjectFileIndex should not fail 
   */
  @Test
  fun `same content root and source root in two modules`() {
    val file = projectModel.baseProjectDir.newVirtualFile("content/src/a.txt")
    val sourceDir = file.parent
    val contentDir = sourceDir.parent
    val module1 = projectModel.createModule("module1")
    val module2 = projectModel.createModule("module2")
    PsiTestUtil.addContentRoot(module1, contentDir)
    PsiTestUtil.addContentRoot(module2, contentDir)
    PsiTestUtil.addSourceRoot(module1, sourceDir)
    PsiTestUtil.addSourceRoot(module2, sourceDir)
    
    assertTrue(fileIndex.isInSourceContent(file))
    assertNotNull(fileIndex.getModuleForFile(file))
    assertFalse(fileIndex.isInLibrary(file))
  }
}