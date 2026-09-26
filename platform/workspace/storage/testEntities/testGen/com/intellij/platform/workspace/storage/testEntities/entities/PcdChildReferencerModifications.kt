// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PcdChildReferencerModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.PcdChildReferencerImpl

@GeneratedCodeApiVersion(3)
interface PcdChildReferencerBuilder : WorkspaceEntityBuilder<PcdChildReferencer> {
  override var entitySource: EntitySource
  var data: String
  var relatedChildEntity: PCDIdChild
}

internal object PcdChildReferencerType : EntityType<PcdChildReferencer, PcdChildReferencerBuilder>() {
  override val entityImplClass: Class<*> get() = PcdChildReferencerImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = PcdChildReferencerImpl.Builder::class.java
  operator fun invoke(
    data: String,
    relatedChildEntity: PCDIdChild,
    entitySource: EntitySource,
    init: (PcdChildReferencerBuilder.() -> Unit)? = null,
  ): PcdChildReferencerBuilder {
    val builder = builder()
    builder.data = data
    builder.relatedChildEntity = relatedChildEntity
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyPcdChildReferencer(
  entity: PcdChildReferencer,
  modification: PcdChildReferencerBuilder.() -> Unit,
): PcdChildReferencer = modifyEntity(PcdChildReferencerBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createPcdChildReferencer")
fun PcdChildReferencer(
  data: String,
  relatedChildEntity: PCDIdChild,
  entitySource: EntitySource,
  init: (PcdChildReferencerBuilder.() -> Unit)? = null,
): PcdChildReferencerBuilder = PcdChildReferencerType(data, relatedChildEntity, entitySource, init)
