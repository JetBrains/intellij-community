// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ClassLevelProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@TestApplication
class ProjectFileIndexPerformanceTest {
  companion object {
    @RegisterExtension
    @JvmField
    val ourProjectModel = ClassLevelProjectModelExtension()
    
    val ourSourceFilesToTest: MutableList<VirtualFile> = ArrayList()
    val ourLibraryFilesToTest: MutableList<VirtualFile> = ArrayList()
    val ourLibrarySourceFilesToTest: MutableList<VirtualFile> = ArrayList()
    val ourExcludedFilesToTest: MutableList<VirtualFile> = ArrayList()
    private lateinit var ourProjectRoot: VirtualFile

    @BeforeAll
    @JvmStatic
    fun initProject() {
      WriteAction.runAndWait<IOException> {
        val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
        ourProjectRoot = fsRoot.subdir(ProjectFileIndexPerformanceTest::class.java.simpleName)
        val bigModuleRoot = ourProjectRoot.subdir("big")
        WriteAction.runAndWait<IOException> {
          for (i in 0..99) {
            val directory = bigModuleRoot.subdir("dir$i").deepSubdir("subDir", 50)
            directory.createManyFiles(50, "File", ".java", ourSourceFilesToTest)
          }
        }
        val bigModule = ourProjectModel.createModule("big")
        PsiTestUtil.addSourceRoot(bigModule, bigModuleRoot)
        for (i in 0..499) {
          val module = ourProjectModel.createModule("small$i")
          val smallModuleRoot = ourProjectRoot.subdir("small$i")
          PsiTestUtil.addContentRoot(module, smallModuleRoot)
          val srcRoot = smallModuleRoot.subdir("src")
          PsiTestUtil.addSourceRoot(module, srcRoot)
          srcRoot.file("File$i.java")
          val excludedRoot = smallModuleRoot.subdir("excluded")
          PsiTestUtil.addExcludedRoot(module, excludedRoot)
          excludedRoot.deepSubdir("exc", 30).createManyFiles(10, "Exc", ".java", ourExcludedFilesToTest)
          val libraryRoot = smallModuleRoot.subdir("lib")
          val libraryClassesRoot = libraryRoot.subdir("classes")
          val librarySourcesRoot = libraryRoot.subdir("src")
          val library = ourProjectModel.addProjectLevelLibrary("lib$i") { model ->
            model.addRoot(libraryClassesRoot, OrderRootType.CLASSES)
            model.addRoot(librarySourcesRoot, OrderRootType.SOURCES)
          }
          libraryClassesRoot.deepSubdir("pack", 30)
            .createManyFiles(10, "Lib", ".class", ourLibraryFilesToTest)
          librarySourcesRoot.deepSubdir("pack", 30)
            .createManyFiles(10, "Lib", ".java", ourLibrarySourceFilesToTest)
          ModuleRootModificationUtil.addDependency(module, library)
        }
      }
    }

    private fun VirtualFile.subdir(name: String): VirtualFile = createChildDirectory(ourProjectModel, name)
    private fun VirtualFile.deepSubdir(name: String, depth: Int = 50): VirtualFile =
      (1..depth).fold(this) { dir, _ -> dir.subdir(name) }
    private fun VirtualFile.file(name: String): VirtualFile = createChildData(ourProjectModel, name)
    
    private fun VirtualFile.createManyFiles(number: Int, namePrefix: String, nameSuffix: String, result: MutableList<VirtualFile>) {
      for (i in 0 until number) {
        result.add(file("$namePrefix$i$nameSuffix"))
      }
    }

    @AfterAll
    @JvmStatic
    fun disposeProject() {
      VfsTestUtil.deleteFile(ourProjectRoot)
      ourSourceFilesToTest.clear()
      ourLibraryFilesToTest.clear()
      ourLibrarySourceFilesToTest.clear()
      ourExcludedFilesToTest.clear()
    }
    
    private val fileIndex: ProjectFileIndex
      get() = ProjectFileIndex.getInstance(ourProjectModel.project)
  }

