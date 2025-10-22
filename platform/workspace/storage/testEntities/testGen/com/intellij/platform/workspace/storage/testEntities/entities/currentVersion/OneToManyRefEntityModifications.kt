// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OneToManyRefEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface OneToManyRefEntityBuilder : WorkspaceEntityBuilder<OneToManyRefEntity> {
  override var entitySource: EntitySource
  var someData: OneToManyRefDataClass
  var anotherEntity: AnotherOneToManyRefEntityBuilder?
}

internal object OneToManyRefEntityType : EntityType<OneToManyRefEntity, OneToManyRefEntityBuilder>() {
  override val entityClass: Class<OneToManyRefEntity> get() = OneToManyRefEntity::class.java
  operator fun invoke(
    someData: OneToManyRefDataClass,
    entitySource: EntitySource,
    init: (OneToManyRefEntityBuilder.() -> Unit)? = null,
  ): OneToManyRefEntityBuilder {
    val builder = builder()
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneToManyRefEntity(
  entity: OneToManyRefEntity,
  modification: OneToManyRefEntityBuilder.() -> Unit,
): OneToManyRefEntity = modifyEntity(OneToManyRefEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneToManyRefEntity")
fun OneToManyRefEntity(
  someData: OneToManyRefDataClass,
  entitySource: EntitySource,
  init: (OneToManyRefEntityBuilder.() -> Unit)? = null,
): OneToManyRefEntityBuilder = OneToManyRefEntityType(someData, entitySource, init)
