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
interface ModifiableDirectoryPackagingElementEntity : ModifiableWorkspaceEntity<DirectoryPackagingElementEntity>, CompositePackagingElementEntity.Builder<DirectoryPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  override var artifact: ModifiableArtifactEntity?
  override var children: List<ModifiablePackagingElementEntity<out PackagingElementEntity>>
  var directoryName: String
}

internal object DirectoryPackagingElementEntityType : EntityType<DirectoryPackagingElementEntity, ModifiableDirectoryPackagingElementEntity>(
  CompositePackagingElementEntityType) {
  override val entityClass: Class<DirectoryPackagingElementEntity> get() = DirectoryPackagingElementEntity::class.java
  operator fun invoke(
    directoryName: String,
    entitySource: EntitySource,
    init: (ModifiableDirectoryPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableDirectoryPackagingElementEntity {
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
  modification: ModifiableDirectoryPackagingElementEntity.() -> Unit,
): DirectoryPackagingElementEntity = modifyEntity(ModifiableDirectoryPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createDirectoryPackagingElementEntity")
fun DirectoryPackagingElementEntity(
  directoryName: String,
  entitySource: EntitySource,
  init: (ModifiableDirectoryPackagingElementEntity.() -> Unit)? = null,
): ModifiableDirectoryPackagingElementEntity = DirectoryPackagingElementEntityType(directoryName, entitySource, init)
