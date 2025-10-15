// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ArtifactPropertiesEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ArtifactPropertiesEntityBuilder : WorkspaceEntityBuilder<ArtifactPropertiesEntity> {
  override var entitySource: EntitySource
  var artifact: ArtifactEntityBuilder
  var providerType: String
  var propertiesXmlTag: String?
}

internal object ArtifactPropertiesEntityType : EntityType<ArtifactPropertiesEntity, ArtifactPropertiesEntityBuilder>() {
  override val entityClass: Class<ArtifactPropertiesEntity> get() = ArtifactPropertiesEntity::class.java
  operator fun invoke(
    providerType: String,
    entitySource: EntitySource,
    init: (ArtifactPropertiesEntityBuilder.() -> Unit)? = null,
  ): ArtifactPropertiesEntityBuilder {
    val builder = builder()
    builder.providerType = providerType
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    providerType: String,
    entitySource: EntitySource,
    init: (ArtifactPropertiesEntity.Builder.() -> Unit)? = null,
  ): ArtifactPropertiesEntity.Builder {
    val builder = builder() as ArtifactPropertiesEntity.Builder
    builder.providerType = providerType
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyArtifactPropertiesEntity(
  entity: ArtifactPropertiesEntity,
  modification: ArtifactPropertiesEntityBuilder.() -> Unit,
): ArtifactPropertiesEntity = modifyEntity(ArtifactPropertiesEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactPropertiesEntity")
fun ArtifactPropertiesEntity(
  providerType: String,
  entitySource: EntitySource,
  init: (ArtifactPropertiesEntityBuilder.() -> Unit)? = null,
): ArtifactPropertiesEntityBuilder = ArtifactPropertiesEntityType(providerType, entitySource, init)
