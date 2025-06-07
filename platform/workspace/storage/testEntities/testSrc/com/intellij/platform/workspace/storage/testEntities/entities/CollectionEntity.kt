// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet


interface CollectionFieldEntity : WorkspaceEntity {
  val versions: Set<Int>
  val names: List<String>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<CollectionFieldEntity> {
    override var entitySource: EntitySource
    var versions: MutableSet<Int>
    var names: MutableList<String>
  }

  companion object : EntityType<CollectionFieldEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      versions: Set<Int>,
      names: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.versions = versions.toMutableWorkspaceSet()
      builder.names = names.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyCollectionFieldEntity(
  entity: CollectionFieldEntity,
  modification: CollectionFieldEntity.Builder.() -> Unit,
): CollectionFieldEntity {
  return modifyEntity(CollectionFieldEntity.Builder::class.java, entity, modification)
}
//endregion
