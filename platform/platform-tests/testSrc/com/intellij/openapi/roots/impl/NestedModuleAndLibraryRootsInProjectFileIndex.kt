// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
class NestedModuleAndLibraryRootsInProjectFileIndex {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @Test
  fun `module root under library root`() {
    val file = projectModel.baseProjectDir.newVirtualDirectory("module/lib/a.txt")
    val libDir = file.parent
    val moduleDir = libDir.parent
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, moduleDir)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libDir, OrderRootType.CLASSES)
    }
    assertTrue(fileIndex.isInLibrary(file))
    assertTrue(fileIndex.isInContent(file))
    assertEquals(module, fileIndex.getModuleForFile(file))
  }

  @Test
  fun `library root under module root`() {
    val file = projectModel.baseProjectDir.newVirtualDirectory("lib/module/a.txt")
    val moduleDir = file.parent
    val libDir = moduleDir.parent
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, moduleDir)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libDir, OrderRootType.CLASSES)
    }
    assertTrue(fileIndex.isInLibrary(file))
    assertTrue(fileIndex.isInContent(file))
    assertEquals(module, fileIndex.getModuleForFile(file))
  }

  @Test
  fun `add remove excluded root under library under module`() {
    val file = projectModel.baseProjectDir.newVirtualDirectory("module/lib/exc/a.txt")
    val excludedDir = file.parent
    val libDir = excludedDir.parent
    val moduleDir = libDir.parent
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, moduleDir)
    val library = projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libDir, OrderRootType.CLASSES)
    }
    checkAddingAndRemovingExcludedRootUnderModuleAndLibrary(file, excludedDir, module, library)
  }
  
  @Test
  fun `add remove excluded root under module under library`() {
    val file = projectModel.baseProjectDir.newVirtualDirectory("lib/module/exc/a.txt")
    val excludedDir = file.parent
    val moduleDir = excludedDir.parent
    val libDir = moduleDir.parent
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, moduleDir)
    val library = projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libDir, OrderRootType.CLASSES)
    }
    checkAddingAndRemovingExcludedRootUnderModuleAndLibrary(file, excludedDir, module, library)
  }

  private fun checkAddingAndRemovingExcludedRootUnderModuleAndLibrary(file: VirtualFile, excludedDir: VirtualFile,
                                                                      module: Module, library: LibraryEx) {
    assertTrue(fileIndex.isInContent(file))

    PsiTestUtil.addExcludedRoot(module, excludedDir)
    assertFalse(fileIndex.isInContent(file))
    assertTrue(fileIndex.isInLibrary(file))

    PsiTestUtil.removeExcludedRoot(module, excludedDir)
    projectModel.modifyLibrary(library) {
      it.addExcludedRoot(excludedDir.url)
    }
    assertTrue(fileIndex.isInContent(file))
    assertFalse(fileIndex.isInLibrary(file))

    projectModel.modifyLibrary(library) {
      it.removeExcludedRoot(excludedDir.url)
    }
    assertTrue(fileIndex.isInContent(file))
    assertTrue(fileIndex.isInLibrary(file))
  }

  @Test
  fun `same dir as source root for module and library`() {
    val file = projectModel.baseProjectDir.newVirtualFile("src/a.txt")
    val module = projectModel.createModule()
    PsiTestUtil.addSourceRoot(module, file.parent)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(file.parent, OrderRootType.SOURCES)
    }
    assertTrue(fileIndex.isInLibrary(file))
    assertTrue(fileIndex.isInSource(file))
    assertTrue(fileIndex.isInSourceContent(file))
    assertTrue(fileIndex.isInLibrarySource(file))
    assertFalse(fileIndex.isInLibraryClasses(file))
    assertEquals(module, fileIndex.getModuleForFile(file))
  }

  @Test
  fun `same dir as source root for module and classes root for library`() {
    val file = projectModel.baseProjectDir.newVirtualFile("src/a.txt")
    val module = projectModel.createModule()
    PsiTestUtil.addSourceRoot(module, file.parent)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(file.parent, OrderRootType.CLASSES)
    }
    assertTrue(fileIndex.isInLibrary(file))
    assertTrue(fileIndex.isInSource(file))
    assertTrue(fileIndex.isInSourceContent(file))
    assertFalse(fileIndex.isInLibrarySource(file))
    assertTrue(fileIndex.isInLibraryClasses(file))
    assertEquals(module, fileIndex.getModuleForFile(file))
  }
}