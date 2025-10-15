// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface ModifiableChangedEnumNameEntity : ModifiableWorkspaceEntity<ChangedEnumNameEntity> {
  override var entitySource: EntitySource
  var someEnum: ChangedEnumNameEnum
}

internal object ChangedEnumNameEntityType : EntityType<ChangedEnumNameEntity, ModifiableChangedEnumNameEntity>() {
  override val entityClass: Class<ChangedEnumNameEntity> get() = ChangedEnumNameEntity::class.java
  operator fun invoke(
    someEnum: ChangedEnumNameEnum,
    entitySource: EntitySource,
    init: (ModifiableChangedEnumNameEntity.() -> Unit)? = null,
  ): ModifiableChangedEnumNameEntity {
    val builder = builder()
    builder.someEnum = someEnum
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedEnumNameEntity(
  entity: ChangedEnumNameEntity,
  modification: ModifiableChangedEnumNameEntity.() -> Unit,
): ChangedEnumNameEntity = modifyEntity(ModifiableChangedEnumNameEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedEnumNameEntity")
fun ChangedEnumNameEntity(
  someEnum: ChangedEnumNameEnum,
  entitySource: EntitySource,
  init: (ModifiableChangedEnumNameEntity.() -> Unit)? = null,
): ModifiableChangedEnumNameEntity = ChangedEnumNameEntityType(someEnum, entitySource, init)
