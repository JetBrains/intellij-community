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
interface ModifiableArtifactRootElementEntity : ModifiableWorkspaceEntity<ArtifactRootElementEntity>, CompositePackagingElementEntity.Builder<ArtifactRootElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  override var artifact: ModifiableArtifactEntity?
  override var children: List<ModifiablePackagingElementEntity<out PackagingElementEntity>>
}

internal object ArtifactRootElementEntityType : EntityType<ArtifactRootElementEntity, ModifiableArtifactRootElementEntity>(
  CompositePackagingElementEntityType) {
  override val entityClass: Class<ArtifactRootElementEntity> get() = ArtifactRootElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableArtifactRootElementEntity.() -> Unit)? = null,
  ): ModifiableArtifactRootElementEntity {
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
  modification: ModifiableArtifactRootElementEntity.() -> Unit,
): ArtifactRootElementEntity = modifyEntity(ModifiableArtifactRootElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactRootElementEntity")
fun ArtifactRootElementEntity(
  entitySource: EntitySource,
  init: (ModifiableArtifactRootElementEntity.() -> Unit)? = null,
): ModifiableArtifactRootElementEntity = ArtifactRootElementEntityType(entitySource, init)
