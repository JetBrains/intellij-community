// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
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
class SdkInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var sdkRoot: VirtualFile
  
  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
    sdkRoot = projectModel.baseProjectDir.newVirtualDirectory("sdk")
  }

  @Test
  fun `sdk roots`() {
    val sdkSourcesRoot = projectModel.baseProjectDir.newVirtualDirectory("sdk-sources")
    val sdkDocRoot = projectModel.baseProjectDir.newVirtualDirectory("sdk-docs")
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
      it.addRoot(sdkSourcesRoot, OrderRootType.SOURCES)
      it.addRoot(sdkDocRoot, OrderRootType.DOCUMENTATION)
    }
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    
    assertTrue(fileIndex.isInProject(sdkRoot))
    assertTrue(fileIndex.isInLibrary(sdkRoot))
    assertTrue(fileIndex.isInLibraryClasses(sdkRoot))
    assertFalse(fileIndex.isInLibrarySource(sdkRoot))
    
    assertTrue(fileIndex.isInProject(sdkSourcesRoot))
    assertTrue(fileIndex.isInLibrary(sdkSourcesRoot))
    assertFalse(fileIndex.isInLibraryClasses(sdkSourcesRoot))
    assertTrue(fileIndex.isInLibrarySource(sdkSourcesRoot))

    assertFalse(fileIndex.isInProject(sdkDocRoot))
  }

  @Test
  fun `add and remove dependency on module SDK`() {
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    assertFalse(fileIndex.isInProject(sdkRoot))
    
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    assertTrue(fileIndex.isInProject(sdkRoot))

    ModuleRootModificationUtil.setModuleSdk(module, null)
    assertFalse(fileIndex.isInProject(sdkRoot))
  }
  
  @Test
  fun `add and remove dependency on project SDK`() {
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    setProjectSdk(sdk)
    ModuleRootModificationUtil.setModuleSdk(module, null)
    assertFalse(fileIndex.isInProject(sdkRoot))
    
    ModuleRootModificationUtil.setSdkInherited(module)
    assertTrue(fileIndex.isInProject(sdkRoot))

    ModuleRootModificationUtil.setModuleSdk(module, projectModel.addSdk("different"))
    assertFalse(fileIndex.isInProject(sdkRoot))
  }

  @Test
  fun `add and remove SDK referenced from module`() {
    val sdk = projectModel.createSdk("unresolved") {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.modifyModel(module) {
      it.setInvalidSdk(sdk.name, sdk.sdkType.name)
      true
    }
    doTestSdkAddingAndRemoving(sdk)
  }

  @Test
  fun `add and remove project SDK inherited in module`() {
    val sdk = projectModel.createSdk("unresolved") {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    runWriteActionAndWait { 
      ProjectRootManager.getInstance(projectModel.project).setProjectSdkName(sdk.name, sdk.sdkType.name)
    }
    ModuleRootModificationUtil.setSdkInherited(module)
    doTestSdkAddingAndRemoving(sdk)
  }

  private fun doTestSdkAddingAndRemoving(sdk: Sdk) {
    assertFalse(fileIndex.isInProject(sdkRoot))

    projectModel.addSdk(sdk)
    assertTrue(fileIndex.isInProject(sdkRoot))

    removeSdk(sdk)
    assertFalse(fileIndex.isInProject(sdkRoot))
  }

  @Test
  fun `add and remove root from module SDK`() {
    val sdk = projectModel.addSdk()
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    doTestSdkModifications(sdk)
  }

  @Test
  fun `add and remove root from project SDK`() {
    val sdk = projectModel.addSdk()
    setProjectSdk(sdk)
    ModuleRootModificationUtil.setSdkInherited(module)
    doTestSdkModifications(sdk)
  }

  private fun removeSdk(sdk: Sdk) {
    runWriteActionAndWait {
      ProjectJdkTable.getInstance().removeJdk(sdk)
    }
  }

  private fun setProjectSdk(sdk: Sdk) {
    runWriteActionAndWait {
      ProjectRootManager.getInstance(projectModel.project).projectSdk = sdk
    }
  }

  private fun doTestSdkModifications(sdk: Sdk) {
    assertFalse(fileIndex.isInProject(sdkRoot))

    projectModel.modifySdk(sdk) {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    assertTrue(fileIndex.isInProject(sdkRoot))

    projectModel.modifySdk(sdk) {
      it.removeRoot(sdkRoot, OrderRootType.CLASSES)
    }
    assertFalse(fileIndex.isInProject(sdkRoot))
  }

  @Test
  internal fun `project SDK in project without modules`() {
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    setProjectSdk(sdk)
    ModuleRootModificationUtil.setModuleSdk(module, null)
    assertFalse(fileIndex.isInProject(sdkRoot))

    projectModel.removeModule(module)
    assertTrue(fileIndex.isInProject(sdkRoot))
  }
}