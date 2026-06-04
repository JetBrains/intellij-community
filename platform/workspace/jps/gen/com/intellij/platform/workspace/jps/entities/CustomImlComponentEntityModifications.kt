// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CustomImlComponentEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.jps.entities.impl.CustomImlComponentEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface CustomImlComponentEntityBuilder : WorkspaceEntityBuilder<CustomImlComponentEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var components: Map<String, String>
}

internal object CustomImlComponentEntityType : EntityType<CustomImlComponentEntity, CustomImlComponentEntityBuilder>() {
  override val entityClass: Class<CustomImlComponentEntity> get() = CustomImlComponentEntity::class.java
  override val entityImplBuilderClass: Class<*> get() = CustomImlComponentEntityImpl.Builder::class.java
  operator fun invoke(
    components: Map<String, String>,
    entitySource: EntitySource,
    init: (CustomImlComponentEntityBuilder.() -> Unit)? = null,
  ): CustomImlComponentEntityBuilder {
    val builder = builder()
    builder.components = components
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyCustomImlComponentEntity(
  entity: CustomImlComponentEntity,
  modification: CustomImlComponentEntityBuilder.() -> Unit,
): CustomImlComponentEntity = modifyEntity(CustomImlComponentEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createCustomImlComponentEntity")
fun CustomImlComponentEntity(
  components: Map<String, String>,
  entitySource: EntitySource,
  init: (CustomImlComponentEntityBuilder.() -> Unit)? = null,
): CustomImlComponentEntityBuilder = CustomImlComponentEntityType(components, entitySource, init)
