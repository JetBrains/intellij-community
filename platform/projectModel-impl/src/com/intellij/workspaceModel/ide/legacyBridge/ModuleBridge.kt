// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModuleBridge : ModuleEx {
  val moduleEntityId: ModuleId

  /**
   * Entity store used by this module and related components like root manager.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  var entityStorage: VersionedEntityStorage

  /**
   * Specifies a diff where module related changes should be written (like root changes).
   * If it's null related changes should be written directly with updateProjectModel.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  var diff: MutableEntityStorage?

  fun rename(newName: String, newModuleFileUrl: VirtualFileUrl?, notifyStorage: Boolean)

  fun onImlFileMoved(newModuleFileUrl: VirtualFileUrl)

  fun registerComponents(corePlugin: IdeaPluginDescriptor?,
                         modules: List<IdeaPluginDescriptorImpl>,
                         precomputedExtensionModel: PrecomputedExtensionModel?,
                         app: Application?,
                         listenerCallbacks: MutableList<in Runnable>?)

  fun callCreateComponents()

  suspend fun callCreateComponentsNonBlocking()

  fun initFacets()
}
