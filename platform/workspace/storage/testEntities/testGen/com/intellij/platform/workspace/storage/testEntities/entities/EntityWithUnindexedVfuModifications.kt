// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EntityWithUnindexedVfuModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.impl.EntityWithUnindexedVfuImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface EntityWithUnindexedVfuBuilder : WorkspaceEntityBuilder<EntityWithUnindexedVfu> {
  override var entitySource: EntitySource
  var indexedVfu: VirtualFileUrl
  var unindexedVfu: VirtualFileUrl
  var indexedDataClass: DataClassWithVfus
  var unindexedDataClass: DataClassWithVfus
}

internal object EntityWithUnindexedVfuType : EntityType<EntityWithUnindexedVfu, EntityWithUnindexedVfuBuilder>() {
  override val entityImplClass: Class<*> get() = EntityWithUnindexedVfuImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = EntityWithUnindexedVfuImpl.Builder::class.java
  operator fun invoke(
    indexedVfu: VirtualFileUrl,
    unindexedVfu: VirtualFileUrl,
    indexedDataClass: DataClassWithVfus,
    unindexedDataClass: DataClassWithVfus,
    entitySource: EntitySource,
    init: (EntityWithUnindexedVfuBuilder.() -> Unit)? = null,
  ): EntityWithUnindexedVfuBuilder {
    val builder = builder()
    builder.indexedVfu = indexedVfu
    builder.unindexedVfu = unindexedVfu
    builder.indexedDataClass = indexedDataClass
    builder.unindexedDataClass = unindexedDataClass
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyEntityWithUnindexedVfu(
  entity: EntityWithUnindexedVfu,
  modification: EntityWithUnindexedVfuBuilder.() -> Unit,
): EntityWithUnindexedVfu = modifyEntity(EntityWithUnindexedVfuBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createEntityWithUnindexedVfu")
fun EntityWithUnindexedVfu(
  indexedVfu: VirtualFileUrl,
  unindexedVfu: VirtualFileUrl,
  indexedDataClass: DataClassWithVfus,
  unindexedDataClass: DataClassWithVfus,
  entitySource: EntitySource,
  init: (EntityWithUnindexedVfuBuilder.() -> Unit)? = null,
): EntityWithUnindexedVfuBuilder =
  EntityWithUnindexedVfuType(indexedVfu, unindexedVfu, indexedDataClass, unindexedDataClass, entitySource, init)
