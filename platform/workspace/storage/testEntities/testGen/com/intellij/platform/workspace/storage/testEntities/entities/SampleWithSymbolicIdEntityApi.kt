// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableSampleWithSymbolicIdEntity : ModifiableWorkspaceEntity<SampleWithSymbolicIdEntity> {
  override var entitySource: EntitySource
  var booleanProperty: Boolean
  var stringProperty: String
  var stringListProperty: MutableList<String>
  var stringMapProperty: Map<String, String>
  var fileProperty: VirtualFileUrl
  var children: List<ModifiableChildWpidSampleEntity>
  var nullableData: String?
}

internal object SampleWithSymbolicIdEntityType : EntityType<SampleWithSymbolicIdEntity, ModifiableSampleWithSymbolicIdEntity>() {
  override val entityClass: Class<SampleWithSymbolicIdEntity> get() = SampleWithSymbolicIdEntity::class.java
  operator fun invoke(
    booleanProperty: Boolean,
    stringProperty: String,
    stringListProperty: List<String>,
    stringMapProperty: Map<String, String>,
    fileProperty: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableSampleWithSymbolicIdEntity.() -> Unit)? = null,
  ): ModifiableSampleWithSymbolicIdEntity {
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

fun MutableEntityStorage.modifySampleWithSymbolicIdEntity(
  entity: SampleWithSymbolicIdEntity,
  modification: ModifiableSampleWithSymbolicIdEntity.() -> Unit,
): SampleWithSymbolicIdEntity = modifyEntity(ModifiableSampleWithSymbolicIdEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSampleWithSymbolicIdEntity")
fun SampleWithSymbolicIdEntity(
  booleanProperty: Boolean,
  stringProperty: String,
  stringListProperty: List<String>,
  stringMapProperty: Map<String, String>,
  fileProperty: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableSampleWithSymbolicIdEntity.() -> Unit)? = null,
): ModifiableSampleWithSymbolicIdEntity =
  SampleWithSymbolicIdEntityType(booleanProperty, stringProperty, stringListProperty, stringMapProperty, fileProperty, entitySource, init)
