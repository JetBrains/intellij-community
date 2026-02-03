// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ArtifactOutputPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface ArtifactOutputPackagingElementEntityBuilder : WorkspaceEntityBuilder<ArtifactOutputPackagingElementEntity>,
                                                        PackagingElementEntity.Builder<ArtifactOutputPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var artifact: ArtifactId?
}

internal object ArtifactOutputPackagingElementEntityType :
  EntityType<ArtifactOutputPackagingElementEntity, ArtifactOutputPackagingElementEntityBuilder>() {
  override val entityClass: Class<ArtifactOutputPackagingElementEntity> get() = ArtifactOutputPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ArtifactOutputPackagingElementEntityBuilder.() -> Unit)? = null,
  ): ArtifactOutputPackagingElementEntityBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (ArtifactOutputPackagingElementEntity.Builder.() -> Unit)? = null,
  ): ArtifactOutputPackagingElementEntity.Builder {
    val builder = builder() as ArtifactOutputPackagingElementEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyArtifactOutputPackagingElementEntity(
  entity: ArtifactOutputPackagingElementEntity,
  modification: ArtifactOutputPackagingElementEntityBuilder.() -> Unit,
): ArtifactOutputPackagingElementEntity = modifyEntity(ArtifactOutputPackagingElementEntityBuilder::class.java, entity, modification)

@Parent
var ArtifactOutputPackagingElementEntityBuilder.artifactEntity: ArtifactEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ArtifactEntity::class.java)

@JvmOverloads
@JvmName("createArtifactOutputPackagingElementEntity")
fun ArtifactOutputPackagingElementEntity(
  entitySource: EntitySource,
  init: (ArtifactOutputPackagingElementEntityBuilder.() -> Unit)? = null,
): ArtifactOutputPackagingElementEntityBuilder = ArtifactOutputPackagingElementEntityType(entitySource, init)
