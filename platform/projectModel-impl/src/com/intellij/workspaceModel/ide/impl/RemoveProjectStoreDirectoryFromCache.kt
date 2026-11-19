// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.backend.workspace.WorkspaceModelSerializerHook
import com.intellij.platform.workspace.jps.entities.ProjectStoreDirectoryEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities

internal class RemoveProjectStoreDirectoryFromCache : WorkspaceModelSerializerHook {
  override fun beforeSerialization(entityStorage: ImmutableEntityStorage): ImmutableEntityStorage {
    val directories = entityStorage.entities<ProjectStoreDirectoryEntity>().toList()
    if (directories.isEmpty()) return entityStorage

    val builder = MutableEntityStorage.from(entityStorage)
    directories.forEach { builder.removeEntity(it) }
    return builder.toSnapshot()
  }
}
