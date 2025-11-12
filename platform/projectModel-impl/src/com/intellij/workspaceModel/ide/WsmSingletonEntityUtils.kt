// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object WsmSingletonEntityUtils {
  private val LOG: Logger = thisLogger()

  fun <E : WorkspaceEntity> getSingleEntity(storage: EntityStorage, entityClass: Class<E>): E? {
    val entities = storage.entities(entityClass).toList()
    if (entities.size > 1) {
      val entList = entities.map {
        when (it) {
          is ProjectSettingsEntity -> "${it.projectSdk},source=${it.entitySource}"
          is JavaProjectSettingsEntity -> "${it.languageLevelId},${it.languageLevelDefault},${it.compilerOutput},source=${it.entitySource}"
          else -> "${it},source=${it.entitySource}"
        }
      }
      LOG.error(
        "There were several entities of type $entityClass while only one was expected. " +
        "Entities: $entList"
      )
    }
    return entities.firstOrNull()
  }

  fun <E : WorkspaceEntity, B : WorkspaceEntity.Builder<E>> getOrAddSingleEntity(
    mutableStorage: MutableEntityStorage,
    entityClass: Class<E>,
    newEntityBuilder: () -> B,
  ): E {
    val entity = getSingleEntity(mutableStorage, entityClass)
    if (entity != null) {
      return entity
    }
    else {
      val newEntity = newEntityBuilder()
      return mutableStorage.addEntity(newEntity)
    }
  }

  fun <E : WorkspaceEntity, B : WorkspaceEntity.Builder<E>> addOrModifySingleEntity(
    mutableStorage: MutableEntityStorage,
    entityClass: Class<E>, entityBuilderClass: Class<B>, newEntityBuilder: () -> B,
    modFunction: (B) -> Unit,
  ) {
    val entity = getSingleEntity(mutableStorage, entityClass)
    if (entity != null) {
      mutableStorage.modifyEntity(entityBuilderClass, entity) {
        modFunction(this)
      }
    }
    else {
      val newEntity = newEntityBuilder()
      modFunction(newEntity)
      mutableStorage.addEntity(newEntity)
    }
  }
}