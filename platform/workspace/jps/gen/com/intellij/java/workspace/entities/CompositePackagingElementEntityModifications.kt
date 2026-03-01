// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CompositePackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface CompositePackagingElementEntityBuilder<T : CompositePackagingElementEntity> : WorkspaceEntityBuilder<T>,
                                                                                        PackagingElementEntity.Builder<T> {
  override var entitySource: EntitySource
  override var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
  var artifact: ArtifactEntityBuilder?
  var children: List<PackagingElementEntityBuilder<out PackagingElementEntity>>
}

internal object CompositePackagingElementEntityType :
  EntityType<CompositePackagingElementEntity, CompositePackagingElementEntityBuilder<CompositePackagingElementEntity>>() {
  override val entityClass: Class<CompositePackagingElementEntity> get() = CompositePackagingElementEntity::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (CompositePackagingElementEntityBuilder<CompositePackagingElementEntity>.() -> Unit)? = null,
  ): CompositePackagingElementEntityBuilder<CompositePackagingElementEntity> {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    entitySource: EntitySource,
    init: (CompositePackagingElementEntity.Builder<CompositePackagingElementEntity>.() -> Unit)? = null,
  ): CompositePackagingElementEntity.Builder<CompositePackagingElementEntity> {
    val builder = builder() as CompositePackagingElementEntity.Builder<CompositePackagingElementEntity>
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}
