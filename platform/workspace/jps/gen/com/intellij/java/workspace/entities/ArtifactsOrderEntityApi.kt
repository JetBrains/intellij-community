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
interface ModifiableArtifactsOrderEntity : ModifiableWorkspaceEntity<ArtifactsOrderEntity> {
  override var entitySource: EntitySource
  var orderOfArtifacts: MutableList<String>
}

internal object ArtifactsOrderEntityType : EntityType<ArtifactsOrderEntity, ModifiableArtifactsOrderEntity>() {
  override val entityClass: Class<ArtifactsOrderEntity> get() = ArtifactsOrderEntity::class.java
  operator fun invoke(
    orderOfArtifacts: List<String>,
    entitySource: EntitySource,
    init: (ModifiableArtifactsOrderEntity.() -> Unit)? = null,
  ): ModifiableArtifactsOrderEntity {
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
  modification: ModifiableArtifactsOrderEntity.() -> Unit,
): ArtifactsOrderEntity = modifyEntity(ModifiableArtifactsOrderEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createArtifactsOrderEntity")
fun ArtifactsOrderEntity(
  orderOfArtifacts: List<String>,
  entitySource: EntitySource,
  init: (ModifiableArtifactsOrderEntity.() -> Unit)? = null,
): ModifiableArtifactsOrderEntity = ArtifactsOrderEntityType(orderOfArtifacts, entitySource, init)
