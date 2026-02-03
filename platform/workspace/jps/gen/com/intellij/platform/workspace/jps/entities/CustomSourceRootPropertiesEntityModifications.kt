// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CustomSourceRootPropertiesEntityModifications")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface CustomSourceRootPropertiesEntityBuilder : WorkspaceEntityBuilder<CustomSourceRootPropertiesEntity> {
  override var entitySource: EntitySource
  var propertiesXmlTag: String
  var sourceRoot: SourceRootEntityBuilder
}

internal object CustomSourceRootPropertiesEntityType :
  EntityType<CustomSourceRootPropertiesEntity, CustomSourceRootPropertiesEntityBuilder>() {
  override val entityClass: Class<CustomSourceRootPropertiesEntity> get() = CustomSourceRootPropertiesEntity::class.java
  operator fun invoke(
    propertiesXmlTag: String,
    entitySource: EntitySource,
    init: (CustomSourceRootPropertiesEntityBuilder.() -> Unit)? = null,
  ): CustomSourceRootPropertiesEntityBuilder {
    val builder = builder()
    builder.propertiesXmlTag = propertiesXmlTag
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    propertiesXmlTag: String,
    entitySource: EntitySource,
    init: (CustomSourceRootPropertiesEntity.Builder.() -> Unit)? = null,
  ): CustomSourceRootPropertiesEntity.Builder {
    val builder = builder() as CustomSourceRootPropertiesEntity.Builder
    builder.propertiesXmlTag = propertiesXmlTag
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyCustomSourceRootPropertiesEntity(
  entity: CustomSourceRootPropertiesEntity,
  modification: CustomSourceRootPropertiesEntityBuilder.() -> Unit,
): CustomSourceRootPropertiesEntity = modifyEntity(CustomSourceRootPropertiesEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createCustomSourceRootPropertiesEntity")
fun CustomSourceRootPropertiesEntity(
  propertiesXmlTag: String,
  entitySource: EntitySource,
  init: (CustomSourceRootPropertiesEntityBuilder.() -> Unit)? = null,
): CustomSourceRootPropertiesEntityBuilder = CustomSourceRootPropertiesEntityType(propertiesXmlTag, entitySource, init)
