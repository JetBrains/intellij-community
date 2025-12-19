// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DirectoryPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface DirectoryPackagingElementEntityBuilder : WorkspaceEntityBuilder<DirectoryPackagingElementEntity>,
                                                   CompositePackagingElementEntity.Builder<DirectoryPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  override var artifact: ArtifactEntityBuilder?
  override var children: List<PackagingElementEntityBuilder<out PackagingElementEntity>>
  var directoryName: String
}

internal object DirectoryPackagingElementEntityType :
  EntityType<DirectoryPackagingElementEntity, DirectoryPackagingElementEntityBuilder>() {
  override val entityClass: Class<DirectoryPackagingElementEntity> get() = DirectoryPackagingElementEntity::class.java
  operator fun invoke(
    directoryName: String,
    entitySource: EntitySource,
    init: (DirectoryPackagingElementEntityBuilder.() -> Unit)? = null,
  ): DirectoryPackagingElementEntityBuilder {
    val builder = builder()
    builder.directoryName = directoryName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    directoryName: String,
    entitySource: EntitySource,
    init: (DirectoryPackagingElementEntity.Builder.() -> Unit)? = null,
  ): DirectoryPackagingElementEntity.Builder {
    val builder = builder() as DirectoryPackagingElementEntity.Builder
    builder.directoryName = directoryName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyDirectoryPackagingElementEntity(
  entity: DirectoryPackagingElementEntity,
  modification: DirectoryPackagingElementEntityBuilder.() -> Unit,
): DirectoryPackagingElementEntity = modifyEntity(DirectoryPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createDirectoryPackagingElementEntity")
fun DirectoryPackagingElementEntity(
  directoryName: String,
  entitySource: EntitySource,
  init: (DirectoryPackagingElementEntityBuilder.() -> Unit)? = null,
): DirectoryPackagingElementEntityBuilder = DirectoryPackagingElementEntityType(directoryName, entitySource, init)
