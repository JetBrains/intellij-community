// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child



interface ParentEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ParentEntity, WorkspaceEntity.Builder<ParentEntity> {
    override var entitySource: EntitySource
    override var parentData: String
    override var child: ChildEntity?
  }

  companion object : EntityType<ParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(parentData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ParentEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ParentEntity, modification: ParentEntity.Builder.() -> Unit) = modifyEntity(
  ParentEntity.Builder::class.java, entity, modification)
//endregion

interface ChildEntity : WorkspaceEntity {
  val childData: String

  //    override val parent: ParentEntity
  val parentEntity: ParentEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildEntity, WorkspaceEntity.Builder<ChildEntity> {
    override var entitySource: EntitySource
    override var childData: String
    override var parentEntity: ParentEntity
  }

  companion object : EntityType<ChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(childData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChildEntity, modification: ChildEntity.Builder.() -> Unit) = modifyEntity(
  ChildEntity.Builder::class.java, entity, modification)
//endregion
