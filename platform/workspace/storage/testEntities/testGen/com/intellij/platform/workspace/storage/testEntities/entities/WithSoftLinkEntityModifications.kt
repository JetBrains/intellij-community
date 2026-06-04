// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WithSoftLinkEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.WithSoftLinkEntityImpl

@GeneratedCodeApiVersion(3)
interface WithSoftLinkEntityBuilder : WorkspaceEntityBuilder<WithSoftLinkEntity> {
  override var entitySource: EntitySource
  var link: NameId
}

internal object WithSoftLinkEntityType : EntityType<WithSoftLinkEntity, WithSoftLinkEntityBuilder>() {
  override val entityClass: Class<WithSoftLinkEntity> get() = WithSoftLinkEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = WithSoftLinkEntityImpl.Builder::class.java
  operator fun invoke(
    link: NameId,
    entitySource: EntitySource,
    init: (WithSoftLinkEntityBuilder.() -> Unit)? = null,
  ): WithSoftLinkEntityBuilder {
    val builder = builder()
    builder.link = link
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyWithSoftLinkEntity(
  entity: WithSoftLinkEntity,
  modification: WithSoftLinkEntityBuilder.() -> Unit,
): WithSoftLinkEntity = modifyEntity(WithSoftLinkEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createWithSoftLinkEntity")
fun WithSoftLinkEntity(
  link: NameId,
  entitySource: EntitySource,
  init: (WithSoftLinkEntityBuilder.() -> Unit)? = null,
): WithSoftLinkEntityBuilder = WithSoftLinkEntityType(link, entitySource, init)
