// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child



interface ParentWithNullsMultiple : WorkspaceEntity {
  val parentData: String

  @Child
  val children: List<ChildWithNullsMultiple>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ParentWithNullsMultiple, WorkspaceEntity.Builder<ParentWithNullsMultiple> {
    override var entitySource: EntitySource
    override var parentData: String
    override var children: List<ChildWithNullsMultiple>
  }

  companion object : EntityType<ParentWithNullsMultiple, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildWithNullsMultiple, WorkspaceEntity.Builder<ChildWithNullsMultiple> {
    override var entitySource: EntitySource
    override var childData: String
  }

  companion object : EntityType<ChildWithNullsMultiple, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
