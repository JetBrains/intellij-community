// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal

@GeneratedCodeApiVersion(3)
interface LibraryEntityBuilder : WorkspaceEntityBuilder<LibraryEntity> {
  override var entitySource: EntitySource
  var name: String
  var tableId: LibraryTableId
  var typeId: LibraryTypeId?
  var roots: MutableList<LibraryRoot>
  var excludedRoots: List<ExcludeUrlEntityBuilder>
}

internal object LibraryEntityType : EntityType<LibraryEntity, LibraryEntityBuilder>() {
  override val entityClass: Class<LibraryEntity> get() = LibraryEntity::class.java
  operator fun invoke(
    name: String,
    tableId: LibraryTableId,
    roots: List<LibraryRoot>,
    entitySource: EntitySource,
    init: (LibraryEntityBuilder.() -> Unit)? = null,
  ): LibraryEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.tableId = tableId
    builder.roots = roots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    name: String,
    tableId: LibraryTableId,
    roots: List<LibraryRoot>,
    entitySource: EntitySource,
    init: (LibraryEntity.Builder.() -> Unit)? = null,
  ): LibraryEntity.Builder {
    val builder = builder() as LibraryEntity.Builder
    builder.name = name
    builder.tableId = tableId
    builder.roots = roots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyLibraryEntity(
  entity: LibraryEntity,
  modification: LibraryEntityBuilder.() -> Unit,
): LibraryEntity = modifyEntity(LibraryEntityBuilder::class.java, entity, modification)

@get:Internal
@set:Internal
var LibraryEntityBuilder.libraryProperties: LibraryPropertiesEntityBuilder?
  by WorkspaceEntity.extensionBuilder(LibraryPropertiesEntity::class.java)

@JvmOverloads
@JvmName("createLibraryEntity")
fun LibraryEntity(
  name: String,
  tableId: LibraryTableId,
  roots: List<LibraryRoot>,
  entitySource: EntitySource,
  init: (LibraryEntityBuilder.() -> Unit)? = null,
): LibraryEntityBuilder = LibraryEntityType(name, tableId, roots, entitySource, init)
