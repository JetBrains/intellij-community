// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.ModifiableModelCommitterService
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableModuleModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class ModifiableModelCommitterServiceBridge : ModifiableModelCommitterService {
  override fun multiCommit(rootModels: MutableCollection<out ModifiableRootModel>, moduleModel: ModifiableModuleModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    // TODO Naive impl, check for existing contact in com.intellij.openapi.module.impl.ModuleManagerImpl.commitModelWithRunnable
    val diffs = mutableSetOf<MutableEntityStorage>()
    diffs += (moduleModel as ModifiableModuleModelBridge).collectChanges()
    val committedModels = ArrayList<ModifiableRootModelBridge>()
    for (rootModel in rootModels) {
      if (rootModel.isChanged) {
        diffs += (rootModel as ModifiableRootModelBridgeImpl).collectChangesAndDispose() ?: continue
        committedModels += rootModel as ModifiableRootModelBridge
      }
      else rootModel.dispose()
    }

    WorkspaceModel.getInstance(moduleModel.project).updateProjectModel("Multicommit for modifiable models") { builder ->
      diffs.forEach { builder.applyChangesFrom(it) }
    }

    for (rootModel in committedModels) {
      rootModel.postCommit()
    }
  }
}