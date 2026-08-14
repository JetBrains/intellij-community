// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PcdParent2EntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.PcdParent2EntityImpl

@GeneratedCodeApiVersion(3)
interface PcdParent2EntityBuilder : WorkspaceEntityBuilder<PcdParent2Entity> {
  override var entitySource: EntitySource
  var name: String
  var version: Int
  var children: List<PcdChildEntityBuilder>
}

internal object PcdParent2EntityType : EntityType<PcdParent2Entity, PcdParent2EntityBuilder>() {
  override val entityImplClass: Class<*> get() = PcdParent2EntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = PcdParent2EntityImpl.Builder::class.java
  operator fun invoke(
    name: String,
    version: Int,
    entitySource: EntitySource,
    init: (PcdParent2EntityBuilder.() -> Unit)? = null,
  ): PcdParent2EntityBuilder {
    val builder = builder()
    builder.name = name
    builder.version = version
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyPcdParent2Entity(
  entity: PcdParent2Entity,
  modification: PcdParent2EntityBuilder.() -> Unit,
): PcdParent2Entity = modifyEntity(PcdParent2EntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createPcdParent2Entity")
fun PcdParent2Entity(
  name: String,
  version: Int,
  entitySource: EntitySource,
  init: (PcdParent2EntityBuilder.() -> Unit)? = null,
): PcdParent2EntityBuilder = PcdParent2EntityType(name, version, entitySource, init)
