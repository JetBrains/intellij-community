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



interface MainEntityParentList : WorkspaceEntity {
  val x: String
  val children: List<@Child AttachedEntityParentList>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : MainEntityParentList, WorkspaceEntity.Builder<MainEntityParentList> {
    override var entitySource: EntitySource
    override var x: String
    override var children: List<AttachedEntityParentList>
  }

  companion object : EntityType<MainEntityParentList, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(x: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MainEntityParentList {
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
fun MutableEntityStorage.modifyEntity(entity: MainEntityParentList, modification: MainEntityParentList.Builder.() -> Unit) = modifyEntity(
  MainEntityParentList.Builder::class.java, entity, modification)
//endregion

interface AttachedEntityParentList : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : AttachedEntityParentList, WorkspaceEntity.Builder<AttachedEntityParentList> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : EntityType<AttachedEntityParentList, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AttachedEntityParentList {
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
fun MutableEntityStorage.modifyEntity(entity: AttachedEntityParentList,
                                      modification: AttachedEntityParentList.Builder.() -> Unit) = modifyEntity(
  AttachedEntityParentList.Builder::class.java, entity, modification)

var AttachedEntityParentList.Builder.ref: MainEntityParentList?
  by WorkspaceEntity.extension()
//endregion

val AttachedEntityParentList.ref: MainEntityParentList?
    by WorkspaceEntity.extension()
