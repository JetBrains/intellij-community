// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

interface ModuleTestEntity : WorkspaceEntityWithPersistentId {
  val name: String

  val contentRoots: List<@Child ContentRootTestEntity>

  override val persistentId: PersistentEntityId<WorkspaceEntityWithPersistentId>
    get() = ModuleTestEntityPersistentId(name)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ModuleTestEntity, ModifiableWorkspaceEntity<ModuleTestEntity>, ObjBuilder<ModuleTestEntity> {
    override var name: String
    override var entitySource: EntitySource
    override var contentRoots: List<ContentRootTestEntity>
  }

  companion object : Type<ModuleTestEntity, Builder>() {
    operator fun invoke(name: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ModuleTestEntity {
      val builder = builder()
      builder.name = name
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ModuleTestEntity, modification: ModuleTestEntity.Builder.() -> Unit) = modifyEntity(
  ModuleTestEntity.Builder::class.java, entity, modification)
//endregion

interface ContentRootTestEntity : WorkspaceEntity {
  val module: ModuleTestEntity
  val sourceRootOrder: @Child SourceRootTestOrderEntity?
  val sourceRoots: List<@Child SourceRootTestEntity>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ContentRootTestEntity, ModifiableWorkspaceEntity<ContentRootTestEntity>, ObjBuilder<ContentRootTestEntity> {
    override var module: ModuleTestEntity
    override var entitySource: EntitySource
    override var sourceRootOrder: SourceRootTestOrderEntity?
    override var sourceRoots: List<SourceRootTestEntity>
  }

  companion object : Type<ContentRootTestEntity, Builder>() {
    operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ContentRootTestEntity {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ContentRootTestEntity, modification: ContentRootTestEntity.Builder.() -> Unit) = modifyEntity(
  ContentRootTestEntity.Builder::class.java, entity, modification)
//endregion

interface SourceRootTestOrderEntity : WorkspaceEntity {
  val data: String
  val contentRoot: ContentRootTestEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SourceRootTestOrderEntity, ModifiableWorkspaceEntity<SourceRootTestOrderEntity>, ObjBuilder<SourceRootTestOrderEntity> {
    override var data: String
    override var entitySource: EntitySource
    override var contentRoot: ContentRootTestEntity
  }

  companion object : Type<SourceRootTestOrderEntity, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SourceRootTestOrderEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SourceRootTestOrderEntity,
                                      modification: SourceRootTestOrderEntity.Builder.() -> Unit) = modifyEntity(
  SourceRootTestOrderEntity.Builder::class.java, entity, modification)
//endregion

interface SourceRootTestEntity : WorkspaceEntity {
  val data: String
  val contentRoot: ContentRootTestEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SourceRootTestEntity, ModifiableWorkspaceEntity<SourceRootTestEntity>, ObjBuilder<SourceRootTestEntity> {
    override var data: String
    override var entitySource: EntitySource
    override var contentRoot: ContentRootTestEntity
  }

  companion object : Type<SourceRootTestEntity, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SourceRootTestEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SourceRootTestEntity, modification: SourceRootTestEntity.Builder.() -> Unit) = modifyEntity(
  SourceRootTestEntity.Builder::class.java, entity, modification)
//endregion

data class ModuleTestEntityPersistentId(val name: String) : PersistentEntityId<ModuleTestEntity> {
  override val presentableName: String
    get() = name
}