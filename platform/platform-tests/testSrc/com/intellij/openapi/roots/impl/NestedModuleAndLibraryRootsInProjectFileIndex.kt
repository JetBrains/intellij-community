// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.EXCLUDED_FROM_MODULE_ONLY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_CONTENT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
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
    fileIndex.assertInModule(file, module, moduleDir, IN_CONTENT or IN_LIBRARY)
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
    fileIndex.assertInModule(file, module, moduleDir, IN_CONTENT or IN_LIBRARY)
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
    checkAddingAndRemovingExcludedRootUnderModuleAndLibrary(file, moduleDir, excludedDir, module, library)
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
    checkAddingAndRemovingExcludedRootUnderModuleAndLibrary(file, moduleDir, excludedDir, module, library)
  }

  private fun checkAddingAndRemovingExcludedRootUnderModuleAndLibrary(file: VirtualFile, moduleDir: VirtualFile, excludedDir: VirtualFile,
                                                                      module: Module, library: LibraryEx) {
    fileIndex.assertInModule(file, module, moduleDir, IN_CONTENT or IN_LIBRARY)

    PsiTestUtil.addExcludedRoot(module, excludedDir)
    if (WorkspaceFileIndexEx.IS_ENABLED) {
      fileIndex.assertInModule(file, module, moduleDir, IN_LIBRARY or EXCLUDED_FROM_MODULE_ONLY)
    }
    else {
      fileIndex.assertScope(file, IN_LIBRARY)
    }

    projectModel.modifyLibrary(library) {
      it.addExcludedRoot(excludedDir.url)
    }
    fileIndex.assertInModule(file, module, moduleDir, EXCLUDED)
    
    PsiTestUtil.removeExcludedRoot(module, excludedDir)
    fileIndex.assertInModule(file, module, moduleDir)

    projectModel.modifyLibrary(library) {
      it.removeExcludedRoot(excludedDir.url)
    }
    fileIndex.assertInModule(file, module, moduleDir, IN_CONTENT or IN_LIBRARY)
  }

  @Test
  fun `same dir as source root for module and library`() {
    val file = projectModel.baseProjectDir.newVirtualFile("src/a.txt")
    val module = projectModel.createModule()
    PsiTestUtil.addSourceRoot(module, file.parent)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(file.parent, OrderRootType.SOURCES)
    }
    fileIndex.assertInModule(file, module, file.parent, IN_CONTENT or IN_LIBRARY or IN_SOURCE)
  }

  @Test
  fun `same dir as source root for module and classes root for library`() {
    val file = projectModel.baseProjectDir.newVirtualFile("src/a.txt")
    val module = projectModel.createModule()
    PsiTestUtil.addSourceRoot(module, file.parent)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(file.parent, OrderRootType.CLASSES)
    }
    fileIndex.assertInModule(file, module, file.parent, IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE)
  }

  @Test
  fun `same dir as source root for module and excluded root for library`() {
    val file = projectModel.baseProjectDir.newVirtualFile("src/a.txt")
    val root = file.parent
    val module = projectModel.createModule()
    PsiTestUtil.addSourceRoot(module, root)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(root, OrderRootType.SOURCES)
      it.addExcludedRoot(root.url)
    }
    fileIndex.assertInModule(file, module, root, IN_CONTENT or IN_SOURCE)
    fileIndex.assertInModule(root, module, root, IN_CONTENT or IN_SOURCE)
  }
  
  @Test
  fun `same dir as library root and excluded root for module`() {
    val file = projectModel.baseProjectDir.newVirtualFile("lib/a.txt")
    val root = file.parent
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, root)
    PsiTestUtil.addExcludedRoot(module, root)
    projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    if (WorkspaceFileIndexEx.IS_ENABLED) {
      fileIndex.assertInModule(file, module, root, IN_LIBRARY or EXCLUDED_FROM_MODULE_ONLY)
      fileIndex.assertInModule(root, module, root, IN_LIBRARY or EXCLUDED_FROM_MODULE_ONLY)
    }
    else {
      fileIndex.assertScope(file, IN_LIBRARY)
      fileIndex.assertScope(root, IN_LIBRARY)
    }
  }
}