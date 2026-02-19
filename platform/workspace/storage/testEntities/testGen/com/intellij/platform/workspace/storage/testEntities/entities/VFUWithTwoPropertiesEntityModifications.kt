// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VFUWithTwoPropertiesEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface VFUWithTwoPropertiesEntityBuilder : WorkspaceEntityBuilder<VFUWithTwoPropertiesEntity> {
  override var entitySource: EntitySource
  var data: String
  var fileProperty: VirtualFileUrl
  var secondFileProperty: VirtualFileUrl
}

internal object VFUWithTwoPropertiesEntityType : EntityType<VFUWithTwoPropertiesEntity, VFUWithTwoPropertiesEntityBuilder>() {
  override val entityClass: Class<VFUWithTwoPropertiesEntity> get() = VFUWithTwoPropertiesEntity::class.java
  operator fun invoke(
    data: String,
    fileProperty: VirtualFileUrl,
    secondFileProperty: VirtualFileUrl,
    entitySource: EntitySource,
    init: (VFUWithTwoPropertiesEntityBuilder.() -> Unit)? = null,
  ): VFUWithTwoPropertiesEntityBuilder {
    val builder = builder()
    builder.data = data
    builder.fileProperty = fileProperty
    builder.secondFileProperty = secondFileProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyVFUWithTwoPropertiesEntity(
  entity: VFUWithTwoPropertiesEntity,
  modification: VFUWithTwoPropertiesEntityBuilder.() -> Unit,
): VFUWithTwoPropertiesEntity = modifyEntity(VFUWithTwoPropertiesEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createVFUWithTwoPropertiesEntity")
fun VFUWithTwoPropertiesEntity(
  data: String,
  fileProperty: VirtualFileUrl,
  secondFileProperty: VirtualFileUrl,
  entitySource: EntitySource,
  init: (VFUWithTwoPropertiesEntityBuilder.() -> Unit)? = null,
): VFUWithTwoPropertiesEntityBuilder = VFUWithTwoPropertiesEntityType(data, fileProperty, secondFileProperty, entitySource, init)
