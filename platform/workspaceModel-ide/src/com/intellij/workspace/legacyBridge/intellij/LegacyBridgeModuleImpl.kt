// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.ModuleId
import com.intellij.workspace.api.TypedEntityStorageDiffBuilder
import com.intellij.workspace.api.TypedEntityStore
import java.io.File

internal class LegacyBridgeModuleImpl(
  override var moduleEntityId: ModuleId,
  name: String,
  project: Project,
  private val filePath: String,
  override var entityStore: TypedEntityStore,
  override var diff: TypedEntityStorageDiffBuilder?
) : ModuleImpl(name, project, filePath), LegacyBridgeModule {

  override fun rename(newName: String, notifyStorage: Boolean) {
    moduleEntityId = moduleEntityId.copy(name = newName)
    super<ModuleImpl>.rename(newName, notifyStorage)
  }

  override fun registerComponents(plugins: List<IdeaPluginDescriptorImpl>,
                                  notifyListeners: Boolean) {
    super.registerComponents(plugins, false)

    val pluginId = PluginManager.getPluginOrPlatformByClassName(javaClass.name)
                   ?: error("Could not find pluginId for class ${javaClass.name}")
    val pluginDescriptor = PluginManagerCore.getPlugin(pluginId)
                           ?: error("Could not find plugin by id: $pluginId")

    registerComponent(ModuleRootManager::class.java, LegacyBridgeModuleRootComponent::class.java, pluginDescriptor, true)

    registerService(LegacyBridgeFilePointerProvider::class.java, LegacyBridgeFilePointerProviderImpl::class.java, pluginDescriptor, false)
    registerService(IComponentStore::class.java, LegacyBridgeModuleStoreImpl::class.java, pluginDescriptor, true)
  }

  override fun getModuleFile(): VirtualFile? = LocalFileSystem.getInstance().findFileByIoFile(File(filePath))

  // TODO It should participate in rename too
  override fun getModuleFilePath(): String = filePath
}