// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExtractedDirectoryPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ExtractedDirectoryPackagingElementEntityBuilder : WorkspaceEntityBuilder<ExtractedDirectoryPackagingElementEntity>,
                                                            FileOrDirectoryPackagingElementEntity.Builder<ExtractedDirectoryPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  override var filePath: VirtualFileUrl
  var pathInArchive: String
}

internal object ExtractedDirectoryPackagingElementEntityType :
  EntityType<ExtractedDirectoryPackagingElementEntity, ExtractedDirectoryPackagingElementEntityBuilder>() {
  override val entityClass: Class<ExtractedDirectoryPackagingElementEntity> get() = ExtractedDirectoryPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    pathInArchive: String,
    entitySource: EntitySource,
    init: (ExtractedDirectoryPackagingElementEntityBuilder.() -> Unit)? = null,
  ): ExtractedDirectoryPackagingElementEntityBuilder {
    val builder = builder()
    builder.filePath = filePath
    builder.pathInArchive = pathInArchive
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    filePath: VirtualFileUrl,
    pathInArchive: String,
    entitySource: EntitySource,
    init: (ExtractedDirectoryPackagingElementEntity.Builder.() -> Unit)? = null,
  ): ExtractedDirectoryPackagingElementEntity.Builder {
    val builder = builder() as ExtractedDirectoryPackagingElementEntity.Builder
    builder.filePath = filePath
    builder.pathInArchive = pathInArchive
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyExtractedDirectoryPackagingElementEntity(
  entity: ExtractedDirectoryPackagingElementEntity,
  modification: ExtractedDirectoryPackagingElementEntityBuilder.() -> Unit,
): ExtractedDirectoryPackagingElementEntity =
  modifyEntity(ExtractedDirectoryPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createExtractedDirectoryPackagingElementEntity")
fun ExtractedDirectoryPackagingElementEntity(
  filePath: VirtualFileUrl,
  pathInArchive: String,
  entitySource: EntitySource,
  init: (ExtractedDirectoryPackagingElementEntityBuilder.() -> Unit)? = null,
): ExtractedDirectoryPackagingElementEntityBuilder =
  ExtractedDirectoryPackagingElementEntityType(filePath, pathInArchive, entitySource, init)
