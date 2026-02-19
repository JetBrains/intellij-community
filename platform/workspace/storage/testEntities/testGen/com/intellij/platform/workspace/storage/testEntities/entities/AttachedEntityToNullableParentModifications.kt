// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AttachedEntityToNullableParentModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

@GeneratedCodeApiVersion(3)
interface AttachedEntityToNullableParentBuilder : WorkspaceEntityBuilder<AttachedEntityToNullableParent> {
  override var entitySource: EntitySource
  var data: String
}

internal object AttachedEntityToNullableParentType : EntityType<AttachedEntityToNullableParent, AttachedEntityToNullableParentBuilder>() {
  override val entityClass: Class<AttachedEntityToNullableParent> get() = AttachedEntityToNullableParent::class.java
  operator fun invoke(
    data: String,
    entitySource: EntitySource,
    init: (AttachedEntityToNullableParentBuilder.() -> Unit)? = null,
  ): AttachedEntityToNullableParentBuilder {
    val builder = builder()
    builder.data = data
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAttachedEntityToNullableParent(
  entity: AttachedEntityToNullableParent,
  modification: AttachedEntityToNullableParentBuilder.() -> Unit,
): AttachedEntityToNullableParent = modifyEntity(AttachedEntityToNullableParentBuilder::class.java, entity, modification)

@Parent
var AttachedEntityToNullableParentBuilder.nullableRef: MainEntityToParentBuilder?
  by WorkspaceEntity.extensionBuilder(MainEntityToParent::class.java)


@JvmOverloads
@JvmName("createAttachedEntityToNullableParent")
fun AttachedEntityToNullableParent(
  data: String,
  entitySource: EntitySource,
  init: (AttachedEntityToNullableParentBuilder.() -> Unit)? = null,
): AttachedEntityToNullableParentBuilder = AttachedEntityToNullableParentType(data, entitySource, init)
