// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableMiddleEntity : ModifiableWorkspaceEntity<MiddleEntity>, ModifiableBaseEntity<MiddleEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositeBaseEntity<out CompositeBaseEntity>?
  var property: String
}

internal object MiddleEntityType : EntityType<MiddleEntity, ModifiableMiddleEntity>() {
  override val entityClass: Class<MiddleEntity> get() = MiddleEntity::class.java
  operator fun invoke(
    property: String,
    entitySource: EntitySource,
    init: (ModifiableMiddleEntity.() -> Unit)? = null,
  ): ModifiableMiddleEntity {
    val builder = builder()
    builder.property = property
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMiddleEntity(
  entity: MiddleEntity,
  modification: ModifiableMiddleEntity.() -> Unit,
): MiddleEntity = modifyEntity(ModifiableMiddleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createMiddleEntity")
fun MiddleEntity(
  property: String,
  entitySource: EntitySource,
  init: (ModifiableMiddleEntity.() -> Unit)? = null,
): ModifiableMiddleEntity = MiddleEntityType(property, entitySource, init)
