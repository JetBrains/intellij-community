// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VFUEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface VFUEntityBuilder : WorkspaceEntityBuilder<VFUEntity> {
  override var entitySource: EntitySource
  var data: String
  var fileProperty: VirtualFileUrl
}

internal object VFUEntityType : EntityType<VFUEntity, VFUEntityBuilder>() {
  override val entityClass: Class<VFUEntity> get() = VFUEntity::class.java
  operator fun invoke(
    data: String,
    fileProperty: VirtualFileUrl,
    entitySource: EntitySource,
    init: (VFUEntityBuilder.() -> Unit)? = null,
  ): VFUEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.fileProperty = fileProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyVFUEntity(
  entity: VFUEntity,
  modification: VFUEntityBuilder.() -> Unit,
): VFUEntity = modifyEntity(VFUEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createVFUEntity")
fun VFUEntity(
  data: String,
  fileProperty: VirtualFileUrl,
  entitySource: EntitySource,
  init: (VFUEntityBuilder.() -> Unit)? = null,
): VFUEntityBuilder = VFUEntityType(data, fileProperty, entitySource, init)
