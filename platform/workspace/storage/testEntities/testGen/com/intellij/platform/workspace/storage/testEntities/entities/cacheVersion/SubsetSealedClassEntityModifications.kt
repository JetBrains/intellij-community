// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SubsetSealedClassEntityModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage

@GeneratedCodeApiVersion(3)
interface SubsetSealedClassEntityBuilder : WorkspaceEntityBuilder<SubsetSealedClassEntity> {
  override var entitySource: EntitySource
  var someData: SubsetSealedClass
}

internal object SubsetSealedClassEntityType : EntityType<SubsetSealedClassEntity, SubsetSealedClassEntityBuilder>() {
  override val entityClass: Class<SubsetSealedClassEntity> get() = SubsetSealedClassEntity::class.java
  operator fun invoke(
    someData: SubsetSealedClass,
    entitySource: EntitySource,
    init: (SubsetSealedClassEntityBuilder.() -> Unit)? = null,
  ): SubsetSealedClassEntityBuilder {
    val builder = builder()
    builder.someData = someData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySubsetSealedClassEntity(
  entity: SubsetSealedClassEntity,
  modification: SubsetSealedClassEntityBuilder.() -> Unit,
): SubsetSealedClassEntity = modifyEntity(SubsetSealedClassEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSubsetSealedClassEntity")
fun SubsetSealedClassEntity(
  someData: SubsetSealedClass,
  entitySource: EntitySource,
  init: (SubsetSealedClassEntityBuilder.() -> Unit)? = null,
): SubsetSealedClassEntityBuilder = SubsetSealedClassEntityType(someData, entitySource, init)
