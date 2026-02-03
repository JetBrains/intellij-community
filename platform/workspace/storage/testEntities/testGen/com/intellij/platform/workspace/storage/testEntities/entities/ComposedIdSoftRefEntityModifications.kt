// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ComposedIdSoftRefEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ComposedIdSoftRefEntityBuilder : WorkspaceEntityBuilder<ComposedIdSoftRefEntity> {
  override var entitySource: EntitySource
  var myName: String
  var link: NameId
}

internal object ComposedIdSoftRefEntityType : EntityType<ComposedIdSoftRefEntity, ComposedIdSoftRefEntityBuilder>() {
  override val entityClass: Class<ComposedIdSoftRefEntity> get() = ComposedIdSoftRefEntity::class.java
  operator fun invoke(
    myName: String,
    link: NameId,
    entitySource: EntitySource,
    init: (ComposedIdSoftRefEntityBuilder.() -> Unit)? = null,
  ): ComposedIdSoftRefEntityBuilder {
    val builder = builder()
    builder.myName = myName
    builder.link = link
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyComposedIdSoftRefEntity(
  entity: ComposedIdSoftRefEntity,
  modification: ComposedIdSoftRefEntityBuilder.() -> Unit,
): ComposedIdSoftRefEntity = modifyEntity(ComposedIdSoftRefEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createComposedIdSoftRefEntity")
fun ComposedIdSoftRefEntity(
  myName: String,
  link: NameId,
  entitySource: EntitySource,
  init: (ComposedIdSoftRefEntityBuilder.() -> Unit)? = null,
): ComposedIdSoftRefEntityBuilder = ComposedIdSoftRefEntityType(myName, link, entitySource, init)
