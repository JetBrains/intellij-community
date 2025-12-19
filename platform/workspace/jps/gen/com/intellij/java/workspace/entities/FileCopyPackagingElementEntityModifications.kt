// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FileCopyPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface FileCopyPackagingElementEntityBuilder : WorkspaceEntityBuilder<FileCopyPackagingElementEntity>,
                                                  FileOrDirectoryPackagingElementEntity.Builder<FileCopyPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  override var filePath: VirtualFileUrl
  var renamedOutputFileName: String?
}

internal object FileCopyPackagingElementEntityType : EntityType<FileCopyPackagingElementEntity, FileCopyPackagingElementEntityBuilder>() {
  override val entityClass: Class<FileCopyPackagingElementEntity> get() = FileCopyPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    entitySource: EntitySource,
    init: (FileCopyPackagingElementEntityBuilder.() -> Unit)? = null,
  ): FileCopyPackagingElementEntityBuilder {
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
    init: (FileCopyPackagingElementEntity.Builder.() -> Unit)? = null,
  ): FileCopyPackagingElementEntity.Builder {
    val builder = builder() as FileCopyPackagingElementEntity.Builder
    builder.filePath = filePath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyFileCopyPackagingElementEntity(
  entity: FileCopyPackagingElementEntity,
  modification: FileCopyPackagingElementEntityBuilder.() -> Unit,
): FileCopyPackagingElementEntity = modifyEntity(FileCopyPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createFileCopyPackagingElementEntity")
fun FileCopyPackagingElementEntity(
  filePath: VirtualFileUrl,
  entitySource: EntitySource,
  init: (FileCopyPackagingElementEntityBuilder.() -> Unit)? = null,
): FileCopyPackagingElementEntityBuilder = FileCopyPackagingElementEntityType(filePath, entitySource, init)
