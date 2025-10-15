// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
interface ModifiableSourceEntity : ModifiableWorkspaceEntity<SourceEntity> {
  override var entitySource: EntitySource
  var data: String
  var children: List<ModifiableChildSourceEntity>
}

internal object SourceEntityType : EntityType<SourceEntity, ModifiableSourceEntity>() {
  override val entityClass: Class<SourceEntity> get() = SourceEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableSourceEntity.() -> Unit)? = null,
  ): ModifiableSourceEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySourceEntity(
  entity: SourceEntity,
  modification: ModifiableSourceEntity.() -> Unit,
): SourceEntity = modifyEntity(ModifiableSourceEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSourceEntity")
fun SourceEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableSourceEntity.() -> Unit)? = null,
): ModifiableSourceEntity = SourceEntityType(data, entitySource, init)
