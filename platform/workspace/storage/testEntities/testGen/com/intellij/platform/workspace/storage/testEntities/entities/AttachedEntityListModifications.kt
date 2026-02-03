// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AttachedEntityListModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface AttachedEntityListBuilder : WorkspaceEntityBuilder<AttachedEntityList> {
  override var entitySource: EntitySource
  var ref: MainEntityListBuilder?
  var data: String
}

internal object AttachedEntityListType : EntityType<AttachedEntityList, AttachedEntityListBuilder>() {
  override val entityClass: Class<AttachedEntityList> get() = AttachedEntityList::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (AttachedEntityListBuilder.() -> Unit)? = null,
  ): AttachedEntityListBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntityList(
  entity: AttachedEntityList,
  modification: AttachedEntityListBuilder.() -> Unit,
): AttachedEntityList = modifyEntity(AttachedEntityListBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createAttachedEntityList")
fun AttachedEntityList(
  data: String,
  entitySource: EntitySource,
  init: (AttachedEntityListBuilder.() -> Unit)? = null,
): AttachedEntityListBuilder = AttachedEntityListType(data, entitySource, init)
