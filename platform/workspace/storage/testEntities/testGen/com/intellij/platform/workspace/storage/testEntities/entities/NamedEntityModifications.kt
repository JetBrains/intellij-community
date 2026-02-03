// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NamedEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface NamedEntityBuilder : WorkspaceEntityBuilder<NamedEntity> {
  override var entitySource: EntitySource
  var myName: String
  var additionalProperty: String?
  var children: List<NamedChildEntityBuilder>
}

internal object NamedEntityType : EntityType<NamedEntity, NamedEntityBuilder>() {
  override val entityClass: Class<NamedEntity> get() = NamedEntity::class.java
  operator fun invoke(
    myName: String,
    entitySource: EntitySource,
    init: (NamedEntityBuilder.() -> Unit)? = null,
  ): NamedEntityBuilder {
    val builder = builder()
    builder.myName = myName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNamedEntity(
  entity: NamedEntity,
  modification: NamedEntityBuilder.() -> Unit,
): NamedEntity = modifyEntity(NamedEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createNamedEntity")
fun NamedEntity(
  myName: String,
  entitySource: EntitySource,
  init: (NamedEntityBuilder.() -> Unit)? = null,
): NamedEntityBuilder = NamedEntityType(myName, entitySource, init)
