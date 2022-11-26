// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.workspaceModel.ide.WorkspaceModelPreUpdateHandler
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId

class ModulePreUpdateHandler : WorkspaceModelPreUpdateHandler {
  override fun update(before: EntityStorage, builder: MutableEntityStorage): Boolean {
    // TODO: 21.12.2020 We need an api to find removed modules faster
    val changes = builder.collectChanges(before)

    val removedModuleSymbolicIds = LinkedHashSet<ModuleId>()
    changes[ModuleEntity::class.java]?.asSequence()?.forEach { change ->
      when (change) {
        is EntityChange.Added -> removedModuleSymbolicIds.remove((change.entity as ModuleEntity).symbolicId)
        is EntityChange.Removed -> removedModuleSymbolicIds.add((change.entity as ModuleEntity).symbolicId)
        else -> {
        }
      }
    }

    if (removedModuleSymbolicIds.isEmpty()) return false

    val librariesToRemove = builder
      .entities(LibraryEntity::class.java)
      .filter { lib -> lib.tableId.let { it is LibraryTableId.ModuleLibraryTableId && it.moduleId in removedModuleSymbolicIds } }
      .toList()

    if (librariesToRemove.isEmpty()) return false

    librariesToRemove.forEach(builder::removeEntity)

    return true
  }
}
