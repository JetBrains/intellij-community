// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FirstEntityWithPIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface FirstEntityWithPIdBuilder : WorkspaceEntityBuilder<FirstEntityWithPId> {
  override var entitySource: EntitySource
  var data: String
}

internal object FirstEntityWithPIdType : EntityType<FirstEntityWithPId, FirstEntityWithPIdBuilder>() {
  override val entityClass: Class<FirstEntityWithPId> get() = FirstEntityWithPId::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (FirstEntityWithPIdBuilder.() -> Unit)? = null,
  ): FirstEntityWithPIdBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyFirstEntityWithPId(
  entity: FirstEntityWithPId,
  modification: FirstEntityWithPIdBuilder.() -> Unit,
): FirstEntityWithPId = modifyEntity(FirstEntityWithPIdBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createFirstEntityWithPId")
fun FirstEntityWithPId(
  data: String,
  entitySource: EntitySource,
  init: (FirstEntityWithPIdBuilder.() -> Unit)? = null,
): FirstEntityWithPIdBuilder = FirstEntityWithPIdType(data, entitySource, init)
