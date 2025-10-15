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
interface ModifiableComputablePropEntity : ModifiableWorkspaceEntity<ComputablePropEntity> {
  override var entitySource: EntitySource
  var list: MutableList<Map<List<Int?>, String>>
  var value: Int
}

internal object ComputablePropEntityType : EntityType<ComputablePropEntity, ModifiableComputablePropEntity>() {
  override val entityClass: Class<ComputablePropEntity> get() = ComputablePropEntity::class.java
  operator fun invoke(
    list: List<Map<List<Int?>, String>>,
    value: Int,
    entitySource: EntitySource,
    init: (ModifiableComputablePropEntity.() -> Unit)? = null,
  ): ModifiableComputablePropEntity {
    val builder = builder()
    builder.list = list.toMutableWorkspaceList()
    builder.value = value
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyComputablePropEntity(
  entity: ComputablePropEntity,
  modification: ModifiableComputablePropEntity.() -> Unit,
): ComputablePropEntity = modifyEntity(ModifiableComputablePropEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createComputablePropEntity")
fun ComputablePropEntity(
  list: List<Map<List<Int?>, String>>,
  value: Int,
  entitySource: EntitySource,
  init: (ModifiableComputablePropEntity.() -> Unit)? = null,
): ModifiableComputablePropEntity = ComputablePropEntityType(list, value, entitySource, init)
