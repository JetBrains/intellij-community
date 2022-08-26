// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.toBuilder
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ModifiableRootModelBridgeTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test(expected = Test.None::class)
  fun `removing module with modifiable model`() {
    runWriteActionAndWait {
      val module = projectModel.createModule()
      val moduleRootManager = ModuleRootManager.getInstance(module) as ModuleRootComponentBridge

      val diff = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.toBuilder()

      val modifiableModel = moduleRootManager.getModifiableModel(diff,
                                                                 RootConfigurationAccessor.DEFAULT_INSTANCE) as ModifiableRootModelBridge

      (ModuleManager.getInstance(projectModel.project) as ModuleManagerBridgeImpl).getModifiableModel(diff).disposeModule(module)

      modifiableModel.prepareForCommit()
      modifiableModel.postCommit()
    }
  }

  @Test(expected = Test.None::class)
  fun `getting module root model from modifiable module`() {
    runWriteActionAndWait {
      val moduleModifiableModel = ModuleManager.getInstance(projectModel.project).getModifiableModel()
      val newModule = moduleModifiableModel.newModule(projectModel.projectRootDir.resolve("myModule/myModule.iml"),
                                                      EmptyModuleType.EMPTY_MODULE) as ModuleBridge


      val moduleRootManager = ModuleRootManager.getInstance(newModule) as ModuleRootComponentBridge

      // Assert no exceptions
      val model = moduleRootManager.getModifiableModel(newModule.diff!! as MutableEntityStorage,
                                                       RootConfigurationAccessor.DEFAULT_INSTANCE)
      model.dispose()
      moduleModifiableModel.dispose()
    }
  }

  @Test(expected = Test.None::class)
  fun `get modifiable models of renamed module`() {
    runWriteActionAndWait {
      val moduleModifiableModel = ModuleManager.getInstance(projectModel.project).getModifiableModel()
      val newModule = moduleModifiableModel.newModule(projectModel.projectRootDir.resolve("myModule/myModule.iml"),
                                                      EmptyModuleType.EMPTY_MODULE) as ModuleBridge
      moduleModifiableModel.commit()

      val builder = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.toBuilder()

      val anotherModifiableModel = (ModuleManager.getInstance(projectModel.project) as ModuleManagerBridgeImpl).getModifiableModel(builder)
      anotherModifiableModel.renameModule(newModule, "newName")

      val moduleRootManager = ModuleRootManager.getInstance(newModule) as ModuleRootComponentBridge

      // Assert no exceptions
      val model = moduleRootManager.getModifiableModel(builder, RootConfigurationAccessor.DEFAULT_INSTANCE)
      anotherModifiableModel.dispose()
    }
  }
}
