// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
class UpdatingProjectFileIndexOnModuleRootFileModificationsTest {
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
  fun `rename content root`() {
    val moduleFile = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertInModule(moduleFile, module, moduleDir)
    
    moduleDir.rename("module2")
    val newContentRoot = ModuleRootManager.getInstance(module).contentRoots.single()
    assertEquals("module2", newContentRoot.name)
    assertEquals(newContentRoot, moduleDir)
    val newFile = newContentRoot.findChild("file.txt")!! 
    fileIndex.assertInModule(newContentRoot, module, moduleDir)
    fileIndex.assertInModule(newFile, module, moduleDir)
  }
  
  @Test
  fun `move content root`() {
    val moduleFile = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    PsiTestUtil.addContentRoot(module, moduleDir)
    fileIndex.assertInModule(moduleFile, module, moduleDir)
    
    val newParent = projectModel.baseProjectDir.newVirtualDirectory("dir")
    moduleDir.move(newParent)
    val movedDir = newParent.findChild("module")!!
    assertEquals(movedDir, ModuleRootManager.getInstance(module).contentRoots.single())
    val newFile = movedDir.findChild("file.txt")!!
    fileIndex.assertInModule(movedDir, module, moduleDir)
    fileIndex.assertInModule(newFile, module, moduleDir)
  }
  
  @Test
  fun `rename file content root`() {
    val fileRoot = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    PsiTestUtil.addContentRoot(module, fileRoot)
    fileIndex.assertInModule(fileRoot, module, fileRoot)
    
    fileRoot.rename("file2.txt")
    val newFile = ModuleRootManager.getInstance(module).contentRoots.single()
    assertEquals("file2.txt", newFile.name)
    assertEquals(newFile, fileRoot)
    fileIndex.assertInModule(newFile, module, newFile)
  }
  
  @Test
  fun `move file content root`() {
    val fileRoot = projectModel.baseProjectDir.newVirtualFile("module/file.txt")
    PsiTestUtil.addContentRoot(module, fileRoot)
    fileIndex.assertInModule(fileRoot, module, fileRoot)
    
    val newParent = projectModel.baseProjectDir.newVirtualDirectory("dir")
    fileRoot.move(newParent)
    val movedFile = newParent.findChild("file.txt")!!
    assertEquals(movedFile, ModuleRootManager.getInstance(module).contentRoots.single())
    fileIndex.assertInModule(movedFile, module, movedFile)
  }
  
  @Test
  fun `rename parent directory of content root`() {
    val moduleFile = projectModel.baseProjectDir.newVirtualFile("module/root/file.txt")
    PsiTestUtil.addContentRoot(module, moduleFile.parent)
    fileIndex.assertInModule(moduleFile, module, moduleFile.parent)
    
    moduleDir.rename("module2")
    val newContentRoot = ModuleRootManager.getInstance(module).contentRoots.single()
    assertEquals("module2", newContentRoot.parent.name)
    assertEquals(moduleDir, newContentRoot.parent)
    val newFile = newContentRoot.findChild("file.txt")!! 
    fileIndex.assertInModule(newContentRoot, module, newContentRoot)
    fileIndex.assertInModule(newFile, module, newContentRoot)
  }
  
  @Test
  fun `move parent directory of content root`() {
    val moduleFile = projectModel.baseProjectDir.newVirtualFile("module/root/file.txt")
    PsiTestUtil.addContentRoot(module, moduleFile.parent)
    fileIndex.assertInModule(moduleFile, module, moduleFile.parent)
    
    val newParent = projectModel.baseProjectDir.newVirtualDirectory("dir")
    moduleDir.move(newParent)
    val movedDir = newParent.findChild("module")!!
    val newContentRoot = ModuleRootManager.getInstance(module).contentRoots.single()
    assertEquals(movedDir, newContentRoot.parent)
    val newFile = newContentRoot.findChild("file.txt")!!
    fileIndex.assertInModule(newContentRoot, module, newContentRoot)
    fileIndex.assertInModule(newFile, module, newContentRoot)
  }
  
  @Test
  fun `rename directory and file under content root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val moduleFile = projectModel.baseProjectDir.newVirtualFile("module/dir/file.txt")
    val subDir = moduleFile.parent
    fileIndex.assertInModule(subDir, module, moduleDir)
    fileIndex.assertInModule(moduleFile, module, moduleDir)
    subDir.rename("dir2")
    val renamedDir = moduleDir.findChild("dir2")!!
    val newModuleFile = renamedDir.findChild("file.txt")!!
    fileIndex.assertInModule(renamedDir, module, moduleDir)
    fileIndex.assertInModule(newModuleFile, module, moduleDir)

    newModuleFile.rename("file2.txt")
    val renamedFile = renamedDir.findChild("file2.txt")!!
    fileIndex.assertInModule(renamedFile, module, moduleDir)
  }

  @Test
  fun `move directory and file under content root`() {
    PsiTestUtil.addContentRoot(module, moduleDir)
    val moduleFile = projectModel.baseProjectDir.newVirtualFile("module/dir/file.txt")
    val subDir = moduleFile.parent
    fileIndex.assertInModule(moduleFile, module, moduleDir)
    val newParent = projectModel.baseProjectDir.newVirtualDirectory("module/dir2")
    subDir.move(newParent)
    val movedDir = newParent.findChild("dir")!!
    val newModuleFile = movedDir.findChild("file.txt")!!
    fileIndex.assertInModule(movedDir, module, moduleDir)
    fileIndex.assertInModule(newModuleFile, module, moduleDir)

    newModuleFile.move(newParent)
    val movedFile = newParent.findChild("file.txt")!!
    fileIndex.assertInModule(movedFile, module, moduleDir)
  }

  private fun VirtualFile.rename(newName: String) {
    runWriteActionAndWait { rename(this@UpdatingProjectFileIndexOnModuleRootFileModificationsTest, newName) }
  }

  private fun VirtualFile.move(newParent: VirtualFile) {
    runWriteActionAndWait { move(this@UpdatingProjectFileIndexOnModuleRootFileModificationsTest, newParent) }
  }
}