// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
interface ModifiableChildSampleEntity : ModifiableWorkspaceEntity<ChildSampleEntity> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: ModifiableSampleEntity?
}

internal object ChildSampleEntityType : EntityType<ChildSampleEntity, ModifiableChildSampleEntity>() {
  override val entityClass: Class<ChildSampleEntity> get() = ChildSampleEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableChildSampleEntity.() -> Unit)? = null,
  ): ModifiableChildSampleEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSampleEntity(
  entity: ChildSampleEntity,
  modification: ModifiableChildSampleEntity.() -> Unit,
): ChildSampleEntity = modifyEntity(ModifiableChildSampleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSampleEntity")
fun ChildSampleEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableChildSampleEntity.() -> Unit)? = null,
): ModifiableChildSampleEntity = ChildSampleEntityType(data, entitySource, init)
