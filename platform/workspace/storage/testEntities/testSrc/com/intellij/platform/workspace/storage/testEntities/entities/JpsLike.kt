// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child

interface ModuleTestEntity : WorkspaceEntityWithSymbolicId {
  val name: String

  val contentRoots: List<@Child ContentRootTestEntity>
  val facets: List<@Child FacetTestEntity>

  override val symbolicId: ModuleTestEntitySymbolicId
    get() = ModuleTestEntitySymbolicId(name)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleTestEntity> {
    override var entitySource: EntitySource
    var name: String
    var contentRoots: List<ContentRootTestEntity.Builder>
    var facets: List<FacetTestEntity.Builder>
  }

  companion object : EntityType<ModuleTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyModuleTestEntity(
  entity: ModuleTestEntity,
  modification: ModuleTestEntity.Builder.() -> Unit,
): ModuleTestEntity {
  return modifyEntity(ModuleTestEntity.Builder::class.java, entity, modification)
}
//endregion

interface ContentRootTestEntity : WorkspaceEntity {
  val module: ModuleTestEntity
  val sourceRootOrder: @Child SourceRootTestOrderEntity?
  val sourceRoots: List<@Child SourceRootTestEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ContentRootTestEntity> {
    override var entitySource: EntitySource
    var module: ModuleTestEntity.Builder
    var sourceRootOrder: SourceRootTestOrderEntity.Builder?
    var sourceRoots: List<SourceRootTestEntity.Builder>
  }

  companion object : EntityType<ContentRootTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyContentRootTestEntity(
  entity: ContentRootTestEntity,
  modification: ContentRootTestEntity.Builder.() -> Unit,
): ContentRootTestEntity {
  return modifyEntity(ContentRootTestEntity.Builder::class.java, entity, modification)
}

var ContentRootTestEntity.Builder.projectModelTestEntity: ProjectModelTestEntity.Builder?
  by WorkspaceEntity.extensionBuilder(ProjectModelTestEntity::class.java)
//endregion

interface SourceRootTestOrderEntity : WorkspaceEntity {
  val data: String
  val contentRoot: ContentRootTestEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SourceRootTestOrderEntity> {
    override var entitySource: EntitySource
    var data: String
    var contentRoot: ContentRootTestEntity.Builder
  }

  companion object : EntityType<SourceRootTestOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifySourceRootTestOrderEntity(
  entity: SourceRootTestOrderEntity,
  modification: SourceRootTestOrderEntity.Builder.() -> Unit,
): SourceRootTestOrderEntity {
  return modifyEntity(SourceRootTestOrderEntity.Builder::class.java, entity, modification)
}
//endregion

interface SourceRootTestEntity : WorkspaceEntity {
  val data: String
  val contentRoot: ContentRootTestEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SourceRootTestEntity> {
    override var entitySource: EntitySource
    var data: String
    var contentRoot: ContentRootTestEntity.Builder
  }

  companion object : EntityType<SourceRootTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifySourceRootTestEntity(
  entity: SourceRootTestEntity,
  modification: SourceRootTestEntity.Builder.() -> Unit,
): SourceRootTestEntity {
  return modifyEntity(SourceRootTestEntity.Builder::class.java, entity, modification)
}
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

  override val symbolicId: FacetTestEntitySymbolicId
    get() = FacetTestEntitySymbolicId(data)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<FacetTestEntity> {
    override var entitySource: EntitySource
    var data: String
    var moreData: String
    var module: ModuleTestEntity.Builder
  }

  companion object : EntityType<FacetTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      moreData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyFacetTestEntity(
  entity: FacetTestEntity,
  modification: FacetTestEntity.Builder.() -> Unit,
): FacetTestEntity {
  return modifyEntity(FacetTestEntity.Builder::class.java, entity, modification)
}
//endregion
