// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.fail
import kotlin.concurrent.thread

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

      val diff = WorkspaceModel.getInstance(projectModel.project).currentSnapshot.toBuilder()

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

      val builder = WorkspaceModel.getInstance(projectModel.project).currentSnapshot.toBuilder()

      val anotherModifiableModel = (ModuleManager.getInstance(projectModel.project) as ModuleManagerBridgeImpl).getModifiableModel(builder)
      anotherModifiableModel.renameModule(newModule, "newName")

      val moduleRootManager = ModuleRootManager.getInstance(newModule) as ModuleRootComponentBridge

      // Assert no exceptions
      val model = moduleRootManager.getModifiableModel(builder, RootConfigurationAccessor.DEFAULT_INSTANCE)
      anotherModifiableModel.dispose()
    }
  }

  /**
   * Test structure: 3 threads:
   * - Silent update in the loop without write action
   * - 2 threads try to get exception
   */
  @Test
  fun `get extensions of the model that is modified`() {
    val module = runWriteActionAndWait {
      val moduleModifiableModel = ModuleManager.getInstance(projectModel.project).getModifiableModel()
      val newModule = moduleModifiableModel.newModule(projectModel.projectRootDir.resolve("myModule/myModule.iml"),
                                                      EmptyModuleType.EMPTY_MODULE) as ModuleBridge
      moduleModifiableModel.commit()
      newModule
    }

    var exception: Throwable? = null
    val job = thread(name = "Background silent updater") {
      repeat(100_000) {
        (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl).updateProjectModelSilent("Test") {
          // No actual update, just run the update function
        }

        val ex = runCatching {
          val moduleRootManager = ModuleRootManager.getInstance(module) as ModuleRootComponentBridge
          moduleRootManager.getModuleExtension(Any::class.java)
        }
        if (ex.isFailure) exception = ex.exceptionOrNull()
      }
    }
    val ex = runCatching {
      repeat(100_000) {
        val moduleRootManager = ModuleRootManager.getInstance(module) as ModuleRootComponentBridge
        moduleRootManager.getModuleExtension(Any::class.java)
      }
    }
    job.join()
    if (exception != null) fail(exception)
    if (ex.isFailure) fail(ex.exceptionOrNull())
  }
}
