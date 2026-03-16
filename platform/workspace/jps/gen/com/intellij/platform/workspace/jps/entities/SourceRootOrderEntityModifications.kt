// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SourceRootOrderEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface SourceRootOrderEntityBuilder : WorkspaceEntityBuilder<SourceRootOrderEntity> {
  override var entitySource: EntitySource
  var orderOfSourceRoots: MutableList<VirtualFileUrl>
  var contentRootEntity: ContentRootEntityBuilder
}

internal object SourceRootOrderEntityType : EntityType<SourceRootOrderEntity, SourceRootOrderEntityBuilder>() {
  override val entityClass: Class<SourceRootOrderEntity> get() = SourceRootOrderEntity::class.java
  operator fun invoke(
    orderOfSourceRoots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (SourceRootOrderEntityBuilder.() -> Unit)? = null,
  ): SourceRootOrderEntityBuilder {
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
  modification: SourceRootOrderEntityBuilder.() -> Unit,
): SourceRootOrderEntity = modifyEntity(SourceRootOrderEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createSourceRootOrderEntity")
fun SourceRootOrderEntity(
  orderOfSourceRoots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (SourceRootOrderEntityBuilder.() -> Unit)? = null,
): SourceRootOrderEntityBuilder = SourceRootOrderEntityType(orderOfSourceRoots, entitySource, init)
