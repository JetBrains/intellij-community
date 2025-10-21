// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SecondEntityWithPIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface SecondEntityWithPIdBuilder : WorkspaceEntityBuilder<SecondEntityWithPId> {
  override var entitySource: EntitySource
  var data: String
}

internal object SecondEntityWithPIdType : EntityType<SecondEntityWithPId, SecondEntityWithPIdBuilder>() {
  override val entityClass: Class<SecondEntityWithPId> get() = SecondEntityWithPId::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (SecondEntityWithPIdBuilder.() -> Unit)? = null,
  ): SecondEntityWithPIdBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySecondEntityWithPId(
  entity: SecondEntityWithPId,
  modification: SecondEntityWithPIdBuilder.() -> Unit,
): SecondEntityWithPId = modifyEntity(SecondEntityWithPIdBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSecondEntityWithPId")
fun SecondEntityWithPId(
  data: String,
  entitySource: EntitySource,
  init: (SecondEntityWithPIdBuilder.() -> Unit)? = null,
): SecondEntityWithPIdBuilder = SecondEntityWithPIdType(data, entitySource, init)
