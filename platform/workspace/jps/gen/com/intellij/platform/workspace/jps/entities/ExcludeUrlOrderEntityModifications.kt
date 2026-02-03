// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExcludeUrlOrderEntityModifications")

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
interface ExcludeUrlOrderEntityBuilder : WorkspaceEntityBuilder<ExcludeUrlOrderEntity> {
  override var entitySource: EntitySource
  var order: MutableList<VirtualFileUrl>
  var contentRoot: ContentRootEntityBuilder
}

internal object ExcludeUrlOrderEntityType : EntityType<ExcludeUrlOrderEntity, ExcludeUrlOrderEntityBuilder>() {
  override val entityClass: Class<ExcludeUrlOrderEntity> get() = ExcludeUrlOrderEntity::class.java
  operator fun invoke(
    order: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ExcludeUrlOrderEntityBuilder.() -> Unit)? = null,
  ): ExcludeUrlOrderEntityBuilder {
    val builder = builder()
    builder.order = order.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    order: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ExcludeUrlOrderEntity.Builder.() -> Unit)? = null,
  ): ExcludeUrlOrderEntity.Builder {
    val builder = builder() as ExcludeUrlOrderEntity.Builder
    builder.order = order.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyExcludeUrlOrderEntity(
  entity: ExcludeUrlOrderEntity,
  modification: ExcludeUrlOrderEntityBuilder.() -> Unit,
): ExcludeUrlOrderEntity = modifyEntity(ExcludeUrlOrderEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createExcludeUrlOrderEntity")
fun ExcludeUrlOrderEntity(
  order: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ExcludeUrlOrderEntityBuilder.() -> Unit)? = null,
): ExcludeUrlOrderEntityBuilder = ExcludeUrlOrderEntityType(order, entitySource, init)
