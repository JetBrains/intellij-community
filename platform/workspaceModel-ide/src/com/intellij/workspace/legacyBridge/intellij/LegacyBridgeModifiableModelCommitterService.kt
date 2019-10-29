package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.ModifiableModelCommitterService
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LegacyBridgeModifiableModelCommitterService : ModifiableModelCommitterService {
  override fun multiCommit(rootModels: MutableCollection<out ModifiableRootModel>, moduleModel: ModifiableModuleModel) {
    // TODO Add all diffs at once
    // TODO Naive impl, check for existing contact in com.intellij.openapi.module.impl.ModuleManagerImpl.commitModelWithRunnable
    for (rootModel in rootModels) {
      if (rootModel.isChanged) rootModel.commit() else rootModel.dispose()
    }
    moduleModel.commit()
  }
}