// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableNotNullToNullEntity : ModifiableWorkspaceEntity<NotNullToNullEntity> {
  override var entitySource: EntitySource
  var nullInt: Int?
  var notNullString: String
  var notNullList: MutableList<Int>
}

internal object NotNullToNullEntityType : EntityType<NotNullToNullEntity, ModifiableNotNullToNullEntity>() {
  override val entityClass: Class<NotNullToNullEntity> get() = NotNullToNullEntity::class.java
  operator fun invoke(
    notNullString: String,
    notNullList: List<Int>,
    entitySource: EntitySource,
    init: (ModifiableNotNullToNullEntity.() -> Unit)? = null,
  ): ModifiableNotNullToNullEntity {
    val builder = builder()
    builder.notNullString = notNullString
    builder.notNullList = notNullList.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNotNullToNullEntity(
  entity: NotNullToNullEntity,
  modification: ModifiableNotNullToNullEntity.() -> Unit,
): NotNullToNullEntity = modifyEntity(ModifiableNotNullToNullEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createNotNullToNullEntity")
fun NotNullToNullEntity(
  notNullString: String,
  notNullList: List<Int>,
  entitySource: EntitySource,
  init: (ModifiableNotNullToNullEntity.() -> Unit)? = null,
): ModifiableNotNullToNullEntity = NotNullToNullEntityType(notNullString, notNullList, entitySource, init)
