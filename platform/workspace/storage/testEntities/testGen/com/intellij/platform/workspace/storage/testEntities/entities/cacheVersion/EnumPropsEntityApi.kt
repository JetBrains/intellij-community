// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableEnumPropsEntity : ModifiableWorkspaceEntity<EnumPropsEntity> {
  override var entitySource: EntitySource
  var someEnum: EnumPropsEnum
}

internal object EnumPropsEntityType : EntityType<EnumPropsEntity, ModifiableEnumPropsEntity>() {
  override val entityClass: Class<EnumPropsEntity> get() = EnumPropsEntity::class.java
  operator fun invoke(
    someEnum: EnumPropsEnum,
    entitySource: EntitySource,
    init: (ModifiableEnumPropsEntity.() -> Unit)? = null,
  ): ModifiableEnumPropsEntity {
    val builder = builder()
    builder.someEnum = someEnum
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEnumPropsEntity(
  entity: EnumPropsEntity,
  modification: ModifiableEnumPropsEntity.() -> Unit,
): EnumPropsEntity = modifyEntity(ModifiableEnumPropsEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createEnumPropsEntity")
fun EnumPropsEntity(
  someEnum: EnumPropsEnum,
  entitySource: EntitySource,
  init: (ModifiableEnumPropsEntity.() -> Unit)? = null,
): ModifiableEnumPropsEntity = EnumPropsEntityType(someEnum, entitySource, init)
