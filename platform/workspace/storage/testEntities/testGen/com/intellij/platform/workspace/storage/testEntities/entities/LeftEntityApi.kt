// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableLeftEntity : ModifiableWorkspaceEntity<LeftEntity>, ModifiableCompositeBaseEntity<LeftEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositeBaseEntity<out CompositeBaseEntity>?
  override var children: List<ModifiableBaseEntity<out BaseEntity>>
  override var parent: ModifiableHeadAbstractionEntity?
}

internal object LeftEntityType : EntityType<LeftEntity, ModifiableLeftEntity>() {
  override val entityClass: Class<LeftEntity> get() = LeftEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableLeftEntity.() -> Unit)? = null,
  ): ModifiableLeftEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyLeftEntity(
  entity: LeftEntity,
  modification: ModifiableLeftEntity.() -> Unit,
): LeftEntity = modifyEntity(ModifiableLeftEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createLeftEntity")
fun LeftEntity(
  entitySource: EntitySource,
  init: (ModifiableLeftEntity.() -> Unit)? = null,
): ModifiableLeftEntity = LeftEntityType(entitySource, init)
