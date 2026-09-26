// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PcdParent1EntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.PcdParent1EntityImpl

@GeneratedCodeApiVersion(3)
interface PcdParent1EntityBuilder : WorkspaceEntityBuilder<PcdParent1Entity> {
  override var entitySource: EntitySource
  var name: String
  var version: Int
  var child: PcdChildEntityBuilder?
}

internal object PcdParent1EntityType : EntityType<PcdParent1Entity, PcdParent1EntityBuilder>() {
  override val entityImplClass: Class<*> get() = PcdParent1EntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = PcdParent1EntityImpl.Builder::class.java
  operator fun invoke(
    name: String,
    version: Int,
    entitySource: EntitySource,
    init: (PcdParent1EntityBuilder.() -> Unit)? = null,
  ): PcdParent1EntityBuilder {
    val builder = builder()
    builder.name = name
    builder.version = version
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyPcdParent1Entity(
  entity: PcdParent1Entity,
  modification: PcdParent1EntityBuilder.() -> Unit,
): PcdParent1Entity = modifyEntity(PcdParent1EntityBuilder::class.java, entity, modification)

var PcdParent1EntityBuilder.extensionChildren: List<PcdExtensionChildBuilder>
  by WorkspaceEntity.extensionBuilder(PcdExtensionChild::class.java)

@JvmOverloads
@JvmName("createPcdParent1Entity")
fun PcdParent1Entity(
  name: String,
  version: Int,
  entitySource: EntitySource,
  init: (PcdParent1EntityBuilder.() -> Unit)? = null,
): PcdParent1EntityBuilder = PcdParent1EntityType(name, version, entitySource, init)
