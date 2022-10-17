// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.MutableEntityStorage



interface BooleanEntity : WorkspaceEntity {
  val data: Boolean

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : BooleanEntity, WorkspaceEntity.Builder<BooleanEntity>, ObjBuilder<BooleanEntity> {
    override var entitySource: EntitySource
    override var data: Boolean
  }

  companion object : Type<BooleanEntity, Builder>() {
    operator fun invoke(data: Boolean, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): BooleanEntity {
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
fun MutableEntityStorage.modifyEntity(entity: BooleanEntity, modification: BooleanEntity.Builder.() -> Unit) = modifyEntity(
  BooleanEntity.Builder::class.java, entity, modification)
//endregion

interface IntEntity : WorkspaceEntity {
  val data: Int

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : IntEntity, WorkspaceEntity.Builder<IntEntity>, ObjBuilder<IntEntity> {
    override var entitySource: EntitySource
    override var data: Int
  }

  companion object : Type<IntEntity, Builder>() {
    operator fun invoke(data: Int, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): IntEntity {
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
fun MutableEntityStorage.modifyEntity(entity: IntEntity, modification: IntEntity.Builder.() -> Unit) = modifyEntity(
  IntEntity.Builder::class.java, entity, modification)
//endregion

interface StringEntity : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : StringEntity, WorkspaceEntity.Builder<StringEntity>, ObjBuilder<StringEntity> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : Type<StringEntity, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): StringEntity {
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
fun MutableEntityStorage.modifyEntity(entity: StringEntity, modification: StringEntity.Builder.() -> Unit) = modifyEntity(
  StringEntity.Builder::class.java, entity, modification)
//endregion

interface ListEntity : WorkspaceEntity {
  val data: List<String>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ListEntity, WorkspaceEntity.Builder<ListEntity>, ObjBuilder<ListEntity> {
    override var entitySource: EntitySource
    override var data: MutableList<String>
  }

  companion object : Type<ListEntity, Builder>() {
    operator fun invoke(data: List<String>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ListEntity {
      val builder = builder()
      builder.data = data.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ListEntity, modification: ListEntity.Builder.() -> Unit) = modifyEntity(
  ListEntity.Builder::class.java, entity, modification)
//endregion


interface OptionalIntEntity : WorkspaceEntity {
  val data: Int?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OptionalIntEntity, WorkspaceEntity.Builder<OptionalIntEntity>, ObjBuilder<OptionalIntEntity> {
    override var entitySource: EntitySource
    override var data: Int?
  }

  companion object : Type<OptionalIntEntity, Builder>() {
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OptionalIntEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OptionalIntEntity, modification: OptionalIntEntity.Builder.() -> Unit) = modifyEntity(
  OptionalIntEntity.Builder::class.java, entity, modification)
//endregion


interface OptionalStringEntity : WorkspaceEntity {
  val data: String?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : OptionalStringEntity, WorkspaceEntity.Builder<OptionalStringEntity>, ObjBuilder<OptionalStringEntity> {
    override var entitySource: EntitySource
    override var data: String?
  }

  companion object : Type<OptionalStringEntity, Builder>() {
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): OptionalStringEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: OptionalStringEntity, modification: OptionalStringEntity.Builder.() -> Unit) = modifyEntity(
  OptionalStringEntity.Builder::class.java, entity, modification)
//endregion

// Not supported at the moment
/*
interface OptionalListIntEntity : WorkspaceEntity {
  val data: List<Int>?
}
*/
