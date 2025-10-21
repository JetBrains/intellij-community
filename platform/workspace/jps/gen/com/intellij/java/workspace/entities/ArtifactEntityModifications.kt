// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ArtifactEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ArtifactEntityBuilder : WorkspaceEntityBuilder<ArtifactEntity> {
  override var entitySource: EntitySource
  var name: String
  var artifactType: String
  var includeInProjectBuild: Boolean
  var outputUrl: VirtualFileUrl?
  var rootElement: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var customProperties: List<ArtifactPropertiesEntityBuilder>
  var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntityBuilder?
}

internal object ArtifactEntityType : EntityType<ArtifactEntity, ArtifactEntityBuilder>() {
  override val entityClass: Class<ArtifactEntity> get() = ArtifactEntity::class.java
  operator fun invoke(
    name: String,
    artifactType: String,
    includeInProjectBuild: Boolean,
    entitySource: EntitySource,
    init: (ArtifactEntityBuilder.() -> Unit)? = null,
  ): ArtifactEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.artifactType = artifactType
    builder.includeInProjectBuild = includeInProjectBuild
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    name: String,
    artifactType: String,
    includeInProjectBuild: Boolean,
    entitySource: EntitySource,
    init: (ArtifactEntity.Builder.() -> Unit)? = null,
  ): ArtifactEntity.Builder {
    val builder = builder() as ArtifactEntity.Builder
    builder.name = name
    builder.artifactType = artifactType
    builder.includeInProjectBuild = includeInProjectBuild
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyArtifactEntity(
  entity: ArtifactEntity,
  modification: ArtifactEntityBuilder.() -> Unit,
): ArtifactEntity = modifyEntity(ArtifactEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactEntity")
fun ArtifactEntity(
  name: String,
  artifactType: String,
  includeInProjectBuild: Boolean,
  entitySource: EntitySource,
  init: (ArtifactEntityBuilder.() -> Unit)? = null,
): ArtifactEntityBuilder = ArtifactEntityType(name, artifactType, includeInProjectBuild, entitySource, init)
