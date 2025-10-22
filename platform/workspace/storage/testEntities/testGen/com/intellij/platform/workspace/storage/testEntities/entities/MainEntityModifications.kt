// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MainEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface MainEntityBuilder : WorkspaceEntityBuilder<MainEntity> {
  override var entitySource: EntitySource
  var x: String
}

internal object MainEntityType : EntityType<MainEntity, MainEntityBuilder>() {
  override val entityClass: Class<MainEntity> get() = MainEntity::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (MainEntityBuilder.() -> Unit)? = null,
  ): MainEntityBuilder {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntity(
  entity: MainEntity,
  modification: MainEntityBuilder.() -> Unit,
): MainEntity = modifyEntity(MainEntityBuilder::class.java, entity, modification)

var MainEntityBuilder.child: AttachedEntityBuilder?
  by WorkspaceEntity.extensionBuilder(AttachedEntity::class.java)


@JvmOverloads
@JvmName("createMainEntity")
fun MainEntity(
  x: String,
  entitySource: EntitySource,
  init: (MainEntityBuilder.() -> Unit)? = null,
): MainEntityBuilder = MainEntityType(x, entitySource, init)
