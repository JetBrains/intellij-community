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
interface ModifiableExtractedDirectoryPackagingElementEntity : ModifiableWorkspaceEntity<ExtractedDirectoryPackagingElementEntity>, FileOrDirectoryPackagingElementEntity.Builder<ExtractedDirectoryPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  override var filePath: VirtualFileUrl
  var pathInArchive: String
}

internal object ExtractedDirectoryPackagingElementEntityType : EntityType<ExtractedDirectoryPackagingElementEntity, ModifiableExtractedDirectoryPackagingElementEntity>(
  FileOrDirectoryPackagingElementEntityType) {
  override val entityClass: Class<ExtractedDirectoryPackagingElementEntity> get() = ExtractedDirectoryPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    pathInArchive: String,
    entitySource: EntitySource,
    init: (ModifiableExtractedDirectoryPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableExtractedDirectoryPackagingElementEntity {
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
  modification: ModifiableExtractedDirectoryPackagingElementEntity.() -> Unit,
): ExtractedDirectoryPackagingElementEntity =
  modifyEntity(ModifiableExtractedDirectoryPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createExtractedDirectoryPackagingElementEntity")
fun ExtractedDirectoryPackagingElementEntity(
  filePath: VirtualFileUrl,
  pathInArchive: String,
  entitySource: EntitySource,
  init: (ModifiableExtractedDirectoryPackagingElementEntity.() -> Unit)? = null,
): ModifiableExtractedDirectoryPackagingElementEntity =
  ExtractedDirectoryPackagingElementEntityType(filePath, pathInArchive, entitySource, init)
