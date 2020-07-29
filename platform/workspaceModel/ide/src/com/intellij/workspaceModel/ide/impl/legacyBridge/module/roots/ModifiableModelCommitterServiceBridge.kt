package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.ModifiableModelCommitterService
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModifiableModuleModelBridge
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class ModifiableModelCommitterServiceBridge : ModifiableModelCommitterService {
  override fun multiCommit(rootModels: MutableCollection<out ModifiableRootModel>, moduleModel: ModifiableModuleModel) {
    // TODO Naive impl, check for existing contact in com.intellij.openapi.module.impl.ModuleManagerImpl.commitModelWithRunnable
    val diffs = mutableSetOf<WorkspaceEntityStorageBuilder>()
    diffs += (moduleModel as ModifiableModuleModelBridge).collectChanges()
    for (rootModel in rootModels) {
      if (rootModel.isChanged) {
        diffs += (rootModel as ModifiableRootModelBridge).collectChangesAndDispose() ?: continue
      }
      else rootModel.dispose()
    }

    WorkspaceModel.getInstance(moduleModel.project).updateProjectModel { builder ->
      diffs.forEach { builder.addDiff(it) }
    }
  }
}