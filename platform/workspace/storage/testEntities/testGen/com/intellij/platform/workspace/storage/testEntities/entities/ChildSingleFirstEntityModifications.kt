// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildSingleFirstEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ChildSingleFirstEntityBuilder : WorkspaceEntityBuilder<ChildSingleFirstEntity>, ChildSingleAbstractBaseEntityBuilder<ChildSingleFirstEntity> {
  override var entitySource: EntitySource
  override var commonData: String
  override var parentEntity: ParentSingleAbEntityBuilder
  var firstData: String
}

internal object ChildSingleFirstEntityType : EntityType<ChildSingleFirstEntity, ChildSingleFirstEntityBuilder>() {
  override val entityClass: Class<ChildSingleFirstEntity> get() = ChildSingleFirstEntity::class.java
  operator fun invoke(
    commonData: String,
    firstData: String,
    entitySource: EntitySource,
    init: (ChildSingleFirstEntityBuilder.() -> Unit)? = null,
  ): ChildSingleFirstEntityBuilder {
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
  modification: ChildSingleFirstEntityBuilder.() -> Unit,
): ChildSingleFirstEntity = modifyEntity(ChildSingleFirstEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildSingleFirstEntity")
fun ChildSingleFirstEntity(
  commonData: String,
  firstData: String,
  entitySource: EntitySource,
  init: (ChildSingleFirstEntityBuilder.() -> Unit)? = null,
): ChildSingleFirstEntityBuilder = ChildSingleFirstEntityType(commonData, firstData, entitySource, init)
