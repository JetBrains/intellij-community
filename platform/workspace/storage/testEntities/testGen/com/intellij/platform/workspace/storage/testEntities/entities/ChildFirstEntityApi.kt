// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ModifiableChildFirstEntity : ModifiableWorkspaceEntity<ChildFirstEntity>, ModifiableChildAbstractBaseEntity<ChildFirstEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ModifiableParentAbEntity
  var firstData: String
}

internal object ChildFirstEntityType : EntityType<ChildFirstEntity, ModifiableChildFirstEntity>() {
  override val entityClass: Class<ChildFirstEntity> get() = ChildFirstEntity::class.java
  operator fun invoke(
    commonData: String,
    firstData: String,
    entitySource: EntitySource,
    init: (ModifiableChildFirstEntity.() -> Unit)? = null,
  ): ModifiableChildFirstEntity {
    val builder = builder()
    builder.commonData = commonData
    builder.firstData = firstData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildFirstEntity(
  entity: ChildFirstEntity,
  modification: ModifiableChildFirstEntity.() -> Unit,
): ChildFirstEntity = modifyEntity(ModifiableChildFirstEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildFirstEntity")
fun ChildFirstEntity(
  commonData: String,
  firstData: String,
  entitySource: EntitySource,
  init: (ModifiableChildFirstEntity.() -> Unit)? = null,
): ModifiableChildFirstEntity = ChildFirstEntityType(commonData, firstData, entitySource, init)