  @Test
  fun `access to source files and files without id`() {
    val noId1 = LightVirtualFile()
    val noId2 = object : LightVirtualFile() {
      override fun getParent(): VirtualFile = noId1
    }
    val noId3 = object : LightVirtualFile() {
      override fun getParent(): VirtualFile = noId2
    }
    val filesWithoutId = arrayOf(noId1, noId2, noId3)
    val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
    PlatformTestUtil.startPerformanceTest("Checking status of source files in ProjectFileIndex", 2500) {
      runReadAction {
        repeat(100) {
          assertFalse(fileIndex.isInContent(fsRoot))
          for (file in filesWithoutId) {
            assertFalse(file is VirtualFileWithId)
            assertFalse(fileIndex.isInContent(file))
            assertFalse(fileIndex.isInSource(file))
            assertFalse(fileIndex.isInLibrary(file))
          }
          for (file in ourSourceFilesToTest) {
            assertTrue(fileIndex.isInContent(file))
            assertTrue(fileIndex.isInSource(file))
            assertFalse(fileIndex.isInLibrary(file))
          }
        }
      }
    }.assertTiming()
  }

  @Test
  fun `access to excluded files`() {
    PlatformTestUtil.startPerformanceTest("Checking status of excluded files in ProjectFileIndex", 250) {
      runReadAction {
        repeat(10) {
          for (file in ourExcludedFilesToTest) {
            assertFalse(fileIndex.isInContent(file))
            assertFalse(fileIndex.isInSource(file))
            assertTrue(fileIndex.isExcluded(file))
          }
        }
      }
    }.assertTiming()
  }
  
  @Test
  fun `access to library files`() {
    PlatformTestUtil.startPerformanceTest("Checking status of library files in ProjectFileIndex", 600) {
      runReadAction {
        repeat(10) {
          for (file in ourLibraryFilesToTest) {
            assertTrue(fileIndex.isInContent(file))
            assertFalse(fileIndex.isInSource(file))
            assertFalse(fileIndex.isInTestSourceContent(file))
            assertTrue(fileIndex.isInProject(file))
            assertTrue(fileIndex.isInLibrary(file))
            assertTrue(fileIndex.isInLibraryClasses(file))
            assertFalse(fileIndex.isInLibrarySource(file))
            assertFalse(fileIndex.isUnderIgnored(file))
            assertFalse(fileIndex.isExcluded(file))
          }
        }
      }
    }.assertTiming()
  }
  
  @Test
  fun `access to library source files`() {
    PlatformTestUtil.startPerformanceTest("Checking status of library source files in ProjectFileIndex", 600) {
      runReadAction {
        repeat(10) {
          for (file in ourLibrarySourceFilesToTest) {
            assertTrue(fileIndex.isInContent(file))
            assertTrue(fileIndex.isInSource(file))
            assertFalse(fileIndex.isInTestSourceContent(file))
            assertTrue(fileIndex.isInProject(file))
            assertTrue(fileIndex.isInLibrary(file))
            assertFalse(fileIndex.isInLibraryClasses(file))
            assertTrue(fileIndex.isInLibrarySource(file))
            assertFalse(fileIndex.isUnderIgnored(file))
            assertFalse(fileIndex.isExcluded(file))
          }
        }
      }
    }.assertTiming()
  }

  @Test
  fun `access to index after change`() {
    assertTrue(WorkspaceFileIndexEx.IS_ENABLED, "This test is expected to fail if the old implementation of DirectoryIndex is used")
    val newRoot = runWriteActionAndWait { ourProjectRoot.subdir("newContentRoot") }
    val module = ourProjectModel.moduleManager.findModuleByName("big")!!
    PlatformTestUtil.startPerformanceTest("Checking status of file after adding and removing content root", 5) {
      runReadAction {
        repeat(50) {
          assertFalse(fileIndex.isInContent(newRoot))
        }
      }
    }.setup {
      PsiTestUtil.addContentRoot(module, newRoot)
      PsiTestUtil.removeContentEntry(module, newRoot)
    }.assertTiming()
  }
}