// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
interface ModifiableChildSourceEntity : ModifiableWorkspaceEntity<ChildSourceEntity> {
  override var entitySource: EntitySource
  var data: String
  var parentEntity: ModifiableSourceEntity
}

internal object ChildSourceEntityType : EntityType<ChildSourceEntity, ModifiableChildSourceEntity>() {
  override val entityClass: Class<ChildSourceEntity> get() = ChildSourceEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableChildSourceEntity.() -> Unit)? = null,
  ): ModifiableChildSourceEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSourceEntity(
  entity: ChildSourceEntity,
  modification: ModifiableChildSourceEntity.() -> Unit,
): ChildSourceEntity = modifyEntity(ModifiableChildSourceEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSourceEntity")
fun ChildSourceEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableChildSourceEntity.() -> Unit)? = null,
): ModifiableChildSourceEntity = ChildSourceEntityType(data, entitySource, init)
