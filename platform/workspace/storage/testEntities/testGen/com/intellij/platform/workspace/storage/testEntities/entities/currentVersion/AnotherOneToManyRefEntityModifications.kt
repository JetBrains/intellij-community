// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AnotherOneToManyRefEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface AnotherOneToManyRefEntityBuilder : WorkspaceEntityBuilder<AnotherOneToManyRefEntity> {
  override var entitySource: EntitySource
  var parentEntity: OneToManyRefEntityBuilder
  var version: Int
  var someData: OneToManyRefDataClass
}

internal object AnotherOneToManyRefEntityType : EntityType<AnotherOneToManyRefEntity, AnotherOneToManyRefEntityBuilder>() {
  override val entityClass: Class<AnotherOneToManyRefEntity> get() = AnotherOneToManyRefEntity::class.java
  operator fun invoke(
    version: Int,
    someData: OneToManyRefDataClass,
    entitySource: EntitySource,
    init: (AnotherOneToManyRefEntityBuilder.() -> Unit)? = null,
  ): AnotherOneToManyRefEntityBuilder {
    val builder = builder()
    builder.version = version
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAnotherOneToManyRefEntity(
  entity: AnotherOneToManyRefEntity,
  modification: AnotherOneToManyRefEntityBuilder.() -> Unit,
): AnotherOneToManyRefEntity = modifyEntity(AnotherOneToManyRefEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createAnotherOneToManyRefEntity")
fun AnotherOneToManyRefEntity(
  version: Int,
  someData: OneToManyRefDataClass,
  entitySource: EntitySource,
  init: (AnotherOneToManyRefEntityBuilder.() -> Unit)? = null,
): AnotherOneToManyRefEntityBuilder = AnotherOneToManyRefEntityType(version, someData, entitySource, init)
