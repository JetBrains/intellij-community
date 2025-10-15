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
interface ModifiableChildSingleFirstEntity : ModifiableWorkspaceEntity<ChildSingleFirstEntity>, ModifiableChildSingleAbstractBaseEntity<ChildSingleFirstEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ModifiableParentSingleAbEntity
  var firstData: String
}

internal object ChildSingleFirstEntityType : EntityType<ChildSingleFirstEntity, ModifiableChildSingleFirstEntity>() {
  override val entityClass: Class<ChildSingleFirstEntity> get() = ChildSingleFirstEntity::class.java
  operator fun invoke(
    commonData: String,
    firstData: String,
    entitySource: EntitySource,
    init: (ModifiableChildSingleFirstEntity.() -> Unit)? = null,
  ): ModifiableChildSingleFirstEntity {
    val builder = builder()
    builder.commonData = commonData
    builder.firstData = firstData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildSingleFirstEntity(
  entity: ChildSingleFirstEntity,
  modification: ModifiableChildSingleFirstEntity.() -> Unit,
): ChildSingleFirstEntity = modifyEntity(ModifiableChildSingleFirstEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSingleFirstEntity")
fun ChildSingleFirstEntity(
  commonData: String,
  firstData: String,
  entitySource: EntitySource,
  init: (ModifiableChildSingleFirstEntity.() -> Unit)? = null,
): ModifiableChildSingleFirstEntity = ChildSingleFirstEntityType(commonData, firstData, entitySource, init)
