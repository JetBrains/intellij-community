// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@GeneratedCodeApiVersion(3)
interface ModifiableLibraryPropertiesEntity : ModifiableWorkspaceEntity<LibraryPropertiesEntity> {
  override var entitySource: EntitySource
  var propertiesXmlTag: String?
  var library: ModifiableLibraryEntity
}

internal object LibraryPropertiesEntityType : EntityType<LibraryPropertiesEntity, ModifiableLibraryPropertiesEntity>() {
  override val entityClass: Class<LibraryPropertiesEntity> get() = LibraryPropertiesEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableLibraryPropertiesEntity.() -> Unit)? = null,
  ): ModifiableLibraryPropertiesEntity {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (LibraryPropertiesEntity.Builder.() -> Unit)? = null,
  ): LibraryPropertiesEntity.Builder {
    val builder = builder() as LibraryPropertiesEntity.Builder
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyLibraryPropertiesEntity(
  entity: LibraryPropertiesEntity,
  modification: ModifiableLibraryPropertiesEntity.() -> Unit,
): LibraryPropertiesEntity = modifyEntity(ModifiableLibraryPropertiesEntity::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createLibraryPropertiesEntity")
fun LibraryPropertiesEntity(
  entitySource: EntitySource,
  init: (ModifiableLibraryPropertiesEntity.() -> Unit)? = null,
): ModifiableLibraryPropertiesEntity = LibraryPropertiesEntityType(entitySource, init)
