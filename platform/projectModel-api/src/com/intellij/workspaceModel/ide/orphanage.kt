// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
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
    val targetModules = event.getChanges(ModuleEntity::class.java)
      .filterIsInstance<EntityChange.Added<ModuleEntity>>()
      .map { it.entity }

    val orphanageSnapshot = project.workspaceModel.orphanage.entityStorage.pointer.storage
    if (targetModules.isNotEmpty() && targetModules.any { orphanageSnapshot.resolve(it.symbolicId) != null }) {
      runLaterAndWrite {
        val orphanage = project.workspaceModel.orphanage.entityStorage.pointer.storage
        val newRoots = targetModules
          .mapNotNull { orphanage.resolve(it.symbolicId) }
          .filter { it.contentRoots.isNotEmpty() }
          .associateWith { it.contentRoots.map { it.createEntityTreeCopy() as ContentRootEntity.Builder } }

        if (newRoots.isNotEmpty()) {
          project.workspaceModel.updateProjectModel("Move orphan elements") {
            newRoots.forEach { (module, roots) ->
              val localModule = it.resolve(module.symbolicId) ?: return@forEach
              it.modifyEntity(localModule) {
                this.contentRoots += roots
              }
            }
          }

          project.workspaceModel.orphanage.update { mutableOrphanage ->
            newRoots.forEach { mutableOrphanage.removeEntity(it.key) }
          }
        }
      }
    }
  }

  private inline fun runLaterAndWrite(crossinline run: () -> Unit) {
    // This is important to use invokeLater in order not to update the project model inside the listeners
    ApplicationManager.getApplication().invokeLater {
      ApplicationManager.getApplication().runWriteAction {
        run()
      }
    }
  }
}
