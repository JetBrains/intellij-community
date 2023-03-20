// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity



interface ParentWithNullsOppositeMultiple : WorkspaceEntity {
  val parentData: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ParentWithNullsOppositeMultiple, WorkspaceEntity.Builder<ParentWithNullsOppositeMultiple>, ObjBuilder<ParentWithNullsOppositeMultiple> {
    override var entitySource: EntitySource
    override var parentData: String
  }

  companion object : Type<ParentWithNullsOppositeMultiple, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(parentData: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ParentWithNullsOppositeMultiple {
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
fun MutableEntityStorage.modifyEntity(entity: ParentWithNullsOppositeMultiple,
                                      modification: ParentWithNullsOppositeMultiple.Builder.() -> Unit) = modifyEntity(
  ParentWithNullsOppositeMultiple.Builder::class.java, entity, modification)

var ParentWithNullsOppositeMultiple.Builder.children: @Child List<ChildWithNullsOppositeMultiple>
  by WorkspaceEntity.extension()
//endregion


interface ChildWithNullsOppositeMultiple : WorkspaceEntity {
  val childData: String

  val parentEntity: ParentWithNullsOppositeMultiple?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildWithNullsOppositeMultiple, WorkspaceEntity.Builder<ChildWithNullsOppositeMultiple>, ObjBuilder<ChildWithNullsOppositeMultiple> {
    override var entitySource: EntitySource
    override var childData: String
    override var parentEntity: ParentWithNullsOppositeMultiple?
  }

  companion object : Type<ChildWithNullsOppositeMultiple, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(childData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildWithNullsOppositeMultiple {
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
fun MutableEntityStorage.modifyEntity(entity: ChildWithNullsOppositeMultiple,
                                      modification: ChildWithNullsOppositeMultiple.Builder.() -> Unit) = modifyEntity(
  ChildWithNullsOppositeMultiple.Builder::class.java, entity, modification)
//endregion


val ParentWithNullsOppositeMultiple.children: List<@Child ChildWithNullsOppositeMultiple>
    by WorkspaceEntity.extension()
