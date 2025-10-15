// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@GeneratedCodeApiVersion(3)
interface ModifiableSourceRootOrderEntity : ModifiableWorkspaceEntity<SourceRootOrderEntity> {
  override var entitySource: EntitySource
  var orderOfSourceRoots: MutableList<VirtualFileUrl>
  var contentRootEntity: ModifiableContentRootEntity
}

internal object SourceRootOrderEntityType : EntityType<SourceRootOrderEntity, ModifiableSourceRootOrderEntity>() {
  override val entityClass: Class<SourceRootOrderEntity> get() = SourceRootOrderEntity::class.java
  operator fun invoke(
    orderOfSourceRoots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ModifiableSourceRootOrderEntity.() -> Unit)? = null,
  ): ModifiableSourceRootOrderEntity {
    val builder = builder()
    builder.orderOfSourceRoots = orderOfSourceRoots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    orderOfSourceRoots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (SourceRootOrderEntity.Builder.() -> Unit)? = null,
  ): SourceRootOrderEntity.Builder {
    val builder = builder() as SourceRootOrderEntity.Builder
    builder.orderOfSourceRoots = orderOfSourceRoots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifySourceRootOrderEntity(
  entity: SourceRootOrderEntity,
  modification: ModifiableSourceRootOrderEntity.() -> Unit,
): SourceRootOrderEntity = modifyEntity(ModifiableSourceRootOrderEntity::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createSourceRootOrderEntity")
fun SourceRootOrderEntity(
  orderOfSourceRoots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ModifiableSourceRootOrderEntity.() -> Unit)? = null,
): ModifiableSourceRootOrderEntity = SourceRootOrderEntityType(orderOfSourceRoots, entitySource, init)
