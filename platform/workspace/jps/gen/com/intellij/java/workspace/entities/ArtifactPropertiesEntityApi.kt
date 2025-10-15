// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableArtifactPropertiesEntity : ModifiableWorkspaceEntity<ArtifactPropertiesEntity> {
  override var entitySource: EntitySource
  var artifact: ModifiableArtifactEntity
  var providerType: String
  var propertiesXmlTag: String?
}

internal object ArtifactPropertiesEntityType : EntityType<ArtifactPropertiesEntity, ModifiableArtifactPropertiesEntity>() {
  override val entityClass: Class<ArtifactPropertiesEntity> get() = ArtifactPropertiesEntity::class.java
  operator fun invoke(
    providerType: String,
    entitySource: EntitySource,
    init: (ModifiableArtifactPropertiesEntity.() -> Unit)? = null,
  ): ModifiableArtifactPropertiesEntity {
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
  modification: ModifiableArtifactPropertiesEntity.() -> Unit,
): ArtifactPropertiesEntity = modifyEntity(ModifiableArtifactPropertiesEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactPropertiesEntity")
fun ArtifactPropertiesEntity(
  providerType: String,
  entitySource: EntitySource,
  init: (ModifiableArtifactPropertiesEntity.() -> Unit)? = null,
): ModifiableArtifactPropertiesEntity = ArtifactPropertiesEntityType(providerType, entitySource, init)
