// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentWithLinkToAbstractChildModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface ParentWithLinkToAbstractChildBuilder : WorkspaceEntityBuilder<ParentWithLinkToAbstractChild> {
  override var entitySource: EntitySource
  var data: String
  var child: AbstractChildWithLinkToParentEntityBuilder<out AbstractChildWithLinkToParentEntity>?
}

internal object ParentWithLinkToAbstractChildType : EntityType<ParentWithLinkToAbstractChild, ParentWithLinkToAbstractChildBuilder>() {
  override val entityClass: Class<ParentWithLinkToAbstractChild> get() = ParentWithLinkToAbstractChild::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (ParentWithLinkToAbstractChildBuilder.() -> Unit)? = null,
  ): ParentWithLinkToAbstractChildBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentWithLinkToAbstractChild(
  entity: ParentWithLinkToAbstractChild,
  modification: ParentWithLinkToAbstractChildBuilder.() -> Unit,
): ParentWithLinkToAbstractChild = modifyEntity(ParentWithLinkToAbstractChildBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentWithLinkToAbstractChild")
fun ParentWithLinkToAbstractChild(
  data: String,
  entitySource: EntitySource,
  init: (ParentWithLinkToAbstractChildBuilder.() -> Unit)? = null,
): ParentWithLinkToAbstractChildBuilder = ParentWithLinkToAbstractChildType(data, entitySource, init)
