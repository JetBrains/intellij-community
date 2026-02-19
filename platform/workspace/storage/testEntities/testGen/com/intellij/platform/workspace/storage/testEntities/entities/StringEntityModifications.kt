// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StringEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface StringEntityBuilder : WorkspaceEntityBuilder<StringEntity> {
  override var entitySource: EntitySource
  var data: String
}

internal object StringEntityType : EntityType<StringEntity, StringEntityBuilder>() {
  override val entityClass: Class<StringEntity> get() = StringEntity::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (StringEntityBuilder.() -> Unit)? = null,
  ): StringEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyStringEntity(
  entity: StringEntity,
  modification: StringEntityBuilder.() -> Unit,
): StringEntity = modifyEntity(StringEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createStringEntity")
fun StringEntity(
  data: String,
  entitySource: EntitySource,
  init: (StringEntityBuilder.() -> Unit)? = null,
): StringEntityBuilder = StringEntityType(data, entitySource, init)
