// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity



interface MainEntityList : WorkspaceEntity {
  val x: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : MainEntityList, WorkspaceEntity.Builder<MainEntityList> {
    override var entitySource: EntitySource
    override var x: String
  }

  companion object : EntityType<MainEntityList, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(x: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MainEntityList {
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
fun MutableEntityStorage.modifyEntity(entity: MainEntityList, modification: MainEntityList.Builder.() -> Unit) = modifyEntity(
  MainEntityList.Builder::class.java, entity, modification)

var MainEntityList.Builder.child: @Child List<AttachedEntityList>
  by WorkspaceEntity.extension()
//endregion

interface AttachedEntityList : WorkspaceEntity {
  val ref: MainEntityList?
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : AttachedEntityList, WorkspaceEntity.Builder<AttachedEntityList> {
    override var entitySource: EntitySource
    override var ref: MainEntityList?
    override var data: String
  }

  companion object : EntityType<AttachedEntityList, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AttachedEntityList {
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
fun MutableEntityStorage.modifyEntity(entity: AttachedEntityList, modification: AttachedEntityList.Builder.() -> Unit) = modifyEntity(
  AttachedEntityList.Builder::class.java, entity, modification)
//endregion

val MainEntityList.child: List<@Child AttachedEntityList>
    by WorkspaceEntity.extension()
