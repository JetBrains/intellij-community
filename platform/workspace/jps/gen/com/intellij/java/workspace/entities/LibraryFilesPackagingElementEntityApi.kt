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
interface ModifiableLibraryFilesPackagingElementEntity : ModifiableWorkspaceEntity<LibraryFilesPackagingElementEntity>, PackagingElementEntity.Builder<LibraryFilesPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  var library: LibraryId?
}

internal object LibraryFilesPackagingElementEntityType : EntityType<LibraryFilesPackagingElementEntity, ModifiableLibraryFilesPackagingElementEntity>(
  PackagingElementEntityType) {
  override val entityClass: Class<LibraryFilesPackagingElementEntity> get() = LibraryFilesPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableLibraryFilesPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableLibraryFilesPackagingElementEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (LibraryFilesPackagingElementEntity.Builder.() -> Unit)? = null,
  ): LibraryFilesPackagingElementEntity.Builder {
    val builder = builder() as LibraryFilesPackagingElementEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyLibraryFilesPackagingElementEntity(
  entity: LibraryFilesPackagingElementEntity,
  modification: ModifiableLibraryFilesPackagingElementEntity.() -> Unit,
): LibraryFilesPackagingElementEntity = modifyEntity(ModifiableLibraryFilesPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createLibraryFilesPackagingElementEntity")
fun LibraryFilesPackagingElementEntity(
  entitySource: EntitySource,
  init: (ModifiableLibraryFilesPackagingElementEntity.() -> Unit)? = null,
): ModifiableLibraryFilesPackagingElementEntity = LibraryFilesPackagingElementEntityType(entitySource, init)
