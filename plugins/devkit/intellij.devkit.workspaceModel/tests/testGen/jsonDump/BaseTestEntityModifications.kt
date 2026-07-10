// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BaseTestEntityModifications")

package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.devkit.workspaceModel.jsonDump.impl.BaseTestEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface BaseTestEntityBuilder : WorkspaceEntityBuilder<BaseTestEntity> {
  override var entitySource: EntitySource
  var name: String
  var children: List<ChildEntityBuilder>
  var singleChild: SingleChildBuilder?
  var listOfAbstract: MutableList<AbstractClass>
}

internal object BaseTestEntityType : EntityType<BaseTestEntity, BaseTestEntityBuilder>() {
  override val entityClass: Class<BaseTestEntity> get() = BaseTestEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = BaseTestEntityImpl.Builder::class.java
  operator fun invoke(
    name: String,
    listOfAbstract: List<AbstractClass>,
    entitySource: EntitySource,
    init: (BaseTestEntityBuilder.() -> Unit)? = null,
  ): BaseTestEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.listOfAbstract = listOfAbstract.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyBaseTestEntity(
  entity: BaseTestEntity,
  modification: BaseTestEntityBuilder.() -> Unit,
): BaseTestEntity = modifyEntity(BaseTestEntityBuilder::class.java, entity, modification)

var BaseTestEntityBuilder.extensionChildren: List<ExtensionChildEntityBuilder>
  by WorkspaceEntity.extensionBuilder(ExtensionChildEntity::class.java)


@JvmOverloads
@JvmName("createBaseTestEntity")
fun BaseTestEntity(
  name: String,
  listOfAbstract: List<AbstractClass>,
  entitySource: EntitySource,
  init: (BaseTestEntityBuilder.() -> Unit)? = null,
): BaseTestEntityBuilder = BaseTestEntityType(name, listOfAbstract, entitySource, init)
