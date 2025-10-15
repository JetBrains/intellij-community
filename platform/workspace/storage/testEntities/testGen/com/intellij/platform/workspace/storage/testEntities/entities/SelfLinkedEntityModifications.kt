// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SelfLinkedEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
interface SelfLinkedEntityBuilder : WorkspaceEntityBuilder<SelfLinkedEntity> {
  override var entitySource: EntitySource
  var parentEntity: SelfLinkedEntityBuilder?
}

internal object SelfLinkedEntityType : EntityType<SelfLinkedEntity, SelfLinkedEntityBuilder>() {
  override val entityClass: Class<SelfLinkedEntity> get() = SelfLinkedEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (SelfLinkedEntityBuilder.() -> Unit)? = null,
  ): SelfLinkedEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySelfLinkedEntity(
  entity: SelfLinkedEntity,
  modification: SelfLinkedEntityBuilder.() -> Unit,
): SelfLinkedEntity = modifyEntity(SelfLinkedEntityBuilder::class.java, entity, modification)

var SelfLinkedEntityBuilder.children: List<SelfLinkedEntityBuilder>
  by WorkspaceEntity.extensionBuilder(SelfLinkedEntity::class.java)


@JvmOverloads
@JvmName("createSelfLinkedEntity")
fun SelfLinkedEntity(
  entitySource: EntitySource,
  init: (SelfLinkedEntityBuilder.() -> Unit)? = null,
): SelfLinkedEntityBuilder = SelfLinkedEntityType(entitySource, init)
