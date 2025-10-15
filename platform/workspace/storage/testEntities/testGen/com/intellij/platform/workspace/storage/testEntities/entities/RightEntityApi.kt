// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableRightEntity : ModifiableWorkspaceEntity<RightEntity>, ModifiableCompositeBaseEntity<RightEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositeBaseEntity<out CompositeBaseEntity>?
  override var children: List<ModifiableBaseEntity<out BaseEntity>>
  override var parent: ModifiableHeadAbstractionEntity?
}

internal object RightEntityType : EntityType<RightEntity, ModifiableRightEntity>() {
  override val entityClass: Class<RightEntity> get() = RightEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableRightEntity.() -> Unit)? = null,
  ): ModifiableRightEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyRightEntity(
  entity: RightEntity,
  modification: ModifiableRightEntity.() -> Unit,
): RightEntity = modifyEntity(ModifiableRightEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createRightEntity")
fun RightEntity(
  entitySource: EntitySource,
  init: (ModifiableRightEntity.() -> Unit)? = null,
): ModifiableRightEntity = RightEntityType(entitySource, init)
