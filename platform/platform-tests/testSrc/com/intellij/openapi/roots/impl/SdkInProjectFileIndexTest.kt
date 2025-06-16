// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.NOT_IN_PROJECT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class SdkInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val baseSdkDir: TempDirectoryExtension = TempDirectoryExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var sdkRoot: VirtualFile
  
  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
    sdkRoot = baseSdkDir.newVirtualDirectory("sdk")
  }

  @Test
  fun `sdk roots`() {
    val sdkSourcesRoot = baseSdkDir.newVirtualDirectory("sdk-sources")
    val sdkDocRoot = baseSdkDir.newVirtualDirectory("sdk-docs")
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
      it.addRoot(sdkSourcesRoot, OrderRootType.SOURCES)
      it.addRoot(sdkDocRoot, OrderRootType.DOCUMENTATION)
    }
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    
    fileIndex.assertScope(sdkRoot, IN_LIBRARY)
    fileIndex.assertScope(sdkSourcesRoot, IN_LIBRARY or IN_SOURCE)
    fileIndex.assertScope(sdkDocRoot, NOT_IN_PROJECT)
  }

  @Test
  fun `add and remove dependency on module SDK`() {
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)
    
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    fileIndex.assertScope(sdkRoot, IN_LIBRARY)

    ModuleRootModificationUtil.setModuleSdk(module, null)
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)
  }
  
  @Test
  fun `add and remove dependency on project SDK`() {
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    val sdk2 = projectModel.addSdk("sdk2") {
      it.addRoot(projectModel.baseProjectDir.newVirtualDirectory("sdk2"), OrderRootType.CLASSES)
    }
    setProjectSdk(sdk)
    ModuleRootModificationUtil.setModuleSdk(module, sdk2)
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)
    
    ModuleRootModificationUtil.setSdkInherited(module)
    fileIndex.assertScope(sdkRoot, IN_LIBRARY)

    ModuleRootModificationUtil.setModuleSdk(module, projectModel.addSdk("different"))
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)
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
  fun `change project SDK`() {
    val sdk1 = projectModel.addSdk("sdk1") {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    val sdk2Root = baseSdkDir.newVirtualDirectory("sdk2")
    val sdk2 = projectModel.addSdk("sdk2") {
      it.addRoot(sdk2Root, OrderRootType.CLASSES)
    }

    setProjectSdk(sdk1)
    ModuleRootModificationUtil.setSdkInherited(module)
    fileIndex.assertScope(sdkRoot, IN_LIBRARY)
    fileIndex.assertScope(sdk2Root, NOT_IN_PROJECT)

    setProjectSdk(sdk2)
    fileIndex.assertScope(sdk2Root, IN_LIBRARY)
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)
  }

  @Test
  fun `add and remove project SDK inherited in module`() {
    val module2 = projectModel.createModule("module2")
    val sdk2 = projectModel.createSdk("sdk2")
    WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk2, projectModel.disposableRule.disposable) }
    ModuleRootModificationUtil.setModuleSdk(module2, sdk2)
    
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
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)

    projectModel.addSdk(sdk)
    fileIndex.assertScope(sdkRoot, IN_LIBRARY)

    removeSdk(sdk)
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)
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
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)

    projectModel.modifySdk(sdk) {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(sdkRoot, IN_LIBRARY)

    projectModel.modifySdk(sdk) {
      it.removeRoot(sdkRoot, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)
  }

  @Test
  fun `project SDK in project without modules`() {
    val sdk = projectModel.addSdk {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }
    val sdk2 = projectModel.addSdk("sdk2") {
      it.addRoot(baseSdkDir.newVirtualDirectory("sdk2"), OrderRootType.CLASSES)
    }
    setProjectSdk(sdk)
    ModuleRootModificationUtil.setModuleSdk(module, sdk2)
    fileIndex.assertScope(sdkRoot, NOT_IN_PROJECT)

    projectModel.removeModule(module)
    fileIndex.assertScope(sdkRoot, IN_LIBRARY)
  }
}