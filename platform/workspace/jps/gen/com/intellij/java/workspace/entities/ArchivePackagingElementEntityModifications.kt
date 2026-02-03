// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ArchivePackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ArchivePackagingElementEntityBuilder : WorkspaceEntityBuilder<ArchivePackagingElementEntity>,
                                                 CompositePackagingElementEntity.Builder<ArchivePackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  override var artifact: ArtifactEntityBuilder?
  override var children: List<PackagingElementEntityBuilder<out PackagingElementEntity>>
  var fileName: String
}

internal object ArchivePackagingElementEntityType : EntityType<ArchivePackagingElementEntity, ArchivePackagingElementEntityBuilder>() {
  override val entityClass: Class<ArchivePackagingElementEntity> get() = ArchivePackagingElementEntity::class.java
  operator fun invoke(
    fileName: String,
    entitySource: EntitySource,
    init: (ArchivePackagingElementEntityBuilder.() -> Unit)? = null,
  ): ArchivePackagingElementEntityBuilder {
    val builder = builder()
    builder.fileName = fileName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    fileName: String,
    entitySource: EntitySource,
    init: (ArchivePackagingElementEntity.Builder.() -> Unit)? = null,
  ): ArchivePackagingElementEntity.Builder {
    val builder = builder() as ArchivePackagingElementEntity.Builder
    builder.fileName = fileName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyArchivePackagingElementEntity(
  entity: ArchivePackagingElementEntity,
  modification: ArchivePackagingElementEntityBuilder.() -> Unit,
): ArchivePackagingElementEntity = modifyEntity(ArchivePackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createArchivePackagingElementEntity")
fun ArchivePackagingElementEntity(
  fileName: String,
  entitySource: EntitySource,
  init: (ArchivePackagingElementEntityBuilder.() -> Unit)? = null,
): ArchivePackagingElementEntityBuilder = ArchivePackagingElementEntityType(fileName, entitySource, init)
