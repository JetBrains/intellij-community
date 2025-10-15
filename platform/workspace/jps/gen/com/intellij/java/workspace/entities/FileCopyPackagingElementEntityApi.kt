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
interface ModifiableFileCopyPackagingElementEntity : ModifiableWorkspaceEntity<FileCopyPackagingElementEntity>, FileOrDirectoryPackagingElementEntity.Builder<FileCopyPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  override var filePath: VirtualFileUrl
  var renamedOutputFileName: String?
}

internal object FileCopyPackagingElementEntityType : EntityType<FileCopyPackagingElementEntity, ModifiableFileCopyPackagingElementEntity>(
  FileOrDirectoryPackagingElementEntityType) {
  override val entityClass: Class<FileCopyPackagingElementEntity> get() = FileCopyPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableFileCopyPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableFileCopyPackagingElementEntity {
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
  modification: ModifiableFileCopyPackagingElementEntity.() -> Unit,
): FileCopyPackagingElementEntity = modifyEntity(ModifiableFileCopyPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createFileCopyPackagingElementEntity")
fun FileCopyPackagingElementEntity(
  filePath: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableFileCopyPackagingElementEntity.() -> Unit)? = null,
): ModifiableFileCopyPackagingElementEntity = FileCopyPackagingElementEntityType(filePath, entitySource, init)
