// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ArtifactsOrderEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ArtifactsOrderEntityBuilder : WorkspaceEntityBuilder<ArtifactsOrderEntity> {
  override var entitySource: EntitySource
  var orderOfArtifacts: MutableList<String>
}

internal object ArtifactsOrderEntityType : EntityType<ArtifactsOrderEntity, ArtifactsOrderEntityBuilder>() {
  override val entityClass: Class<ArtifactsOrderEntity> get() = ArtifactsOrderEntity::class.java
  operator fun invoke(
    orderOfArtifacts: List<String>,
    entitySource: EntitySource,
    init: (ArtifactsOrderEntityBuilder.() -> Unit)? = null,
  ): ArtifactsOrderEntityBuilder {
    val builder = builder()
    builder.orderOfArtifacts = orderOfArtifacts.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    orderOfArtifacts: List<String>,
    entitySource: EntitySource,
    init: (ArtifactsOrderEntity.Builder.() -> Unit)? = null,
  ): ArtifactsOrderEntity.Builder {
    val builder = builder() as ArtifactsOrderEntity.Builder
    builder.orderOfArtifacts = orderOfArtifacts.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyArtifactsOrderEntity(
  entity: ArtifactsOrderEntity,
  modification: ArtifactsOrderEntityBuilder.() -> Unit,
): ArtifactsOrderEntity = modifyEntity(ArtifactsOrderEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactsOrderEntity")
fun ArtifactsOrderEntity(
  orderOfArtifacts: List<String>,
  entitySource: EntitySource,
  init: (ArtifactsOrderEntityBuilder.() -> Unit)? = null,
): ArtifactsOrderEntityBuilder = ArtifactsOrderEntityType(orderOfArtifacts, entitySource, init)
