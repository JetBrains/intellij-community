// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WithListSoftLinksEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface WithListSoftLinksEntityBuilder : WorkspaceEntityBuilder<WithListSoftLinksEntity> {
  override var entitySource: EntitySource
  var myName: String
  var links: MutableList<NameId>
}

internal object WithListSoftLinksEntityType : EntityType<WithListSoftLinksEntity, WithListSoftLinksEntityBuilder>() {
  override val entityClass: Class<WithListSoftLinksEntity> get() = WithListSoftLinksEntity::class.java
  operator fun invoke(
    myName: String,
    links: List<NameId>,
    entitySource: EntitySource,
    init: (WithListSoftLinksEntityBuilder.() -> Unit)? = null,
  ): WithListSoftLinksEntityBuilder {
    val builder = builder()
    builder.myName = myName
    builder.links = links.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyWithListSoftLinksEntity(
  entity: WithListSoftLinksEntity,
  modification: WithListSoftLinksEntityBuilder.() -> Unit,
): WithListSoftLinksEntity = modifyEntity(WithListSoftLinksEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createWithListSoftLinksEntity")
fun WithListSoftLinksEntity(
  myName: String,
  links: List<NameId>,
  entitySource: EntitySource,
  init: (WithListSoftLinksEntityBuilder.() -> Unit)? = null,
): WithListSoftLinksEntityBuilder = WithListSoftLinksEntityType(myName, links, entitySource, init)
