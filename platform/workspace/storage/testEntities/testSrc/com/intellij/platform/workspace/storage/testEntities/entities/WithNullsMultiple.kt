// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentWithNullsMultiple : WorkspaceEntity {
  val parentData: String

  @Child
  val children: List<ChildWithNullsMultiple>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentWithNullsMultiple> {
    override var entitySource: EntitySource
    var parentData: String
    var children: List<ChildWithNullsMultiple.Builder>
  }

  companion object : EntityType<ParentWithNullsMultiple, Builder>() {
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
fun MutableEntityStorage.modifyParentWithNullsMultiple(
  entity: ParentWithNullsMultiple,
  modification: ParentWithNullsMultiple.Builder.() -> Unit,
): ParentWithNullsMultiple {
  return modifyEntity(ParentWithNullsMultiple.Builder::class.java, entity, modification)
}
//endregion

interface ChildWithNullsMultiple : WorkspaceEntity {
  val childData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildWithNullsMultiple> {
    override var entitySource: EntitySource
    var childData: String
  }

  companion object : EntityType<ChildWithNullsMultiple, Builder>() {
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
fun MutableEntityStorage.modifyChildWithNullsMultiple(
  entity: ChildWithNullsMultiple,
  modification: ChildWithNullsMultiple.Builder.() -> Unit,
): ChildWithNullsMultiple {
  return modifyEntity(ChildWithNullsMultiple.Builder::class.java, entity, modification)
}

var ChildWithNullsMultiple.Builder.parent: ParentWithNullsMultiple.Builder?
  by WorkspaceEntity.extensionBuilder(ParentWithNullsMultiple::class.java)
//endregion

val ChildWithNullsMultiple.parent: ParentWithNullsMultiple?
    by WorkspaceEntity.extension()
