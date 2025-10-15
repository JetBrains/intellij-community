// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableHeadAbstractionEntity : ModifiableWorkspaceEntity<HeadAbstractionEntity> {
  override var entitySource: EntitySource
  var data: String
  var child: ModifiableCompositeBaseEntity<out CompositeBaseEntity>?
}

internal object HeadAbstractionEntityType : EntityType<HeadAbstractionEntity, ModifiableHeadAbstractionEntity>() {
  override val entityClass: Class<HeadAbstractionEntity> get() = HeadAbstractionEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableHeadAbstractionEntity.() -> Unit)? = null,
  ): ModifiableHeadAbstractionEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyHeadAbstractionEntity(
  entity: HeadAbstractionEntity,
  modification: ModifiableHeadAbstractionEntity.() -> Unit,
): HeadAbstractionEntity = modifyEntity(ModifiableHeadAbstractionEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createHeadAbstractionEntity")
fun HeadAbstractionEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableHeadAbstractionEntity.() -> Unit)? = null,
): ModifiableHeadAbstractionEntity = HeadAbstractionEntityType(data, entitySource, init)
