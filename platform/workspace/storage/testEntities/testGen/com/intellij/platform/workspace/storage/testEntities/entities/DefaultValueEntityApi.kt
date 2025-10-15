// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default

@GeneratedCodeApiVersion(3)
interface ModifiableDefaultValueEntity : ModifiableWorkspaceEntity<DefaultValueEntity> {
  override var entitySource: EntitySource
  var name: String
  var isGenerated: Boolean
  var anotherName: String
}

internal object DefaultValueEntityType : EntityType<DefaultValueEntity, ModifiableDefaultValueEntity>() {
  override val entityClass: Class<DefaultValueEntity> get() = DefaultValueEntity::class.java
  operator fun invoke(
    name: String,
    entitySource: EntitySource,
    init: (ModifiableDefaultValueEntity.() -> Unit)? = null,
  ): ModifiableDefaultValueEntity {
    val builder = builder()
    builder.name = name
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyDefaultValueEntity(
  entity: DefaultValueEntity,
  modification: ModifiableDefaultValueEntity.() -> Unit,
): DefaultValueEntity = modifyEntity(ModifiableDefaultValueEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createDefaultValueEntity")
fun DefaultValueEntity(
  name: String,
  entitySource: EntitySource,
  init: (ModifiableDefaultValueEntity.() -> Unit)? = null,
): ModifiableDefaultValueEntity = DefaultValueEntityType(name, entitySource, init)
