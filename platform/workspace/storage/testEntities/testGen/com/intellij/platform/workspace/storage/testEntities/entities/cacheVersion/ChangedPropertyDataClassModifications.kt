// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChangedPropertyDataClassModifications")

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl.ChangedPropertyDataClassImpl

@GeneratedCodeApiVersion(3)
interface ChangedPropertyDataClassBuilder : WorkspaceEntityBuilder<ChangedPropertyDataClass> {
  override var entitySource: EntitySource
  var text: String
  var propertyToChange: SpecialDataClass
}

internal object ChangedPropertyDataClassType : EntityType<ChangedPropertyDataClass, ChangedPropertyDataClassBuilder>() {
  override val entityImplClass: Class<*> get() = ChangedPropertyDataClassImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = ChangedPropertyDataClassImpl.Builder::class.java
  operator fun invoke(
    text: String,
    propertyToChange: SpecialDataClass,
    entitySource: EntitySource,
    init: (ChangedPropertyDataClassBuilder.() -> Unit)? = null,
  ): ChangedPropertyDataClassBuilder {
    val builder = builder()
    builder.text = text
    builder.propertyToChange = propertyToChange
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChangedPropertyDataClass(
  entity: ChangedPropertyDataClass,
  modification: ChangedPropertyDataClassBuilder.() -> Unit,
): ChangedPropertyDataClass = modifyEntity(ChangedPropertyDataClassBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChangedPropertyDataClass")
fun ChangedPropertyDataClass(
  text: String,
  propertyToChange: SpecialDataClass,
  entitySource: EntitySource,
  init: (ChangedPropertyDataClassBuilder.() -> Unit)? = null,
): ChangedPropertyDataClassBuilder = ChangedPropertyDataClassType(text, propertyToChange, entitySource, init)
