// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.util.containers.Interner
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

class UnloadedModuleDescriptionBridge private constructor(
  private val name: String,
  private val dependencyModuleNames: List<String>,
  private val contentRoots: List<VirtualFilePointer>,
  private val groupPath: List<String>
) : UnloadedModuleDescription {
  override fun getName(): String = name

  override fun getDependencyModuleNames(): List<String> = dependencyModuleNames

  override fun getContentRoots(): List<VirtualFilePointer> = contentRoots

  override fun getGroupPath(): List<String> = groupPath

  companion object {
    fun createDescriptions(entities: List<ModuleEntity>): List<UnloadedModuleDescription> {
      val interner = Interner.createStringInterner()
      return entities.map { entity -> create(entity, interner) }
    }

    fun createDescription(entity: ModuleEntity): UnloadedModuleDescription = create(entity, Interner.createStringInterner())

    private fun create(entity: ModuleEntity, interner: Interner<String>): UnloadedModuleDescriptionBridge {
      val contentRoots = entity.contentRoots.sortedBy { contentEntry -> contentEntry.url.url }
        .mapTo(ArrayList()) { contentEntry -> contentEntry.url as VirtualFileUrlBridge }
      val dependencyModuleNames = entity.dependencies.filterIsInstance(ModuleDependencyItem.Exportable.ModuleDependency::class.java)
        .map { moduleDependency -> interner.intern(moduleDependency.module.name) }
      return UnloadedModuleDescriptionBridge(entity.name, dependencyModuleNames, contentRoots, entity.groupPath?.path ?: emptyList())
    }
  }
}