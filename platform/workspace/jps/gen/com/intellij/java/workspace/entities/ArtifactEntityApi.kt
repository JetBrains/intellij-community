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
interface ModifiableArtifactEntity : ModifiableWorkspaceEntity<ArtifactEntity> {
  override var entitySource: EntitySource
  var name: String
  var artifactType: String
  var includeInProjectBuild: Boolean
  var outputUrl: VirtualFileUrl?
  var rootElement: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  var customProperties: List<ModifiableArtifactPropertiesEntity>
  var artifactOutputPackagingElement: ModifiableArtifactOutputPackagingElementEntity?
}

internal object ArtifactEntityType : EntityType<ArtifactEntity, ModifiableArtifactEntity>() {
  override val entityClass: Class<ArtifactEntity> get() = ArtifactEntity::class.java
  operator fun invoke(
    name: String,
    artifactType: String,
    includeInProjectBuild: Boolean,
    entitySource: EntitySource,
    init: (ModifiableArtifactEntity.() -> Unit)? = null,
  ): ModifiableArtifactEntity {
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
  modification: ModifiableArtifactEntity.() -> Unit,
): ArtifactEntity = modifyEntity(ModifiableArtifactEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactEntity")
fun ArtifactEntity(
  name: String,
  artifactType: String,
  includeInProjectBuild: Boolean,
  entitySource: EntitySource,
  init: (ModifiableArtifactEntity.() -> Unit)? = null,
): ModifiableArtifactEntity = ArtifactEntityType(name, artifactType, includeInProjectBuild, entitySource, init)
