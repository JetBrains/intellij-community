// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DefaultPropEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface DefaultPropEntityBuilder : WorkspaceEntityBuilder<DefaultPropEntity> {
  override var entitySource: EntitySource
  var someString: String
  var someList: MutableList<Int>
  var constInt: Int
}

internal object DefaultPropEntityType : EntityType<DefaultPropEntity, DefaultPropEntityBuilder>() {
  override val entityClass: Class<DefaultPropEntity> get() = DefaultPropEntity::class.java
  operator fun invoke(
    someString: String,
    someList: List<Int>,
    entitySource: EntitySource,
    init: (DefaultPropEntityBuilder.() -> Unit)? = null,
  ): DefaultPropEntityBuilder {
    val builder = builder()
    builder.someString = someString
    builder.someList = someList.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyDefaultPropEntity(
  entity: DefaultPropEntity,
  modification: DefaultPropEntityBuilder.() -> Unit,
): DefaultPropEntity = modifyEntity(DefaultPropEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createDefaultPropEntity")
fun DefaultPropEntity(
  someString: String,
  someList: List<Int>,
  entitySource: EntitySource,
  init: (DefaultPropEntityBuilder.() -> Unit)? = null,
): DefaultPropEntityBuilder = DefaultPropEntityType(someString, someList, entitySource, init)
