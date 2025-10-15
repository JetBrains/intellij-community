// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Open

@GeneratedCodeApiVersion(3)
interface ModifiableSubsetSealedClassEntity : ModifiableWorkspaceEntity<SubsetSealedClassEntity> {
  override var entitySource: EntitySource
  var someData: SubsetSealedClass
}

internal object SubsetSealedClassEntityType : EntityType<SubsetSealedClassEntity, ModifiableSubsetSealedClassEntity>() {
  override val entityClass: Class<SubsetSealedClassEntity> get() = SubsetSealedClassEntity::class.java
  operator fun invoke(
    someData: SubsetSealedClass,
    entitySource: EntitySource,
    init: (ModifiableSubsetSealedClassEntity.() -> Unit)? = null,
  ): ModifiableSubsetSealedClassEntity {
    val builder = builder()
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySubsetSealedClassEntity(
  entity: SubsetSealedClassEntity,
  modification: ModifiableSubsetSealedClassEntity.() -> Unit,
): SubsetSealedClassEntity = modifyEntity(ModifiableSubsetSealedClassEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSubsetSealedClassEntity")
fun SubsetSealedClassEntity(
  someData: SubsetSealedClass,
  entitySource: EntitySource,
  init: (ModifiableSubsetSealedClassEntity.() -> Unit)? = null,
): ModifiableSubsetSealedClassEntity = SubsetSealedClassEntityType(someData, entitySource, init)
