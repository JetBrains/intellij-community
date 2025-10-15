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
interface ModifiableFileOrDirectoryPackagingElementEntity<T : FileOrDirectoryPackagingElementEntity> : ModifiableWorkspaceEntity<T>, PackagingElementEntity.Builder<T> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  var filePath: VirtualFileUrl
}

internal object FileOrDirectoryPackagingElementEntityType : EntityType<FileOrDirectoryPackagingElementEntity, ModifiableFileOrDirectoryPackagingElementEntity<FileOrDirectoryPackagingElementEntity>>(
  PackagingElementEntityType) {
  override val entityClass: Class<FileOrDirectoryPackagingElementEntity> get() = FileOrDirectoryPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableFileOrDirectoryPackagingElementEntity<FileOrDirectoryPackagingElementEntity>.() -> Unit)? = null,
  ): ModifiableFileOrDirectoryPackagingElementEntity<FileOrDirectoryPackagingElementEntity> {
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
