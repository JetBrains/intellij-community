// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WithSealedEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface WithSealedEntityBuilder : WorkspaceEntityBuilder<WithSealedEntity> {
  override var entitySource: EntitySource
  var classes: MutableList<MySealedClass>
  var interfaces: MutableList<MySealedInterface>
}

internal object WithSealedEntityType : EntityType<WithSealedEntity, WithSealedEntityBuilder>() {
  override val entityClass: Class<WithSealedEntity> get() = WithSealedEntity::class.java
  operator fun invoke(
    classes: List<MySealedClass>,
    interfaces: List<MySealedInterface>,
    entitySource: EntitySource,
    init: (WithSealedEntityBuilder.() -> Unit)? = null,
  ): WithSealedEntityBuilder {
    val builder = builder()
    builder.classes = classes.toMutableWorkspaceList()
    builder.interfaces = interfaces.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyWithSealedEntity(
  entity: WithSealedEntity,
  modification: WithSealedEntityBuilder.() -> Unit,
): WithSealedEntity = modifyEntity(WithSealedEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createWithSealedEntity")
fun WithSealedEntity(
  classes: List<MySealedClass>,
  interfaces: List<MySealedInterface>,
  entitySource: EntitySource,
  init: (WithSealedEntityBuilder.() -> Unit)? = null,
): WithSealedEntityBuilder = WithSealedEntityType(classes, interfaces, entitySource, init)
