package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

interface ModuleBridge : ModuleEx {
  val moduleEntityId: ModuleId

  /**
   * Entity store used by this module and related components like root manager.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  var entityStorage: VersionedEntityStorage

  /**
   * Specifies a diff where module related changes should be written (like root changes).
   * If it's null related changes should written directly with updateProjectModel.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  var diff: WorkspaceEntityStorageDiffBuilder?

  fun rename(newName: String, newModuleFileUrl: VirtualFileUrl?, notifyStorage: Boolean)

  fun registerComponents(corePlugin: IdeaPluginDescriptor?, plugins: List<IdeaPluginDescriptorImpl>,
                         precomputedExtensionModel: PrecomputedExtensionModel?, app: Application?, listenerCallbacks: List<Runnable>?)

  fun callCreateComponents()
}
