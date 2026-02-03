// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SampleEntity2Modifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SampleEntity2Builder : WorkspaceEntityBuilder<SampleEntity2> {
  override var entitySource: EntitySource
  var data: String
  var boolData: Boolean
  var optionalData: String?
}

internal object SampleEntity2Type : EntityType<SampleEntity2, SampleEntity2Builder>() {
  override val entityClass: Class<SampleEntity2> get() = SampleEntity2::class.java
  operator fun invoke(
    data: String,
    boolData: Boolean,
    entitySource: EntitySource,
    init: (SampleEntity2Builder.() -> Unit)? = null,
  ): SampleEntity2Builder {
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
  modification: SampleEntity2Builder.() -> Unit,
): SampleEntity2 = modifyEntity(SampleEntity2Builder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSampleEntity2")
fun SampleEntity2(
  data: String,
  boolData: Boolean,
  entitySource: EntitySource,
  init: (SampleEntity2Builder.() -> Unit)? = null,
): SampleEntity2Builder = SampleEntity2Type(data, boolData, entitySource, init)
