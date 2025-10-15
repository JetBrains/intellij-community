// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface ModifiableWithListSoftLinksEntity : ModifiableWorkspaceEntity<WithListSoftLinksEntity> {
  override var entitySource: EntitySource
  var myName: String
  var links: MutableList<NameId>
}

internal object WithListSoftLinksEntityType : EntityType<WithListSoftLinksEntity, ModifiableWithListSoftLinksEntity>() {
  override val entityClass: Class<WithListSoftLinksEntity> get() = WithListSoftLinksEntity::class.java
  operator fun invoke(
    myName: String,
    links: List<NameId>,
    entitySource: EntitySource,
    init: (ModifiableWithListSoftLinksEntity.() -> Unit)? = null,
  ): ModifiableWithListSoftLinksEntity {
    val builder = builder()
    builder.myName = myName
    builder.links = links.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyWithListSoftLinksEntity(
  entity: WithListSoftLinksEntity,
  modification: ModifiableWithListSoftLinksEntity.() -> Unit,
): WithListSoftLinksEntity = modifyEntity(ModifiableWithListSoftLinksEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createWithListSoftLinksEntity")
fun WithListSoftLinksEntity(
  myName: String,
  links: List<NameId>,
  entitySource: EntitySource,
  init: (ModifiableWithListSoftLinksEntity.() -> Unit)? = null,
): ModifiableWithListSoftLinksEntity = WithListSoftLinksEntityType(myName, links, entitySource, init)
