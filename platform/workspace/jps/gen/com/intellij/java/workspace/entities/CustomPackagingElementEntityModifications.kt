// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CustomPackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface CustomPackagingElementEntityBuilder : WorkspaceEntityBuilder<CustomPackagingElementEntity>,
                                                CompositePackagingElementEntity.Builder<CustomPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  override var artifact: ArtifactEntityBuilder?
  override var children: List<PackagingElementEntityBuilder<out PackagingElementEntity>>
  var typeId: String
  var propertiesXmlTag: String
}

internal object CustomPackagingElementEntityType : EntityType<CustomPackagingElementEntity, CustomPackagingElementEntityBuilder>() {
  override val entityClass: Class<CustomPackagingElementEntity> get() = CustomPackagingElementEntity::class.java
  operator fun invoke(
    typeId: String,
    propertiesXmlTag: String,
    entitySource: EntitySource,
    init: (CustomPackagingElementEntityBuilder.() -> Unit)? = null,
  ): CustomPackagingElementEntityBuilder {
    val builder = builder()
    builder.typeId = typeId
    builder.propertiesXmlTag = propertiesXmlTag
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    typeId: String,
    propertiesXmlTag: String,
    entitySource: EntitySource,
    init: (CustomPackagingElementEntity.Builder.() -> Unit)? = null,
  ): CustomPackagingElementEntity.Builder {
    val builder = builder() as CustomPackagingElementEntity.Builder
    builder.typeId = typeId
    builder.propertiesXmlTag = propertiesXmlTag
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyCustomPackagingElementEntity(
  entity: CustomPackagingElementEntity,
  modification: CustomPackagingElementEntityBuilder.() -> Unit,
): CustomPackagingElementEntity = modifyEntity(CustomPackagingElementEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createCustomPackagingElementEntity")
fun CustomPackagingElementEntity(
  typeId: String,
  propertiesXmlTag: String,
  entitySource: EntitySource,
  init: (CustomPackagingElementEntityBuilder.() -> Unit)? = null,
): CustomPackagingElementEntityBuilder = CustomPackagingElementEntityType(typeId, propertiesXmlTag, entitySource, init)
