// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableSourceRootTestEntity : ModifiableWorkspaceEntity<SourceRootTestEntity> {
  override var entitySource: EntitySource
  var data: String
  var contentRoot: ModifiableContentRootTestEntity
}

internal object SourceRootTestEntityType : EntityType<SourceRootTestEntity, ModifiableSourceRootTestEntity>() {
  override val entityClass: Class<SourceRootTestEntity> get() = SourceRootTestEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSourceRootTestEntity.() -> Unit)? = null,
  ): ModifiableSourceRootTestEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySourceRootTestEntity(
  entity: SourceRootTestEntity,
  modification: ModifiableSourceRootTestEntity.() -> Unit,
): SourceRootTestEntity = modifyEntity(ModifiableSourceRootTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSourceRootTestEntity")
fun SourceRootTestEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSourceRootTestEntity.() -> Unit)? = null,
): ModifiableSourceRootTestEntity = SourceRootTestEntityType(data, entitySource, init)
