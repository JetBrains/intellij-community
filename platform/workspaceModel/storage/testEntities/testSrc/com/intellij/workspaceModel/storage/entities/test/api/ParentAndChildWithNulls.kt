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



interface ParentWithNulls : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildWithNulls?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ParentWithNulls, WorkspaceEntity.Builder<ParentWithNulls>, ObjBuilder<ParentWithNulls> {
    override var entitySource: EntitySource
    override var parentData: String
    override var child: ChildWithNulls?
  }

  companion object : Type<ParentWithNulls, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(parentData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentWithNulls {
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
fun MutableEntityStorage.modifyEntity(entity: ParentWithNulls, modification: ParentWithNulls.Builder.() -> Unit) = modifyEntity(
  ParentWithNulls.Builder::class.java, entity, modification)
//endregion

interface ChildWithNulls : WorkspaceEntity {
  val childData: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildWithNulls, WorkspaceEntity.Builder<ChildWithNulls>, ObjBuilder<ChildWithNulls> {
    override var entitySource: EntitySource
    override var childData: String
  }

  companion object : Type<ChildWithNulls, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(childData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildWithNulls {
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
fun MutableEntityStorage.modifyEntity(entity: ChildWithNulls, modification: ChildWithNulls.Builder.() -> Unit) = modifyEntity(
  ChildWithNulls.Builder::class.java, entity, modification)

var ChildWithNulls.Builder.parentEntity: ParentWithNulls?
  by WorkspaceEntity.extension()
//endregion

val ChildWithNulls.parentEntity: ParentWithNulls?
    by WorkspaceEntity.extension()
