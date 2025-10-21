// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OneToOneRefEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface OneToOneRefEntityBuilder : WorkspaceEntityBuilder<OneToOneRefEntity> {
  override var entitySource: EntitySource
  var version: Int
  var text: String
  var anotherEntity: AnotherOneToOneRefEntityBuilder?
}

internal object OneToOneRefEntityType : EntityType<OneToOneRefEntity, OneToOneRefEntityBuilder>() {
  override val entityClass: Class<OneToOneRefEntity> get() = OneToOneRefEntity::class.java
  operator fun invoke(
    version: Int,
    text: String,
    entitySource: EntitySource,
    init: (OneToOneRefEntityBuilder.() -> Unit)? = null,
  ): OneToOneRefEntityBuilder {
    val builder = builder()
    builder.version = version
    builder.text = text
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneToOneRefEntity(
  entity: OneToOneRefEntity,
  modification: OneToOneRefEntityBuilder.() -> Unit,
): OneToOneRefEntity = modifyEntity(OneToOneRefEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneToOneRefEntity")
fun OneToOneRefEntity(
  version: Int,
  text: String,
  entitySource: EntitySource,
  init: (OneToOneRefEntityBuilder.() -> Unit)? = null,
): OneToOneRefEntityBuilder = OneToOneRefEntityType(version, text, entitySource, init)
