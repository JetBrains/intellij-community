// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_CONTENT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_TEST_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase.createChildData
import com.intellij.testFramework.HeavyPlatformTestCase.createChildDirectory
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.*

@TestApplication
@RunInEdt
class ContentRootWithExcludedPatternsInProjectFileIndexTest {
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
    PsiTestUtil.addContentRoot(module, moduleDir)
  }

  @Test
  fun `exclude file by extension`() {
    /*
      root/
        dir/
          a.txt
          A.java
        src/     (module source root)
          a.txt
          A.java
        testSrc/ (module test source root)
          a.txt
          A.java
        a.txt
        A.java

      All *.txt files are excluded by pattern.
     */
    addExcludePattern("*.txt")
    val dir = createChildDirectory(moduleDir, "dir")
    val src = createChildDirectory(moduleDir, "src")
    PsiTestUtil.addSourceRoot(module, src)
    val testSrc = createChildDirectory(moduleDir, "testSrc")
    PsiTestUtil.addSourceRoot(module, testSrc, true)
    val txt1 = createChildData(moduleDir, "a.txt")
    val txt2 = createChildData(dir, "a.txt")
    val txt3 = createChildData(src, "a.txt")
    val txt4 = createChildData(testSrc, "a.txt")
    val java1 = createChildData(moduleDir, "A.java")
    val java2 = createChildData(dir, "A.java")
    val java3 = createChildData(src, "A.java")
    val java4 = createChildData(testSrc, "A.java")
    assertExcluded(txt1)
    assertExcluded(txt2)
    assertExcluded(txt3)
    assertExcluded(txt4)
    assertNotExcluded(java1)
    assertNotExcluded(java2)
    fileIndex.assertInModule(java3, module, moduleDir, IN_CONTENT or IN_SOURCE)
    fileIndex.assertInModule(java4, module, moduleDir, IN_CONTENT or IN_SOURCE or IN_TEST_SOURCE)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(java1, java2), listOf(txt1, txt2))
  }

  @Test
  fun `exclude directory by name`() {
    /*
      root/
        dir/
          a.txt
          exc/      <- excluded
            a.txt   <- excluded
        exc/        <- excluded
          a.txt     <- excluded
          dir2/     <- excluded
            a.txt   <- excluded
     */
    addExcludePattern("exc")
    val dir = createChildDirectory(moduleDir, "dir")
    val exc = createChildDirectory(moduleDir, "exc")
    val dirUnderExc = createChildDirectory(exc, "dir2")
    val excUnderDir = createChildDirectory(dir, "exc")
    val underExc = createChildData(exc, "a.txt")
    val underDir = createChildData(dir, "a.txt")
    val underExcUnderDir = createChildData(excUnderDir, "a.txt")
    val underDirUnderExc = createChildData(dirUnderExc, "a.txt")
    assertExcluded(exc)
    assertExcluded(underExc)
    assertExcluded(dirUnderExc)
    assertExcluded(underDirUnderExc)
    assertExcluded(underExcUnderDir)
    assertNotExcluded(dir)
    assertNotExcluded(underDir)
    DirectoryIndexTestCase.assertIteratedContent(module, listOf(underDir), Arrays.asList(underExc, underDirUnderExc, underExcUnderDir))
  }

  @Test
  fun `add remove pattern for file under source root`() {
    val srcRoot = createChildDirectory(moduleDir, "src")
    val txt = createChildData(srcRoot, "a.txt")
    val java = createChildData(srcRoot, "A.java")
    addExcludePattern("*.txt")
    PsiTestUtil.addSourceRoot(module, srcRoot)
    assertExcluded(txt)
    fileIndex.assertInModule(java, module, moduleDir, IN_CONTENT or IN_SOURCE)
    addExcludePattern("*.java")
    assertExcluded(txt)
    assertExcluded(java)
    removeExcludePattern("*.txt")
    fileIndex.assertInModule(txt, module, moduleDir, IN_CONTENT or IN_SOURCE)
    assertExcluded(java)
  }

  private fun assertNotExcluded(file: VirtualFile) {
    fileIndex.assertInModule(file, module, moduleDir)
  }

  private fun assertExcluded(file: VirtualFile) {
    fileIndex.assertInModule(file, module, moduleDir, EXCLUDED)
  }


  private fun addExcludePattern(pattern: String) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      MarkRootActionBase.findContentEntry(model, moduleDir)!!.addExcludePattern(pattern)
    }
  }

  private fun removeExcludePattern(pattern: String) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      MarkRootActionBase.findContentEntry(model, moduleDir)!!.removeExcludePattern(pattern)
    }
  }

}