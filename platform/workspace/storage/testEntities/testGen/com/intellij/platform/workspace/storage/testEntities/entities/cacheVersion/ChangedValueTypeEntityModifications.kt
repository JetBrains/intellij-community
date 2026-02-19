// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChangedValueTypeEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ChangedValueTypeEntityBuilder : WorkspaceEntityBuilder<ChangedValueTypeEntity> {
  override var entitySource: EntitySource
  var type: String
  var someKey: Int
  var text: MutableList<String>
}

internal object ChangedValueTypeEntityType : EntityType<ChangedValueTypeEntity, ChangedValueTypeEntityBuilder>() {
  override val entityClass: Class<ChangedValueTypeEntity> get() = ChangedValueTypeEntity::class.java
  operator fun invoke(
    type: String,
    someKey: Int,
    text: List<String>,
    entitySource: EntitySource,
    init: (ChangedValueTypeEntityBuilder.() -> Unit)? = null,
  ): ChangedValueTypeEntityBuilder {
    val builder = builder()
    builder.type = type
    builder.someKey = someKey
    builder.text = text.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedValueTypeEntity(
  entity: ChangedValueTypeEntity,
  modification: ChangedValueTypeEntityBuilder.() -> Unit,
): ChangedValueTypeEntity = modifyEntity(ChangedValueTypeEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedValueTypeEntity")
fun ChangedValueTypeEntity(
  type: String,
  someKey: Int,
  text: List<String>,
  entitySource: EntitySource,
  init: (ChangedValueTypeEntityBuilder.() -> Unit)? = null,
): ChangedValueTypeEntityBuilder = ChangedValueTypeEntityType(type, someKey, text, entitySource, init)
