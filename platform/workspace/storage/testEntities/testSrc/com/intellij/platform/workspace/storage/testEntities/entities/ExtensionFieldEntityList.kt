// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface MainEntityList : WorkspaceEntity {
  val x: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<MainEntityList> {
    override var entitySource: EntitySource
    var x: String
  }

  companion object : EntityType<MainEntityList, Builder>() {
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
fun MutableEntityStorage.modifyMainEntityList(
  entity: MainEntityList,
  modification: MainEntityList.Builder.() -> Unit,
): MainEntityList {
  return modifyEntity(MainEntityList.Builder::class.java, entity, modification)
}

var MainEntityList.Builder.child: @Child List<AttachedEntityList.Builder>
  by WorkspaceEntity.extensionBuilder(AttachedEntityList::class.java)
//endregion

interface AttachedEntityList : WorkspaceEntity {
  val ref: MainEntityList?
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<AttachedEntityList> {
    override var entitySource: EntitySource
    var ref: MainEntityList.Builder?
    var data: String
  }

  companion object : EntityType<AttachedEntityList, Builder>() {
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
fun MutableEntityStorage.modifyAttachedEntityList(
  entity: AttachedEntityList,
  modification: AttachedEntityList.Builder.() -> Unit,
): AttachedEntityList {
  return modifyEntity(AttachedEntityList.Builder::class.java, entity, modification)
}
//endregion

val MainEntityList.child: List<@Child AttachedEntityList>
    by WorkspaceEntity.extension()
