// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ArtifactRootElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ArtifactRootElementEntityBuilder : WorkspaceEntityBuilder<ArtifactRootElementEntity>,
                                             CompositePackagingElementEntity.Builder<ArtifactRootElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  override var artifact: ArtifactEntityBuilder?
  override var children: List<PackagingElementEntityBuilder<out PackagingElementEntity>>
}

internal object ArtifactRootElementEntityType : EntityType<ArtifactRootElementEntity, ArtifactRootElementEntityBuilder>() {
  override val entityClass: Class<ArtifactRootElementEntity> get() = ArtifactRootElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ArtifactRootElementEntityBuilder.() -> Unit)? = null,
  ): ArtifactRootElementEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (ArtifactRootElementEntity.Builder.() -> Unit)? = null,
  ): ArtifactRootElementEntity.Builder {
    val builder = builder() as ArtifactRootElementEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyArtifactRootElementEntity(
  entity: ArtifactRootElementEntity,
  modification: ArtifactRootElementEntityBuilder.() -> Unit,
): ArtifactRootElementEntity = modifyEntity(ArtifactRootElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactRootElementEntity")
fun ArtifactRootElementEntity(
  entitySource: EntitySource,
  init: (ArtifactRootElementEntityBuilder.() -> Unit)? = null,
): ArtifactRootElementEntityBuilder = ArtifactRootElementEntityType(entitySource, init)
