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
interface ModifiablePackagingElementEntity<T : PackagingElementEntity> : ModifiableWorkspaceEntity<T> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableCompositePackagingElementEntity<out CompositePackagingElementEntity>?
}

internal object PackagingElementEntityType : EntityType<PackagingElementEntity, ModifiablePackagingElementEntity<PackagingElementEntity>>() {
  override val entityClass: Class<PackagingElementEntity> get() = PackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiablePackagingElementEntity<PackagingElementEntity>.() -> Unit)? = null,
  ): ModifiablePackagingElementEntity<PackagingElementEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (PackagingElementEntity.Builder<PackagingElementEntity>.() -> Unit)? = null,
  ): PackagingElementEntity.Builder<PackagingElementEntity> {
    val builder = builder() as PackagingElementEntity.Builder<PackagingElementEntity>
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
