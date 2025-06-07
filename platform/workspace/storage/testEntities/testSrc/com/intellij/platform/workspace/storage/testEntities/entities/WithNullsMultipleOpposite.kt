// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentWithNullsOppositeMultiple : WorkspaceEntity {
  val parentData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentWithNullsOppositeMultiple> {
    override var entitySource: EntitySource
    var parentData: String
  }

  companion object : EntityType<ParentWithNullsOppositeMultiple, Builder>() {
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
fun MutableEntityStorage.modifyParentWithNullsOppositeMultiple(
  entity: ParentWithNullsOppositeMultiple,
  modification: ParentWithNullsOppositeMultiple.Builder.() -> Unit,
): ParentWithNullsOppositeMultiple {
  return modifyEntity(ParentWithNullsOppositeMultiple.Builder::class.java, entity, modification)
}

var ParentWithNullsOppositeMultiple.Builder.children: @Child List<ChildWithNullsOppositeMultiple.Builder>
  by WorkspaceEntity.extensionBuilder(ChildWithNullsOppositeMultiple::class.java)
//endregion


interface ChildWithNullsOppositeMultiple : WorkspaceEntity {
  val childData: String

  val parentEntity: ParentWithNullsOppositeMultiple?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildWithNullsOppositeMultiple> {
    override var entitySource: EntitySource
    var childData: String
    var parentEntity: ParentWithNullsOppositeMultiple.Builder?
  }

  companion object : EntityType<ChildWithNullsOppositeMultiple, Builder>() {
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
fun MutableEntityStorage.modifyChildWithNullsOppositeMultiple(
  entity: ChildWithNullsOppositeMultiple,
  modification: ChildWithNullsOppositeMultiple.Builder.() -> Unit,
): ChildWithNullsOppositeMultiple {
  return modifyEntity(ChildWithNullsOppositeMultiple.Builder::class.java, entity, modification)
}
//endregion


val ParentWithNullsOppositeMultiple.children: List<@Child ChildWithNullsOppositeMultiple>
    by WorkspaceEntity.extension()
