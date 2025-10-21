// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SoftLinkReferencedChildModifications")

package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface SoftLinkReferencedChildBuilder : WorkspaceEntityBuilder<SoftLinkReferencedChild> {
  override var entitySource: EntitySource
  var parentEntity: EntityWithSoftLinksBuilder
}

internal object SoftLinkReferencedChildType : EntityType<SoftLinkReferencedChild, SoftLinkReferencedChildBuilder>() {
  override val entityClass: Class<SoftLinkReferencedChild> get() = SoftLinkReferencedChild::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (SoftLinkReferencedChildBuilder.() -> Unit)? = null,
  ): SoftLinkReferencedChildBuilder {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySoftLinkReferencedChild(
  entity: SoftLinkReferencedChild,
  modification: SoftLinkReferencedChildBuilder.() -> Unit,
): SoftLinkReferencedChild = modifyEntity(SoftLinkReferencedChildBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSoftLinkReferencedChild")
fun SoftLinkReferencedChild(
  entitySource: EntitySource,
  init: (SoftLinkReferencedChildBuilder.() -> Unit)? = null,
): SoftLinkReferencedChildBuilder = SoftLinkReferencedChildType(entitySource, init)
