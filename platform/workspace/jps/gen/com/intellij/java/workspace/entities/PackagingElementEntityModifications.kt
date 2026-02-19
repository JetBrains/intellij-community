// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface PackagingElementEntityBuilder<T : PackagingElementEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
}

internal object PackagingElementEntityType : EntityType<PackagingElementEntity, PackagingElementEntityBuilder<PackagingElementEntity>>() {
  override val entityClass: Class<PackagingElementEntity> get() = PackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (PackagingElementEntityBuilder<PackagingElementEntity>.() -> Unit)? = null,
  ): PackagingElementEntityBuilder<PackagingElementEntity> {
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
