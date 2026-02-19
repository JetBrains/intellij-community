// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DirectoryCopyPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface DirectoryCopyPackagingElementEntityBuilder : WorkspaceEntityBuilder<DirectoryCopyPackagingElementEntity>,
                                                       FileOrDirectoryPackagingElementEntity.Builder<DirectoryCopyPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  override var filePath: VirtualFileUrl
}

internal object DirectoryCopyPackagingElementEntityType :
  EntityType<DirectoryCopyPackagingElementEntity, DirectoryCopyPackagingElementEntityBuilder>() {
  override val entityClass: Class<DirectoryCopyPackagingElementEntity> get() = DirectoryCopyPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    entitySource: EntitySource,
    init: (DirectoryCopyPackagingElementEntityBuilder.() -> Unit)? = null,
  ): DirectoryCopyPackagingElementEntityBuilder {
    val builder = builder()
    builder.filePath = filePath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    filePath: VirtualFileUrl,
    entitySource: EntitySource,
    init: (DirectoryCopyPackagingElementEntity.Builder.() -> Unit)? = null,
  ): DirectoryCopyPackagingElementEntity.Builder {
    val builder = builder() as DirectoryCopyPackagingElementEntity.Builder
    builder.filePath = filePath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyDirectoryCopyPackagingElementEntity(
  entity: DirectoryCopyPackagingElementEntity,
  modification: DirectoryCopyPackagingElementEntityBuilder.() -> Unit,
): DirectoryCopyPackagingElementEntity = modifyEntity(DirectoryCopyPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createDirectoryCopyPackagingElementEntity")
fun DirectoryCopyPackagingElementEntity(
  filePath: VirtualFileUrl,
  entitySource: EntitySource,
  init: (DirectoryCopyPackagingElementEntityBuilder.() -> Unit)? = null,
): DirectoryCopyPackagingElementEntityBuilder = DirectoryCopyPackagingElementEntityType(filePath, entitySource, init)
