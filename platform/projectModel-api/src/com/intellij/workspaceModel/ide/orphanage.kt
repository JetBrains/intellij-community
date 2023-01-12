// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl


class Orphanage {
  val entityStorage: VersionedEntityStorageImpl = VersionedEntityStorageImpl(EntityStorageSnapshot.empty())

  fun update(updater: (MutableEntityStorage) -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val before = entityStorage.current
    val builder = MutableEntityStorage.from(before)

    updater(builder)

    val newStorage: EntityStorageSnapshot = builder.toSnapshot()
    entityStorage.replace(newStorage, emptyMap(), {}, {})
  }
}

class OrphanListener(val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    //val modules = event.getChanges(ModuleEntity::class.java)
    //  .filterIsInstance<EntityChange.Added<ModuleEntity>>()
    //  .mapNotNull {
    //    project.workspaceModel.currentSnapshot.resolve(it.entity.symbolicId)
    //  }
    //
    //// This is important to use invokeLater in order not to update the project model inside of the listeners
    //ApplicationManager.getApplication().invokeLater {
    //  ApplicationManager.getApplication().runWriteAction {
    //    WorkspaceModel.getInstance(project).updateProjectModel("") {
    //      for (module in modules) {
    //        it.modifyEntity(module) {
    //          // TODO: Implement copy with parents
    //        }
    //      }
    //    }
    //  }
    //}
  }
}
