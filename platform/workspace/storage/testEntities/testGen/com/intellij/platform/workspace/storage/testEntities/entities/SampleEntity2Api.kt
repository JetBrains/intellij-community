// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableSampleEntity2 : ModifiableWorkspaceEntity<SampleEntity2> {
  override var entitySource: EntitySource
  var data: String
  var boolData: Boolean
  var optionalData: String?
}

internal object SampleEntity2Type : EntityType<SampleEntity2, ModifiableSampleEntity2>() {
  override val entityClass: Class<SampleEntity2> get() = SampleEntity2::class.java
  operator fun invoke(
    data: String,
    boolData: Boolean,
    entitySource: EntitySource,
    init: (ModifiableSampleEntity2.() -> Unit)? = null,
  ): ModifiableSampleEntity2 {
    val builder = builder()
    builder.data = data
    builder.boolData = boolData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySampleEntity2(
  entity: SampleEntity2,
  modification: ModifiableSampleEntity2.() -> Unit,
): SampleEntity2 = modifyEntity(ModifiableSampleEntity2::class.java, entity, modification)

@JvmOverloads
@JvmName("createSampleEntity2")
fun SampleEntity2(
  data: String,
  boolData: Boolean,
  entitySource: EntitySource,
  init: (ModifiableSampleEntity2.() -> Unit)? = null,
): ModifiableSampleEntity2 = SampleEntity2Type(data, boolData, entitySource, init)
