// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx.ModifiableModelEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ClassLevelProjectModelExtension
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
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
            for (j in 0..49) {
              ourSourceFilesToTest.add(directory.file("file$j"))
            }
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
          val libraryRoot = smallModuleRoot.subdir("lib")
          val libraryClassesRoot = libraryRoot.subdir("classes")
          val librarySourcesRoot = libraryRoot.subdir("src")
          val library = ourProjectModel.addProjectLevelLibrary("lib$i") { model ->
            model.addRoot(libraryClassesRoot, OrderRootType.CLASSES)
            model.addRoot(librarySourcesRoot, OrderRootType.CLASSES)
          }
          ModuleRootModificationUtil.addDependency(module, library)
        }
      }
    }

    private fun VirtualFile.subdir(name: String): VirtualFile = createChildDirectory(ourProjectModel, name)
    private fun VirtualFile.deepSubdir(name: String, depth: Int = 50): VirtualFile =
      (1..depth).fold(this) { dir, _ -> dir.subdir(name) }
    private fun VirtualFile.file(name: String): VirtualFile = createChildData(ourProjectModel, name)

    @AfterAll
    @JvmStatic
    fun disposeProject() {
      VfsTestUtil.deleteFile(ourProjectRoot)
      ourSourceFilesToTest.clear()
    }
  }


  @Test
  fun testAccessPerformance() {
    val noId1 = LightVirtualFile()
    val noId2 = object : LightVirtualFile() {
      override fun getParent(): VirtualFile = noId1
    }
    val noId3 = object : LightVirtualFile() {
      override fun getParent(): VirtualFile = noId2
    }
    val filesWithoutId = arrayOf(noId1, noId2, noId3)
    val index = ProjectFileIndex.getInstance(ourProjectModel.project)
    val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
    PlatformTestUtil.startPerformanceTest("Directory index query", 2500) {
      repeat(100) {
        assertFalse(index.isInContent(fsRoot))
        for (file in filesWithoutId) {
          assertFalse(file is VirtualFileWithId)
          assertFalse(index.isInContent(file))
          assertFalse(index.isInSource(file))
          assertFalse(index.isInLibrary(file))
        }
        for (file in ourSourceFilesToTest) {
          assertTrue(index.isInContent(file))
          assertTrue(index.isInSource(file))
          assertFalse(index.isInLibrary(file))
        }
      }
    }.assertTiming()
  }
}