// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableSoftLinkReferencedChild : ModifiableWorkspaceEntity<SoftLinkReferencedChild> {
  override var entitySource: EntitySource
  var parentEntity: ModifiableEntityWithSoftLinks
}

internal object SoftLinkReferencedChildType : EntityType<SoftLinkReferencedChild, ModifiableSoftLinkReferencedChild>() {
  override val entityClass: Class<SoftLinkReferencedChild> get() = SoftLinkReferencedChild::class.java
  operator fun invoke(
    entitySource: EntitySource,
    init: (ModifiableSoftLinkReferencedChild.() -> Unit)? = null,
  ): ModifiableSoftLinkReferencedChild {
    val builder = builder()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySoftLinkReferencedChild(
  entity: SoftLinkReferencedChild,
  modification: ModifiableSoftLinkReferencedChild.() -> Unit,
): SoftLinkReferencedChild = modifyEntity(ModifiableSoftLinkReferencedChild::class.java, entity, modification)

@JvmOverloads
@JvmName("createSoftLinkReferencedChild")
fun SoftLinkReferencedChild(
  entitySource: EntitySource,
  init: (ModifiableSoftLinkReferencedChild.() -> Unit)? = null,
): ModifiableSoftLinkReferencedChild = SoftLinkReferencedChildType(entitySource, init)
