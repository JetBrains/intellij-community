// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PcdExtensionChildModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.PcdExtensionChildImpl

@GeneratedCodeApiVersion(3)
interface PcdExtensionChildBuilder : WorkspaceEntityBuilder<PcdExtensionChild> {
  override var entitySource: EntitySource
  var data: Float
  var parent: PcdParent1EntityBuilder
}

internal object PcdExtensionChildType : EntityType<PcdExtensionChild, PcdExtensionChildBuilder>() {
  override val entityImplClass: Class<*> get() = PcdExtensionChildImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = PcdExtensionChildImpl.Builder::class.java
  operator fun invoke(
    data: Float,
    entitySource: EntitySource,
    init: (PcdExtensionChildBuilder.() -> Unit)? = null,
  ): PcdExtensionChildBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyPcdExtensionChild(
  entity: PcdExtensionChild,
  modification: PcdExtensionChildBuilder.() -> Unit,
): PcdExtensionChild = modifyEntity(PcdExtensionChildBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createPcdExtensionChild")
fun PcdExtensionChild(
  data: Float,
  entitySource: EntitySource,
  init: (PcdExtensionChildBuilder.() -> Unit)? = null,
): PcdExtensionChildBuilder = PcdExtensionChildType(data, entitySource, init)
