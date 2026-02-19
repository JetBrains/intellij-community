// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SetVFUEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface SetVFUEntityBuilder : WorkspaceEntityBuilder<SetVFUEntity> {
  override var entitySource: EntitySource
  var data: String
  var fileProperty: MutableSet<VirtualFileUrl>
}

internal object SetVFUEntityType : EntityType<SetVFUEntity, SetVFUEntityBuilder>() {
  override val entityClass: Class<SetVFUEntity> get() = SetVFUEntity::class.java
  operator fun invoke(
    data: String,
    fileProperty: Set<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (SetVFUEntityBuilder.() -> Unit)? = null,
  ): SetVFUEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.fileProperty = fileProperty.toMutableWorkspaceSet()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySetVFUEntity(
  entity: SetVFUEntity,
  modification: SetVFUEntityBuilder.() -> Unit,
): SetVFUEntity = modifyEntity(SetVFUEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSetVFUEntity")
fun SetVFUEntity(
  data: String,
  fileProperty: Set<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (SetVFUEntityBuilder.() -> Unit)? = null,
): SetVFUEntityBuilder = SetVFUEntityType(data, fileProperty, entitySource, init)
