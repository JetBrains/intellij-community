// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OneEntityWithSymbolicIdModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface OneEntityWithSymbolicIdBuilder : WorkspaceEntityBuilder<OneEntityWithSymbolicId> {
  override var entitySource: EntitySource
  var myName: String
}

internal object OneEntityWithSymbolicIdType : EntityType<OneEntityWithSymbolicId, OneEntityWithSymbolicIdBuilder>() {
  override val entityClass: Class<OneEntityWithSymbolicId> get() = OneEntityWithSymbolicId::class.java
  operator fun invoke(
    myName: String,
    entitySource: EntitySource,
    init: (OneEntityWithSymbolicIdBuilder.() -> Unit)? = null,
  ): OneEntityWithSymbolicIdBuilder {
    val builder = builder()
    builder.myName = myName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneEntityWithSymbolicId(
  entity: OneEntityWithSymbolicId,
  modification: OneEntityWithSymbolicIdBuilder.() -> Unit,
): OneEntityWithSymbolicId = modifyEntity(OneEntityWithSymbolicIdBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneEntityWithSymbolicId")
fun OneEntityWithSymbolicId(
  myName: String,
  entitySource: EntitySource,
  init: (OneEntityWithSymbolicIdBuilder.() -> Unit)? = null,
): OneEntityWithSymbolicIdBuilder = OneEntityWithSymbolicIdType(myName, entitySource, init)
