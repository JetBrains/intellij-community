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
interface ModifiableChildSingleSecondEntity : ModifiableWorkspaceEntity<ChildSingleSecondEntity>, ModifiableChildSingleAbstractBaseEntity<ChildSingleSecondEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ModifiableParentSingleAbEntity
  var secondData: String
}

internal object ChildSingleSecondEntityType : EntityType<ChildSingleSecondEntity, ModifiableChildSingleSecondEntity>() {
  override val entityClass: Class<ChildSingleSecondEntity> get() = ChildSingleSecondEntity::class.java
  operator fun invoke(
    commonData: String,
    secondData: String,
    entitySource: EntitySource,
    init: (ModifiableChildSingleSecondEntity.() -> Unit)? = null,
  ): ModifiableChildSingleSecondEntity {
    val builder = builder()
    builder.commonData = commonData
    builder.secondData = secondData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSingleSecondEntity(
  entity: ChildSingleSecondEntity,
  modification: ModifiableChildSingleSecondEntity.() -> Unit,
): ChildSingleSecondEntity = modifyEntity(ModifiableChildSingleSecondEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSingleSecondEntity")
fun ChildSingleSecondEntity(
  commonData: String,
  secondData: String,
  entitySource: EntitySource,
  init: (ModifiableChildSingleSecondEntity.() -> Unit)? = null,
): ModifiableChildSingleSecondEntity = ChildSingleSecondEntityType(commonData, secondData, entitySource, init)
