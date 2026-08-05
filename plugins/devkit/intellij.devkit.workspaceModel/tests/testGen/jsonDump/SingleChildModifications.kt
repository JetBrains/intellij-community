// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SingleChildModifications")

package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.devkit.workspaceModel.jsonDump.impl.SingleChildImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface SingleChildBuilder : WorkspaceEntityBuilder<SingleChild> {
  override var entitySource: EntitySource
  var someData: String
  var parent: BaseTestEntityBuilder
}

internal object SingleChildType : EntityType<SingleChild, SingleChildBuilder>() {
  override val entityClass: Class<SingleChild> get() = SingleChild::class.java
  override val entityImplBuilderClass: Class<*> get() = SingleChildImpl.Builder::class.java
  operator fun invoke(
    someData: String,
    entitySource: EntitySource,
    init: (SingleChildBuilder.() -> Unit)? = null,
  ): SingleChildBuilder {
    val builder = builder()
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySingleChild(
  entity: SingleChild,
  modification: SingleChildBuilder.() -> Unit,
): SingleChild = modifyEntity(SingleChildBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSingleChild")
fun SingleChild(
  someData: String,
  entitySource: EntitySource,
  init: (SingleChildBuilder.() -> Unit)? = null,
): SingleChildBuilder = SingleChildType(someData, entitySource, init)
