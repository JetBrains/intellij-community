// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableStringEntity : ModifiableWorkspaceEntity<StringEntity> {
  override var entitySource: EntitySource
  var data: String
}

internal object StringEntityType : EntityType<StringEntity, ModifiableStringEntity>() {
  override val entityClass: Class<StringEntity> get() = StringEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ModifiableStringEntity.() -> Unit)? = null,
  ): ModifiableStringEntity {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyStringEntity(
  entity: StringEntity,
  modification: ModifiableStringEntity.() -> Unit,
): StringEntity = modifyEntity(ModifiableStringEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createStringEntity")
fun StringEntity(
  data: String,
  entitySource: EntitySource,
  init: (ModifiableStringEntity.() -> Unit)? = null,
): ModifiableStringEntity = StringEntityType(data, entitySource, init)
