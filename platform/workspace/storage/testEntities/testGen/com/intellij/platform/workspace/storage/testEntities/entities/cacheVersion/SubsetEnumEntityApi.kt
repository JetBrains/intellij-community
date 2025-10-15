// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableSubsetEnumEntity : ModifiableWorkspaceEntity<SubsetEnumEntity> {
  override var entitySource: EntitySource
  var someEnum: SubsetEnumEnum
}

internal object SubsetEnumEntityType : EntityType<SubsetEnumEntity, ModifiableSubsetEnumEntity>() {
  override val entityClass: Class<SubsetEnumEntity> get() = SubsetEnumEntity::class.java
  operator fun invoke(
    someEnum: SubsetEnumEnum,
    entitySource: EntitySource,
    init: (ModifiableSubsetEnumEntity.() -> Unit)? = null,
  ): ModifiableSubsetEnumEntity {
    val builder = builder()
    builder.someEnum = someEnum
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySubsetEnumEntity(
  entity: SubsetEnumEntity,
  modification: ModifiableSubsetEnumEntity.() -> Unit,
): SubsetEnumEntity = modifyEntity(ModifiableSubsetEnumEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSubsetEnumEntity")
fun SubsetEnumEntity(
  someEnum: SubsetEnumEnum,
  entitySource: EntitySource,
  init: (ModifiableSubsetEnumEntity.() -> Unit)? = null,
): ModifiableSubsetEnumEntity = SubsetEnumEntityType(someEnum, entitySource, init)
