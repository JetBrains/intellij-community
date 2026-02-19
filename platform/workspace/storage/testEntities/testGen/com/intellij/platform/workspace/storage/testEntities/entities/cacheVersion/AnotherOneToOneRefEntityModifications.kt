// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AnotherOneToOneRefEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface AnotherOneToOneRefEntityBuilder : WorkspaceEntityBuilder<AnotherOneToOneRefEntity> {
  override var entitySource: EntitySource
  var someString: String
  var boolean: Boolean
  var parentEntity: OneToOneRefEntityBuilder
}

internal object AnotherOneToOneRefEntityType : EntityType<AnotherOneToOneRefEntity, AnotherOneToOneRefEntityBuilder>() {
  override val entityClass: Class<AnotherOneToOneRefEntity> get() = AnotherOneToOneRefEntity::class.java
  operator fun invoke(
    someString: String,
    boolean: Boolean,
    entitySource: EntitySource,
    init: (AnotherOneToOneRefEntityBuilder.() -> Unit)? = null,
  ): AnotherOneToOneRefEntityBuilder {
    val builder = builder()
    builder.someString = someString
    builder.boolean = boolean
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyAnotherOneToOneRefEntity(
  entity: AnotherOneToOneRefEntity,
  modification: AnotherOneToOneRefEntityBuilder.() -> Unit,
): AnotherOneToOneRefEntity = modifyEntity(AnotherOneToOneRefEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createAnotherOneToOneRefEntity")
fun AnotherOneToOneRefEntity(
  someString: String,
  boolean: Boolean,
  entitySource: EntitySource,
  init: (AnotherOneToOneRefEntityBuilder.() -> Unit)? = null,
): AnotherOneToOneRefEntityBuilder = AnotherOneToOneRefEntityType(someString, boolean, entitySource, init)
