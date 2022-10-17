// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage



interface ParentWithNullsMultiple : WorkspaceEntity {
  val parentData: String

  @Child
  val children: List<ChildWithNullsMultiple>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ParentWithNullsMultiple, WorkspaceEntity.Builder<ParentWithNullsMultiple>, ObjBuilder<ParentWithNullsMultiple> {
    override var entitySource: EntitySource
    override var parentData: String
    override var children: List<ChildWithNullsMultiple>
  }

  companion object : Type<ParentWithNullsMultiple, Builder>() {
    operator fun invoke(parentData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentWithNullsMultiple {
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
fun MutableEntityStorage.modifyEntity(entity: ParentWithNullsMultiple,
                                      modification: ParentWithNullsMultiple.Builder.() -> Unit) = modifyEntity(
  ParentWithNullsMultiple.Builder::class.java, entity, modification)
//endregion

interface ChildWithNullsMultiple : WorkspaceEntity {
  val childData: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildWithNullsMultiple, WorkspaceEntity.Builder<ChildWithNullsMultiple>, ObjBuilder<ChildWithNullsMultiple> {
    override var entitySource: EntitySource
    override var childData: String
  }

  companion object : Type<ChildWithNullsMultiple, Builder>() {
    operator fun invoke(childData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildWithNullsMultiple {
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
fun MutableEntityStorage.modifyEntity(entity: ChildWithNullsMultiple,
                                      modification: ChildWithNullsMultiple.Builder.() -> Unit) = modifyEntity(
  ChildWithNullsMultiple.Builder::class.java, entity, modification)

var ChildWithNullsMultiple.Builder.parent: ParentWithNullsMultiple?
  by WorkspaceEntity.extension()
//endregion

val ChildWithNullsMultiple.parent: ParentWithNullsMultiple?
    by WorkspaceEntity.extension()
