// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ComposedLinkEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ComposedLinkEntityBuilder : WorkspaceEntityBuilder<ComposedLinkEntity> {
  override var entitySource: EntitySource
  var link: ComposedId
}

internal object ComposedLinkEntityType : EntityType<ComposedLinkEntity, ComposedLinkEntityBuilder>() {
  override val entityClass: Class<ComposedLinkEntity> get() = ComposedLinkEntity::class.java
  operator fun invoke(
    link: ComposedId,
    entitySource: EntitySource,
    init: (ComposedLinkEntityBuilder.() -> Unit)? = null,
  ): ComposedLinkEntityBuilder {
    val builder = builder()
    builder.link = link
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyComposedLinkEntity(
  entity: ComposedLinkEntity,
  modification: ComposedLinkEntityBuilder.() -> Unit,
): ComposedLinkEntity = modifyEntity(ComposedLinkEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createComposedLinkEntity")
fun ComposedLinkEntity(
  link: ComposedId,
  entitySource: EntitySource,
  init: (ComposedLinkEntityBuilder.() -> Unit)? = null,
): ComposedLinkEntityBuilder = ComposedLinkEntityType(link, entitySource, init)
