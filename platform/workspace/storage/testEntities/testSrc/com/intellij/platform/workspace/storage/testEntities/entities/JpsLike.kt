// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child

interface ModuleTestEntity : WorkspaceEntityWithSymbolicId {
  val name: String

  val contentRoots: List<@Child ContentRootTestEntity>
  val facets: List<@Child FacetTestEntity>

  override val symbolicId: SymbolicEntityId<WorkspaceEntityWithSymbolicId>
    get() = ModuleTestEntitySymbolicId(name)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleTestEntity, WorkspaceEntity.Builder<ModuleTestEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var contentRoots: List<ContentRootTestEntity>
    override var facets: List<FacetTestEntity>
  }

  companion object : EntityType<ModuleTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
  @GeneratedCodeApiVersion(2)
  interface Builder : ContentRootTestEntity, WorkspaceEntity.Builder<ContentRootTestEntity> {
    override var entitySource: EntitySource
    override var module: ModuleTestEntity
    override var sourceRootOrder: SourceRootTestOrderEntity?
    override var sourceRoots: List<SourceRootTestEntity>
  }

  companion object : EntityType<ContentRootTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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

var ContentRootTestEntity.Builder.projectModelTestEntity: ProjectModelTestEntity?
  by WorkspaceEntity.extension()
//endregion

interface SourceRootTestOrderEntity : WorkspaceEntity {
  val data: String
  val contentRoot: ContentRootTestEntity

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SourceRootTestOrderEntity, WorkspaceEntity.Builder<SourceRootTestOrderEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var contentRoot: ContentRootTestEntity
  }

  companion object : EntityType<SourceRootTestOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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
  @GeneratedCodeApiVersion(2)
  interface Builder : SourceRootTestEntity, WorkspaceEntity.Builder<SourceRootTestEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var contentRoot: ContentRootTestEntity
  }

  companion object : EntityType<SourceRootTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
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

data class ModuleTestEntitySymbolicId(val name: String) : SymbolicEntityId<ModuleTestEntity> {
  override val presentableName: String
    get() = name
}

data class FacetTestEntitySymbolicId(val name: String) : SymbolicEntityId<FacetTestEntity> {
  override val presentableName: String
    get() = name
}

interface FacetTestEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  val moreData: String
  val module: ModuleTestEntity

  override val symbolicId: SymbolicEntityId<WorkspaceEntityWithSymbolicId>
    get() = FacetTestEntitySymbolicId(data)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : FacetTestEntity, WorkspaceEntity.Builder<FacetTestEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var moreData: String
    override var module: ModuleTestEntity
  }

  companion object : EntityType<FacetTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, moreData: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FacetTestEntity {
      val builder = builder()
      builder.data = data
      builder.moreData = moreData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: FacetTestEntity, modification: FacetTestEntity.Builder.() -> Unit) = modifyEntity(
  FacetTestEntity.Builder::class.java, entity, modification)
//endregion
