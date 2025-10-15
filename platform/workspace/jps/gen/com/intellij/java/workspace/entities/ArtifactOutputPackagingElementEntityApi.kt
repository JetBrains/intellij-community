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
interface ModifiableArtifactOutputPackagingElementEntity : ModifiableWorkspaceEntity<ArtifactOutputPackagingElementEntity>, PackagingElementEntity.Builder<ArtifactOutputPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  var artifact: ArtifactId?
}

internal object ArtifactOutputPackagingElementEntityType : EntityType<ArtifactOutputPackagingElementEntity, ModifiableArtifactOutputPackagingElementEntity>(
  PackagingElementEntityType) {
  override val entityClass: Class<ArtifactOutputPackagingElementEntity> get() = ArtifactOutputPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableArtifactOutputPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableArtifactOutputPackagingElementEntity {
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
  modification: ModifiableArtifactOutputPackagingElementEntity.() -> Unit,
): ArtifactOutputPackagingElementEntity = modifyEntity(ModifiableArtifactOutputPackagingElementEntity::class.java, entity, modification)

@Parent
var ModifiableArtifactOutputPackagingElementEntity.artifactEntity: ModifiableArtifactEntity?
  by WorkspaceEntity.extensionBuilder(ArtifactEntity::class.java)

@JvmOverloads
@JvmName("createArtifactOutputPackagingElementEntity")
fun ArtifactOutputPackagingElementEntity(
  entitySource: EntitySource,
  init: (ModifiableArtifactOutputPackagingElementEntity.() -> Unit)? = null,
): ModifiableArtifactOutputPackagingElementEntity = ArtifactOutputPackagingElementEntityType(entitySource, init)
