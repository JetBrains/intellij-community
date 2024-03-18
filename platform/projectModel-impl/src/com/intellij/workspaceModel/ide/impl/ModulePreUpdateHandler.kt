// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.backend.workspace.WorkspaceModelPreUpdateHandler
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation

class ModulePreUpdateHandler : WorkspaceModelPreUpdateHandler {
  @OptIn(EntityStorageInstrumentationApi::class)
  override fun update(before: EntityStorage, builder: MutableEntityStorage): Boolean {
    // TODO: 21.12.2020 We need an api to find removed modules faster
    val changes = (builder as MutableEntityStorageInstrumentation).collectChanges()

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
