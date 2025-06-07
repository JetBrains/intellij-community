// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentWithNulls : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildWithNulls?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentWithNulls> {
    override var entitySource: EntitySource
    var parentData: String
    var child: ChildWithNulls.Builder?
  }

  companion object : EntityType<ParentWithNulls, Builder>() {
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
fun MutableEntityStorage.modifyParentWithNulls(
  entity: ParentWithNulls,
  modification: ParentWithNulls.Builder.() -> Unit,
): ParentWithNulls {
  return modifyEntity(ParentWithNulls.Builder::class.java, entity, modification)
}
//endregion

interface ChildWithNulls : WorkspaceEntity {
  val childData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildWithNulls> {
    override var entitySource: EntitySource
    var childData: String
  }

  companion object : EntityType<ChildWithNulls, Builder>() {
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
fun MutableEntityStorage.modifyChildWithNulls(
  entity: ChildWithNulls,
  modification: ChildWithNulls.Builder.() -> Unit,
): ChildWithNulls {
  return modifyEntity(ChildWithNulls.Builder::class.java, entity, modification)
}

var ChildWithNulls.Builder.parentEntity: ParentWithNulls.Builder?
  by WorkspaceEntity.extensionBuilder(ParentWithNulls::class.java)
//endregion

val ChildWithNulls.parentEntity: ParentWithNulls?
    by WorkspaceEntity.extension()
