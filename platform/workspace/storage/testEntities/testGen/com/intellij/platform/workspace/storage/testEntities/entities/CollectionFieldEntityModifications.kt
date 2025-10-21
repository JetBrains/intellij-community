// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CollectionFieldEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet

@GeneratedCodeApiVersion(3)
interface CollectionFieldEntityBuilder : WorkspaceEntityBuilder<CollectionFieldEntity> {
  override var entitySource: EntitySource
  var versions: MutableSet<Int>
  var names: MutableList<String>
}

internal object CollectionFieldEntityType : EntityType<CollectionFieldEntity, CollectionFieldEntityBuilder>() {
  override val entityClass: Class<CollectionFieldEntity> get() = CollectionFieldEntity::class.java
  operator fun invoke(
    versions: Set<Int>,
    names: List<String>,
    entitySource: EntitySource,
    init: (CollectionFieldEntityBuilder.() -> Unit)? = null,
  ): CollectionFieldEntityBuilder {
    val builder = builder()
    builder.versions = versions.toMutableWorkspaceSet()
    builder.names = names.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyCollectionFieldEntity(
  entity: CollectionFieldEntity,
  modification: CollectionFieldEntityBuilder.() -> Unit,
): CollectionFieldEntity = modifyEntity(CollectionFieldEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createCollectionFieldEntity")
fun CollectionFieldEntity(
  versions: Set<Int>,
  names: List<String>,
  entitySource: EntitySource,
  init: (CollectionFieldEntityBuilder.() -> Unit)? = null,
): CollectionFieldEntityBuilder = CollectionFieldEntityType(versions, names, entitySource, init)
