// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableLibraryEntity : ModifiableWorkspaceEntity<LibraryEntity> {
  override var entitySource: EntitySource
  var name: String
  var tableId: LibraryTableId
  var typeId: LibraryTypeId?
  var roots: MutableList<LibraryRoot>
  var excludedRoots: List<ModifiableExcludeUrlEntity>
}

internal object LibraryEntityType : EntityType<LibraryEntity, ModifiableLibraryEntity>() {
  override val entityClass: Class<LibraryEntity> get() = LibraryEntity::class.java
  operator fun invoke(
    name: String,
    tableId: LibraryTableId,
    roots: List<LibraryRoot>,
    entitySource: EntitySource,
    init: (ModifiableLibraryEntity.() -> Unit)? = null,
  ): ModifiableLibraryEntity {
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
  modification: ModifiableLibraryEntity.() -> Unit,
): LibraryEntity = modifyEntity(ModifiableLibraryEntity::class.java, entity, modification)

@get:Internal
@set:Internal
var ModifiableLibraryEntity.libraryProperties: ModifiableLibraryPropertiesEntity?
  by WorkspaceEntity.extensionBuilder(LibraryPropertiesEntity::class.java)

@JvmOverloads
@JvmName("createLibraryEntity")
fun LibraryEntity(
  name: String,
  tableId: LibraryTableId,
  roots: List<LibraryRoot>,
  entitySource: EntitySource,
  init: (ModifiableLibraryEntity.() -> Unit)? = null,
): ModifiableLibraryEntity = LibraryEntityType(name, tableId, roots, entitySource, init)
