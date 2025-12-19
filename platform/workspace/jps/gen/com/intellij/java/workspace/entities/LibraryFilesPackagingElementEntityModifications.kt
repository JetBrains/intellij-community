// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryFilesPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface LibraryFilesPackagingElementEntityBuilder : WorkspaceEntityBuilder<LibraryFilesPackagingElementEntity>,
                                                      PackagingElementEntity.Builder<LibraryFilesPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var library: LibraryId?
}

internal object LibraryFilesPackagingElementEntityType :
  EntityType<LibraryFilesPackagingElementEntity, LibraryFilesPackagingElementEntityBuilder>() {
  override val entityClass: Class<LibraryFilesPackagingElementEntity> get() = LibraryFilesPackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (LibraryFilesPackagingElementEntityBuilder.() -> Unit)? = null,
  ): LibraryFilesPackagingElementEntityBuilder {
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
  modification: LibraryFilesPackagingElementEntityBuilder.() -> Unit,
): LibraryFilesPackagingElementEntity = modifyEntity(LibraryFilesPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createLibraryFilesPackagingElementEntity")
fun LibraryFilesPackagingElementEntity(
  entitySource: EntitySource,
  init: (LibraryFilesPackagingElementEntityBuilder.() -> Unit)? = null,
): LibraryFilesPackagingElementEntityBuilder = LibraryFilesPackagingElementEntityType(entitySource, init)
