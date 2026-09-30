// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EntityWithDataClassWithVfuModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.testEntities.entities.impl.EntityWithDataClassWithVfuImpl

@GeneratedCodeApiVersion(3)
interface EntityWithDataClassWithVfuBuilder : WorkspaceEntityBuilder<EntityWithDataClassWithVfu> {
  override var entitySource: EntitySource
  var singleDataClass: DataClassWithVfus
  var listOfDataClass: MutableList<DataClassWithVfus>
}

internal object EntityWithDataClassWithVfuType : EntityType<EntityWithDataClassWithVfu, EntityWithDataClassWithVfuBuilder>() {
  override val entityImplClass: Class<*> get() = EntityWithDataClassWithVfuImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = EntityWithDataClassWithVfuImpl.Builder::class.java
  operator fun invoke(
    singleDataClass: DataClassWithVfus,
    listOfDataClass: List<DataClassWithVfus>,
    entitySource: EntitySource,
    init: (EntityWithDataClassWithVfuBuilder.() -> Unit)? = null,
  ): EntityWithDataClassWithVfuBuilder {
    val builder = builder()
    builder.singleDataClass = singleDataClass
    builder.listOfDataClass = listOfDataClass.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEntityWithDataClassWithVfu(
  entity: EntityWithDataClassWithVfu,
  modification: EntityWithDataClassWithVfuBuilder.() -> Unit,
): EntityWithDataClassWithVfu = modifyEntity(EntityWithDataClassWithVfuBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithDataClassWithVfu")
fun EntityWithDataClassWithVfu(
  singleDataClass: DataClassWithVfus,
  listOfDataClass: List<DataClassWithVfus>,
  entitySource: EntitySource,
  init: (EntityWithDataClassWithVfuBuilder.() -> Unit)? = null,
): EntityWithDataClassWithVfuBuilder = EntityWithDataClassWithVfuType(singleDataClass, listOfDataClass, entitySource, init)
