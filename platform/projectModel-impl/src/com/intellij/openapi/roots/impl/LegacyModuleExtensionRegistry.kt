// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.openapi.roots.ModuleExtensionEp
import com.intellij.openapi.roots.ModuleRootManagerEx
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

private val legacyModuleExtensionEp: ExtensionPointName<ModuleExtensionEp> = ExtensionPointName("com.intellij.moduleExtension")

@VisibleForTesting
@ApiStatus.Internal
@Service(Service.Level.APP)
class LegacyModuleExtensionRegistry(coroutineScope: CoroutineScope) {
  init {
    legacyModuleExtensionEp.addChangeListener(coroutineScope) {
      dropRootModelCache()
    }
  }

  private fun dropRootModelCache() {
    ProjectManager.getInstance().openProjects.forEach { project ->
      ModuleManager.getInstance(project).modules.forEach { module ->
        ModuleRootManagerEx.getInstanceEx(module).dropCaches()
      }
    }
  }
  
  fun forEachExtension(consumer: (ModuleExtensionEp) -> Unit) {
    legacyModuleExtensionEp.forEachExtensionSafe(consumer)
  }
  
  @TestOnly
  fun registerModuleExtension(extensionClass: Class<out ModuleExtension>, pluginDescriptor: PluginDescriptor, parentDisposable: Disposable) {
    val extension = ModuleExtensionEp()
    extension.implementationClass = extensionClass.name
    extension.setPluginDescriptor(pluginDescriptor)
    legacyModuleExtensionEp.point.registerExtension(extension, pluginDescriptor, parentDisposable)
  }
}