// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableNullToNotNullEntity : ModifiableWorkspaceEntity<NullToNotNullEntity> {
  override var entitySource: EntitySource
  var nullString: String
  var notNullBoolean: Boolean
  var notNullInt: Int
}

internal object NullToNotNullEntityType : EntityType<NullToNotNullEntity, ModifiableNullToNotNullEntity>() {
  override val entityClass: Class<NullToNotNullEntity> get() = NullToNotNullEntity::class.java
  operator fun invoke(
    nullString: String,
    notNullBoolean: Boolean,
    notNullInt: Int,
    entitySource: EntitySource,
    init: (ModifiableNullToNotNullEntity.() -> Unit)? = null,
  ): ModifiableNullToNotNullEntity {
    val builder = builder()
    builder.nullString = nullString
    builder.notNullBoolean = notNullBoolean
    builder.notNullInt = notNullInt
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNullToNotNullEntity(
  entity: NullToNotNullEntity,
  modification: ModifiableNullToNotNullEntity.() -> Unit,
): NullToNotNullEntity = modifyEntity(ModifiableNullToNotNullEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createNullToNotNullEntity")
fun NullToNotNullEntity(
  nullString: String,
  notNullBoolean: Boolean,
  notNullInt: Int,
  entitySource: EntitySource,
  init: (ModifiableNullToNotNullEntity.() -> Unit)? = null,
): ModifiableNullToNotNullEntity = NullToNotNullEntityType(nullString, notNullBoolean, notNullInt, entitySource, init)
