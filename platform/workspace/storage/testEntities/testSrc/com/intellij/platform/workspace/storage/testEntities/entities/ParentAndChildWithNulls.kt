// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity



interface ParentWithNulls : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildWithNulls?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ParentWithNulls, WorkspaceEntity.Builder<ParentWithNulls> {
    override var entitySource: EntitySource
    override var parentData: String
    override var child: ChildWithNulls?
  }

  companion object : EntityType<ParentWithNulls, Builder>() {
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildWithNulls, WorkspaceEntity.Builder<ChildWithNulls> {
    override var entitySource: EntitySource
    override var childData: String
  }

  companion object : EntityType<ChildWithNulls, Builder>() {
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
