// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModifiableRootModelBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
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
  val projectModel = ProjectModelRule(true)

  @Test(expected = Test.None::class)
  fun `removing module with modifiable model`() {
    runWriteActionAndWait {
      val module = projectModel.createModule()
      val moduleRootManager = ModuleRootManager.getInstance(module) as ModuleRootComponentBridge

      val initialStore = WorkspaceModel.getInstance(projectModel.project).entityStorage.current
      val diff = initialStore.toBuilder()

      val modifiableModel = moduleRootManager.getModifiableModel(diff, initialStore,
                                                                 RootConfigurationAccessor.DEFAULT_INSTANCE) as ModifiableRootModelBridgeImpl

      (ModuleManager.getInstance(projectModel.project) as ModuleManagerComponentBridge).getModifiableModel(diff).disposeModule(module)

      modifiableModel.prepareForCommit()
      modifiableModel.postCommit()
    }
  }
}