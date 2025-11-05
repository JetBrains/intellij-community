// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MainEntityToParentModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface MainEntityToParentBuilder : WorkspaceEntityBuilder<MainEntityToParent> {
  override var entitySource: EntitySource
  var x: String
  var child: AttachedEntityToParentBuilder?
  var childNullableParent: AttachedEntityToNullableParentBuilder?
}

internal object MainEntityToParentType : EntityType<MainEntityToParent, MainEntityToParentBuilder>() {
  override val entityClass: Class<MainEntityToParent> get() = MainEntityToParent::class.java
  operator fun invoke(
    x: String,
    entitySource: EntitySource,
    init: (MainEntityToParentBuilder.() -> Unit)? = null,
  ): MainEntityToParentBuilder {
    val builder = builder()
    builder.x = x
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMainEntityToParent(
  entity: MainEntityToParent,
  modification: MainEntityToParentBuilder.() -> Unit,
): MainEntityToParent = modifyEntity(MainEntityToParentBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createMainEntityToParent")
fun MainEntityToParent(
  x: String,
  entitySource: EntitySource,
  init: (MainEntityToParentBuilder.() -> Unit)? = null,
): MainEntityToParentBuilder = MainEntityToParentType(x, entitySource, init)
