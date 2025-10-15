// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet

@GeneratedCodeApiVersion(3)
interface ModifiableSimplePropsEntity : ModifiableWorkspaceEntity<SimplePropsEntity> {
  override var entitySource: EntitySource
  var text: String
  var list: MutableList<Int>
  var set: MutableSet<List<String>>
  var map: Map<Set<Int>, List<String>>
  var bool: Boolean
}

internal object SimplePropsEntityType : EntityType<SimplePropsEntity, ModifiableSimplePropsEntity>() {
  override val entityClass: Class<SimplePropsEntity> get() = SimplePropsEntity::class.java
  operator fun invoke(
    text: String,
    list: List<Int>,
    set: Set<List<String>>,
    map: Map<Set<Int>, List<String>>,
    bool: Boolean,
    entitySource: EntitySource,
    init: (ModifiableSimplePropsEntity.() -> Unit)? = null,
  ): ModifiableSimplePropsEntity {
    val builder = builder()
    builder.text = text
    builder.list = list.toMutableWorkspaceList()
    builder.set = set.toMutableWorkspaceSet()
    builder.map = map
    builder.bool = bool
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySimplePropsEntity(
  entity: SimplePropsEntity,
  modification: ModifiableSimplePropsEntity.() -> Unit,
): SimplePropsEntity = modifyEntity(ModifiableSimplePropsEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSimplePropsEntity")
fun SimplePropsEntity(
  text: String,
  list: List<Int>,
  set: Set<List<String>>,
  map: Map<Set<Int>, List<String>>,
  bool: Boolean,
  entitySource: EntitySource,
  init: (ModifiableSimplePropsEntity.() -> Unit)? = null,
): ModifiableSimplePropsEntity = SimplePropsEntityType(text, list, set, map, bool, entitySource, init)
