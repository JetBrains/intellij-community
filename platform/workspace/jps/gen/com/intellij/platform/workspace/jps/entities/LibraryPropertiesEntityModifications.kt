// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryPropertiesEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface LibraryPropertiesEntityBuilder : WorkspaceEntityBuilder<LibraryPropertiesEntity> {
  override var entitySource: EntitySource
  var propertiesXmlTag: String?
  var library: LibraryEntityBuilder
}

internal object LibraryPropertiesEntityType : EntityType<LibraryPropertiesEntity, LibraryPropertiesEntityBuilder>() {
  override val entityClass: Class<LibraryPropertiesEntity> get() = LibraryPropertiesEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (LibraryPropertiesEntityBuilder.() -> Unit)? = null,
  ): LibraryPropertiesEntityBuilder {
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
  modification: LibraryPropertiesEntityBuilder.() -> Unit,
): LibraryPropertiesEntity = modifyEntity(LibraryPropertiesEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createLibraryPropertiesEntity")
fun LibraryPropertiesEntity(
  entitySource: EntitySource,
  init: (LibraryPropertiesEntityBuilder.() -> Unit)? = null,
): LibraryPropertiesEntityBuilder = LibraryPropertiesEntityType(entitySource, init)
