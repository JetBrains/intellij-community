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
interface ModifiableChildSecondEntity : ModifiableWorkspaceEntity<ChildSecondEntity>, ModifiableChildAbstractBaseEntity<ChildSecondEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ModifiableParentAbEntity
  var secondData: String
}

internal object ChildSecondEntityType : EntityType<ChildSecondEntity, ModifiableChildSecondEntity>() {
  override val entityClass: Class<ChildSecondEntity> get() = ChildSecondEntity::class.java
  operator fun invoke(
    commonData: String,
    secondData: String,
    entitySource: EntitySource,
    init: (ModifiableChildSecondEntity.() -> Unit)? = null,
  ): ModifiableChildSecondEntity {
    val builder = builder()
    builder.commonData = commonData
    builder.secondData = secondData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSecondEntity(
  entity: ChildSecondEntity,
  modification: ModifiableChildSecondEntity.() -> Unit,
): ChildSecondEntity = modifyEntity(ModifiableChildSecondEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSecondEntity")
fun ChildSecondEntity(
  commonData: String,
  secondData: String,
  entitySource: EntitySource,
  init: (ModifiableChildSecondEntity.() -> Unit)? = null,
): ModifiableChildSecondEntity = ChildSecondEntityType(commonData, secondData, entitySource, init)
