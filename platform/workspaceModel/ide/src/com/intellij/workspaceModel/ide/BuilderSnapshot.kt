// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.ConsistencyCheckingMode
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class StorageReplacement internal constructor(
  val version: Long,
  val snapshot: WorkspaceEntityStorage,
  val changes: Map<Class<*>, List<EntityChange<*>>>
)

class BuilderSnapshot(val version: Long, private val storage: WorkspaceEntityStorage) {
  val builder: WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilder.from(storage)

  /**
   * It's suggested to call this method WITHOUT write locks or anything
   */
  fun getStorageReplacement(): StorageReplacement {
    val changes = builder.collectChanges(storage)
    val newStorage = builder.toStorage()
    return StorageReplacement(version, newStorage, changes)
  }
}

fun ConsistencyCheckingMode.Companion.defaultIde() = default(ApplicationManager.getApplication().isEAP)