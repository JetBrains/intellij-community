// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.project

import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class BaseProjectDirectoriesTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @Test
  fun `add remove content root`() {
    val module = projectModel.createModule()
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("root1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("root2")
    checkBaseDirectories()
    
    ModuleRootModificationUtil.addContentRoot(module, root1)
    checkBaseDirectories(root1)

    ModuleRootModificationUtil.addContentRoot(module, root2)
    checkBaseDirectories(root1, root2)
    
    PsiTestUtil.removeContentEntry(module, root1)
    checkBaseDirectories(root2)
    
    PsiTestUtil.removeContentEntry(module, root2)
    checkBaseDirectories()
  }

  @Test
  fun `add inner content root`() {
    val module = projectModel.createModule()
    val root = projectModel.baseProjectDir.newVirtualDirectory("root")
    ModuleRootModificationUtil.addContentRoot(module, root)
    
    checkBaseDirectories(root)

    val inner = projectModel.baseProjectDir.newVirtualDirectory("root/inner")
    ModuleRootModificationUtil.addContentRoot(module, inner)
    checkBaseDirectories(root)
  }

  @Test
  fun `add remove outer content root`() {
    val module = projectModel.createModule()
    val outer = projectModel.baseProjectDir.newVirtualDirectory("root")
    val inner = projectModel.baseProjectDir.newVirtualDirectory("root/inner")
    ModuleRootModificationUtil.addContentRoot(module, inner)
    checkBaseDirectories(inner)

    ModuleRootModificationUtil.addContentRoot(module, outer)
    checkBaseDirectories(outer)
    
    PsiTestUtil.removeContentEntry(module, outer)
    checkBaseDirectories(inner)
  }

  private fun checkBaseDirectories(vararg files: VirtualFile) {
    assertEquals(files.toSet(), projectModel.project.getBaseDirectories().toSet())
  }
}