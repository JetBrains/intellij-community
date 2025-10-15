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
interface ModifiableDirectoryCopyPackagingElementEntity : ModifiableWorkspaceEntity<DirectoryCopyPackagingElementEntity>, FileOrDirectoryPackagingElementEntity.Builder<DirectoryCopyPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  override var filePath: VirtualFileUrl
}

internal object DirectoryCopyPackagingElementEntityType : EntityType<DirectoryCopyPackagingElementEntity, ModifiableDirectoryCopyPackagingElementEntity>(
  FileOrDirectoryPackagingElementEntityType) {
  override val entityClass: Class<DirectoryCopyPackagingElementEntity> get() = DirectoryCopyPackagingElementEntity::class.java
  operator fun invoke(
    filePath: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableDirectoryCopyPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableDirectoryCopyPackagingElementEntity {
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
  modification: ModifiableDirectoryCopyPackagingElementEntity.() -> Unit,
): DirectoryCopyPackagingElementEntity = modifyEntity(ModifiableDirectoryCopyPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createDirectoryCopyPackagingElementEntity")
fun DirectoryCopyPackagingElementEntity(
  filePath: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableDirectoryCopyPackagingElementEntity.() -> Unit)? = null,
): ModifiableDirectoryCopyPackagingElementEntity = DirectoryCopyPackagingElementEntityType(filePath, entitySource, init)
