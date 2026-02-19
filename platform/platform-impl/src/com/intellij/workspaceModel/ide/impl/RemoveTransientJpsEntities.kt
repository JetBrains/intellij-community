// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.backend.workspace.WorkspaceModelSerializerHook
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.NonPersistentEntitySource

class RemoveTransientJpsEntities : WorkspaceModelSerializerHook {
  override fun beforeSerialization(entityStorage: ImmutableEntityStorage): ImmutableEntityStorage {
      val builder = MutableEntityStorage.from(entityStorage)
      val nonPersistentModules = builder.entities(ModuleEntity::class.java)
        .filter { it.entitySource == NonPersistentEntitySource }
        .toList()
      nonPersistentModules.forEach {
        builder.removeEntity(it)
      }
      return builder.toSnapshot()
  }
}