// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
interface ModifiableSdkEntity : ModifiableWorkspaceEntity<SdkEntity> {
  override var entitySource: EntitySource
  var name: String
  var type: String
  var version: String?
  var homePath: VirtualFileUrl?
  var roots: MutableList<SdkRoot>
  var additionalData: String
}

internal object SdkEntityType : EntityType<SdkEntity, ModifiableSdkEntity>() {
  override val entityClass: Class<SdkEntity> get() = SdkEntity::class.java
  operator fun invoke(
    name: String,
    type: String,
    roots: List<SdkRoot>,
    additionalData: String,
    entitySource: EntitySource,
    init: (ModifiableSdkEntity.() -> Unit)? = null,
  ): ModifiableSdkEntity {
    val builder = builder()
    builder.name = name
    builder.type = type
    builder.roots = roots.toMutableWorkspaceList()
    builder.additionalData = additionalData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    name: String,
    type: String,
    roots: List<SdkRoot>,
    additionalData: String,
    entitySource: EntitySource,
    init: (SdkEntity.Builder.() -> Unit)? = null,
  ): SdkEntity.Builder {
    val builder = builder() as SdkEntity.Builder
    builder.name = name
    builder.type = type
    builder.roots = roots.toMutableWorkspaceList()
    builder.additionalData = additionalData
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySdkEntity(
  entity: SdkEntity,
  modification: ModifiableSdkEntity.() -> Unit,
): SdkEntity = modifyEntity(ModifiableSdkEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSdkEntity")
fun SdkEntity(
  name: String,
  type: String,
  roots: List<SdkRoot>,
  additionalData: String,
  entitySource: EntitySource,
  init: (ModifiableSdkEntity.() -> Unit)? = null,
): ModifiableSdkEntity = SdkEntityType(name, type, roots, additionalData, entitySource, init)
