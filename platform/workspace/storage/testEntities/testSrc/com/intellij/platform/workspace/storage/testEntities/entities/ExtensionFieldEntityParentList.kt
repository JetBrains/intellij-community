// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface MainEntityParentList : WorkspaceEntity {
  val x: String
  val children: List<@Child AttachedEntityParentList>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<MainEntityParentList> {
    override var entitySource: EntitySource
    var x: String
    var children: List<AttachedEntityParentList.Builder>
  }

  companion object : EntityType<MainEntityParentList, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      x: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.x = x
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyMainEntityParentList(
  entity: MainEntityParentList,
  modification: MainEntityParentList.Builder.() -> Unit,
): MainEntityParentList {
  return modifyEntity(MainEntityParentList.Builder::class.java, entity, modification)
}
//endregion

interface AttachedEntityParentList : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<AttachedEntityParentList> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<AttachedEntityParentList, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyAttachedEntityParentList(
  entity: AttachedEntityParentList,
  modification: AttachedEntityParentList.Builder.() -> Unit,
): AttachedEntityParentList {
  return modifyEntity(AttachedEntityParentList.Builder::class.java, entity, modification)
}

var AttachedEntityParentList.Builder.ref: MainEntityParentList.Builder?
  by WorkspaceEntity.extensionBuilder(MainEntityParentList::class.java)
//endregion

val AttachedEntityParentList.ref: MainEntityParentList?
    by WorkspaceEntity.extension()
