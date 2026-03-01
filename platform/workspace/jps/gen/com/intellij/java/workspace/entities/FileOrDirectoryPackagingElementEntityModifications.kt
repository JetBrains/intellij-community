// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FileOrDirectoryPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface FileOrDirectoryPackagingElementEntityBuilder<T : FileOrDirectoryPackagingElementEntity> : WorkspaceEntityBuilder<T>,
                                                                                                    PackagingElementEntity.Builder<T> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var filePath: VirtualFileUrl
}

internal object FileOrDirectoryPackagingElementEntityType :
  EntityType<FileOrDirectoryPackagingElementEntity, FileOrDirectoryPackagingElementEntityBuilder<FileOrDirectoryPackagingElementEntity>>() {
  override val entityClass: Class<FileOrDirectoryPackagingElementEntity> get() = FileOrDirectoryPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    entitySource: EntitySource,
    init: (FileOrDirectoryPackagingElementEntityBuilder<FileOrDirectoryPackagingElementEntity>.() -> Unit)? = null,
  ): FileOrDirectoryPackagingElementEntityBuilder<FileOrDirectoryPackagingElementEntity> {
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
    init: (FileOrDirectoryPackagingElementEntity.Builder<FileOrDirectoryPackagingElementEntity>.() -> Unit)? = null,
  ): FileOrDirectoryPackagingElementEntity.Builder<FileOrDirectoryPackagingElementEntity> {
    val builder = builder() as FileOrDirectoryPackagingElementEntity.Builder<FileOrDirectoryPackagingElementEntity>
    builder.filePath = filePath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
