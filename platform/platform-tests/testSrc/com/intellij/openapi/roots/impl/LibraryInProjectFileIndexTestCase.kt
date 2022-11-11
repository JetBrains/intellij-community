// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
abstract class LibraryInProjectFileIndexTestCase {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  protected abstract val libraryTable: LibraryTable
  protected abstract fun createLibrary(name: String = "lib", setup: (LibraryEx.ModifiableModelEx) -> Unit = {}): LibraryEx
  
  lateinit var root: VirtualFile
  lateinit var module: Module

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @BeforeEach
  internal fun setUp() {
    module = projectModel.createModule()
    root = projectModel.baseProjectDir.newVirtualDirectory("lib")
  }

  @Test
  fun `library roots`() {
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("lib-src")
    val docRoot = projectModel.baseProjectDir.newVirtualDirectory("lib-doc")
    val excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("lib-exc")
    val library = createLibrary {
      it.addRoot(root, OrderRootType.CLASSES)
      it.addRoot(srcRoot, OrderRootType.SOURCES)
      it.addRoot(docRoot, OrderRootType.DOCUMENTATION)
      it.addExcludedRoot(excludedRoot.url)
    }
    ModuleRootModificationUtil.addDependency(module, library)

    fun assertInLibrary(file: VirtualFile) {
      assertTrue(fileIndex.isInProject(file))
      assertFalse(fileIndex.isInContent(file))
      assertFalse(fileIndex.isExcluded(file))
      assertTrue(fileIndex.isInLibrary(file))
    }

    assertInLibrary(root)
    assertTrue(fileIndex.isInLibraryClasses(root))
    assertFalse(fileIndex.isInLibrarySource(root))
    
    assertInLibrary(srcRoot)
    assertFalse(fileIndex.isInLibraryClasses(srcRoot))
    assertTrue(fileIndex.isInLibrarySource(srcRoot))

    assertFalse(fileIndex.isInProject(docRoot))
    assertFalse(fileIndex.isInLibrary(docRoot))
    
    assertFalse(fileIndex.isInProject(excludedRoot))
    assertTrue(fileIndex.isInProjectOrExcluded(excludedRoot))
    assertTrue(fileIndex.isExcluded(excludedRoot))
  }

  @Test
  fun `add and remove dependency on library`() {
    val library = createLibrary { 
      it.addRoot(root, OrderRootType.CLASSES)
    }
    assertFalse(fileIndex.isInProject(root))

    ModuleRootModificationUtil.addDependency(module, library)
    assertTrue(fileIndex.isInProject(root))
    
    ModuleRootModificationUtil.removeDependency(module, library)
    assertFalse(fileIndex.isInProject(root))
  }

  @Test
  fun `add and remove library referenced from module`() {
    val name = "unresolved"
    ModuleRootModificationUtil.modifyModel(module) {
      it.addInvalidLibrary(name, libraryTable.tableLevel)
      true
    }
    assertFalse(fileIndex.isInProject(root))
    
    val library = createLibrary(name) { 
      it.addRoot(root, OrderRootType.CLASSES)
    }
    assertTrue(fileIndex.isInProject(root))

    runWriteActionAndWait { 
      libraryTable.removeLibrary(library)
    }
    assertFalse(fileIndex.isInProject(root))
  }

  @Test
  fun `add and remove root from library`() {
    val library = createLibrary()
    ModuleRootModificationUtil.addDependency(module, library)
    assertFalse(fileIndex.isInProject(root))
    
    projectModel.modifyLibrary(library) {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    assertTrue(fileIndex.isInProject(root))
    
    projectModel.modifyLibrary(library) {
      it.removeRoot(root.url, OrderRootType.CLASSES)
    }
    assertFalse(fileIndex.isInProject(root))
  }

  @Test
  fun `add and remove excluded root from library`() {
    val library = createLibrary()
    val excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("exc")
    ModuleRootModificationUtil.addDependency(module, library)
    assertFalse(fileIndex.isInProject(excludedRoot))
    assertFalse(fileIndex.isInProjectOrExcluded(excludedRoot))
    
    projectModel.modifyLibrary(library) {
      it.addExcludedRoot(excludedRoot.url)
    }
    assertFalse(fileIndex.isInProject(excludedRoot))
    assertTrue(fileIndex.isInProjectOrExcluded(excludedRoot))
    
    projectModel.modifyLibrary(library) {
      it.removeExcludedRoot(excludedRoot.url)
    }
    assertFalse(fileIndex.isInProject(excludedRoot))
    assertFalse(fileIndex.isInProjectOrExcluded(excludedRoot))
  }
}

