// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableSourceRootTestOrderEntity : ModifiableWorkspaceEntity<SourceRootTestOrderEntity> {
  override var entitySource: EntitySource
  var data: String
  var contentRoot: ModifiableContentRootTestEntity
}

internal object SourceRootTestOrderEntityType : EntityType<SourceRootTestOrderEntity, ModifiableSourceRootTestOrderEntity>() {
  override val entityClass: Class<SourceRootTestOrderEntity> get() = SourceRootTestOrderEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSourceRootTestOrderEntity.() -> Unit)? = null,
  ): ModifiableSourceRootTestOrderEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySourceRootTestOrderEntity(
  entity: SourceRootTestOrderEntity,
  modification: ModifiableSourceRootTestOrderEntity.() -> Unit,
): SourceRootTestOrderEntity = modifyEntity(ModifiableSourceRootTestOrderEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSourceRootTestOrderEntity")
fun SourceRootTestOrderEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSourceRootTestOrderEntity.() -> Unit)? = null,
): ModifiableSourceRootTestOrderEntity = SourceRootTestOrderEntityType(data, entitySource, init)
