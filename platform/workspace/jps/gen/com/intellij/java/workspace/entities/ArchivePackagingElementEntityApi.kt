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
interface ModifiableArchivePackagingElementEntity : ModifiableWorkspaceEntity<ArchivePackagingElementEntity>, CompositePackagingElementEntity.Builder<ArchivePackagingElementEntity> {
  override var entitySource: EntitySource
  override var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
  override var artifact: ModifiableArtifactEntity?
  override var children: List<ModifiablePackagingElementEntity<out PackagingElementEntity>>
  var fileName: String
}

internal object ArchivePackagingElementEntityType : EntityType<ArchivePackagingElementEntity, ModifiableArchivePackagingElementEntity>(
  CompositePackagingElementEntityType) {
  override val entityClass: Class<ArchivePackagingElementEntity> get() = ArchivePackagingElementEntity::class.java
  operator fun invoke(
    fileName: String,
    entitySource: EntitySource,
    init: (ModifiableArchivePackagingElementEntity.() -> Unit)? = null,
  ): ModifiableArchivePackagingElementEntity {
    val builder = builder()
    builder.fileName = fileName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    fileName: String,
    entitySource: EntitySource,
    init: (ArchivePackagingElementEntity.Builder.() -> Unit)? = null,
  ): ArchivePackagingElementEntity.Builder {
    val builder = builder() as ArchivePackagingElementEntity.Builder
    builder.fileName = fileName
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyArchivePackagingElementEntity(
  entity: ArchivePackagingElementEntity,
  modification: ModifiableArchivePackagingElementEntity.() -> Unit,
): ArchivePackagingElementEntity = modifyEntity(ModifiableArchivePackagingElementEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createArchivePackagingElementEntity")
fun ArchivePackagingElementEntity(
  fileName: String,
  entitySource: EntitySource,
  init: (ModifiableArchivePackagingElementEntity.() -> Unit)? = null,
): ModifiableArchivePackagingElementEntity = ArchivePackagingElementEntityType(fileName, entitySource, init)
