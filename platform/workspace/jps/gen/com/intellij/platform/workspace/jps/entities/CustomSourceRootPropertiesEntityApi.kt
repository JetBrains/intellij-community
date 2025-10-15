// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@GeneratedCodeApiVersion(3)
interface ModifiableCustomSourceRootPropertiesEntity : ModifiableWorkspaceEntity<CustomSourceRootPropertiesEntity> {
  override var entitySource: EntitySource
  var propertiesXmlTag: String
  var sourceRoot: ModifiableSourceRootEntity
}

internal object CustomSourceRootPropertiesEntityType : EntityType<CustomSourceRootPropertiesEntity, ModifiableCustomSourceRootPropertiesEntity>() {
  override val entityClass: Class<CustomSourceRootPropertiesEntity> get() = CustomSourceRootPropertiesEntity::class.java
  operator fun invoke(
    propertiesXmlTag: String,
    entitySource: EntitySource,
    init: (ModifiableCustomSourceRootPropertiesEntity.() -> Unit)? = null,
  ): ModifiableCustomSourceRootPropertiesEntity {
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
  modification: ModifiableCustomSourceRootPropertiesEntity.() -> Unit,
): CustomSourceRootPropertiesEntity = modifyEntity(ModifiableCustomSourceRootPropertiesEntity::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createCustomSourceRootPropertiesEntity")
fun CustomSourceRootPropertiesEntity(
  propertiesXmlTag: String,
  entitySource: EntitySource,
  init: (ModifiableCustomSourceRootPropertiesEntity.() -> Unit)? = null,
): ModifiableCustomSourceRootPropertiesEntity = CustomSourceRootPropertiesEntityType(propertiesXmlTag, entitySource, init)
