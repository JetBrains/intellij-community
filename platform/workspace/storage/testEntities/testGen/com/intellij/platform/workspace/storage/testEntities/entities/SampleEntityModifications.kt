// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SampleEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
interface SampleEntityBuilder : WorkspaceEntityBuilder<SampleEntity> {
  override var entitySource: EntitySource
  var booleanProperty: Boolean
  var stringProperty: String
  var stringListProperty: MutableList<String>
  var stringMapProperty: Map<String, String>
  var fileProperty: VirtualFileUrl
  var children: List<ChildSampleEntityBuilder>
  var nullableData: String?
  var randomUUID: UUID?
}

internal object SampleEntityType : EntityType<SampleEntity, SampleEntityBuilder>() {
  override val entityClass: Class<SampleEntity> get() = SampleEntity::class.java
  operator fun invoke(
    booleanProperty: Boolean,
    stringProperty: String,
    stringListProperty: List<String>,
    stringMapProperty: Map<String, String>,
    fileProperty: VirtualFileUrl,
    entitySource: EntitySource,
    init: (SampleEntityBuilder.() -> Unit)? = null,
  ): SampleEntityBuilder {
    val builder = builder()
    builder.booleanProperty = booleanProperty
    builder.stringProperty = stringProperty
    builder.stringListProperty = stringListProperty.toMutableWorkspaceList()
    builder.stringMapProperty = stringMapProperty
    builder.fileProperty = fileProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySampleEntity(
  entity: SampleEntity,
  modification: SampleEntityBuilder.() -> Unit,
): SampleEntity = modifyEntity(SampleEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSampleEntity")
fun SampleEntity(
  booleanProperty: Boolean,
  stringProperty: String,
  stringListProperty: List<String>,
  stringMapProperty: Map<String, String>,
  fileProperty: VirtualFileUrl,
  entitySource: EntitySource,
  init: (SampleEntityBuilder.() -> Unit)? = null,
): SampleEntityBuilder =
  SampleEntityType(booleanProperty, stringProperty, stringListProperty, stringMapProperty, fileProperty, entitySource, init)
