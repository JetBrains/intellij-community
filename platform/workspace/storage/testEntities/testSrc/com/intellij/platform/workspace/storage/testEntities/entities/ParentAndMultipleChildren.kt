// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentMultipleEntity : WorkspaceEntity {
  val parentData: String
  val children: List<@Child ChildMultipleEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentMultipleEntity> {
    override var entitySource: EntitySource
    var parentData: String
    var children: List<ChildMultipleEntity.Builder>
  }

  companion object : EntityType<ParentMultipleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      parentData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.parentData = parentData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyParentMultipleEntity(
  entity: ParentMultipleEntity,
  modification: ParentMultipleEntity.Builder.() -> Unit,
): ParentMultipleEntity {
  return modifyEntity(ParentMultipleEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildMultipleEntity : WorkspaceEntity {
  val childData: String

  val parentEntity: ParentMultipleEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildMultipleEntity> {
    override var entitySource: EntitySource
    var childData: String
    var parentEntity: ParentMultipleEntity.Builder
  }

  companion object : EntityType<ChildMultipleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.childData = childData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyChildMultipleEntity(
  entity: ChildMultipleEntity,
  modification: ChildMultipleEntity.Builder.() -> Unit,
): ChildMultipleEntity {
  return modifyEntity(ChildMultipleEntity.Builder::class.java, entity, modification)
}
//endregion
