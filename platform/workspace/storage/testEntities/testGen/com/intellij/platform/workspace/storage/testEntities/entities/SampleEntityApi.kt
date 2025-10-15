// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
interface ModifiableSampleEntity : ModifiableWorkspaceEntity<SampleEntity> {
  override var entitySource: EntitySource
  var booleanProperty: Boolean
  var stringProperty: String
  var stringListProperty: MutableList<String>
  var stringMapProperty: Map<String, String>
  var fileProperty: VirtualFileUrl
  var children: List<ModifiableChildSampleEntity>
  var nullableData: String?
  var randomUUID: UUID?
}

internal object SampleEntityType : EntityType<SampleEntity, ModifiableSampleEntity>() {
  override val entityClass: Class<SampleEntity> get() = SampleEntity::class.java
  operator fun invoke(
    booleanProperty: Boolean,
    stringProperty: String,
    stringListProperty: List<String>,
    stringMapProperty: Map<String, String>,
    fileProperty: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableSampleEntity.() -> Unit)? = null,
  ): ModifiableSampleEntity {
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
  modification: ModifiableSampleEntity.() -> Unit,
): SampleEntity = modifyEntity(ModifiableSampleEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSampleEntity")
fun SampleEntity(
  booleanProperty: Boolean,
  stringProperty: String,
  stringListProperty: List<String>,
  stringMapProperty: Map<String, String>,
  fileProperty: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableSampleEntity.() -> Unit)? = null,
): ModifiableSampleEntity =
  SampleEntityType(booleanProperty, stringProperty, stringListProperty, stringMapProperty, fileProperty, entitySource, init)
