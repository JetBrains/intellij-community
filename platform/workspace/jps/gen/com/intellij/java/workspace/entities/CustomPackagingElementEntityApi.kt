// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableCustomPackagingElementEntity : ModifiableWorkspaceEntity<CustomPackagingElementEntity>, CompositePackagingElementEntity.Builder<CustomPackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  override var artifact: ModifiableArtifactEntity?
  override var children: List<ModifiablePackagingElementEntity<out PackagingElementEntity>>
  var typeId: String
  var propertiesXmlTag: String
}

internal object CustomPackagingElementEntityType : EntityType<CustomPackagingElementEntity, ModifiableCustomPackagingElementEntity>(
  CompositePackagingElementEntityType) {
  override val entityClass: Class<CustomPackagingElementEntity> get() = CustomPackagingElementEntity::class.java
  operator fun invoke(
    typeId: String,
    propertiesXmlTag: String,
    entitySource: EntitySource,
    init: (ModifiableCustomPackagingElementEntity.() -> Unit)? = null,
  ): ModifiableCustomPackagingElementEntity {
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
  modification: ModifiableCustomPackagingElementEntity.() -> Unit,
): CustomPackagingElementEntity = modifyEntity(ModifiableCustomPackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createCustomPackagingElementEntity")
fun CustomPackagingElementEntity(
  typeId: String,
  propertiesXmlTag: String,
  entitySource: EntitySource,
  init: (ModifiableCustomPackagingElementEntity.() -> Unit)? = null,
): ModifiableCustomPackagingElementEntity = CustomPackagingElementEntityType(typeId, propertiesXmlTag, entitySource, init)
