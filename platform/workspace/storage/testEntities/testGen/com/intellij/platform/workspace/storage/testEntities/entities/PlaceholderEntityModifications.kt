// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PlaceholderEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface PlaceholderEntityBuilder : WorkspaceEntityBuilder<PlaceholderEntity> {
  override var entitySource: EntitySource
  var myId: String
}

internal object PlaceholderEntityType : EntityType<PlaceholderEntity, PlaceholderEntityBuilder>() {
  override val entityClass: Class<PlaceholderEntity> get() = PlaceholderEntity::class.java
  operator fun invoke(
    myId: String,
    entitySource: EntitySource,
    init: (PlaceholderEntityBuilder.() -> Unit)? = null,
  ): PlaceholderEntityBuilder {
    val builder = builder()
    builder.myId = myId
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyPlaceholderEntity(
  entity: PlaceholderEntity,
  modification: PlaceholderEntityBuilder.() -> Unit,
): PlaceholderEntity = modifyEntity(PlaceholderEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createPlaceholderEntity")
fun PlaceholderEntity(
  myId: String,
  entitySource: EntitySource,
  init: (PlaceholderEntityBuilder.() -> Unit)? = null,
): PlaceholderEntityBuilder = PlaceholderEntityType(myId, entitySource, init)
