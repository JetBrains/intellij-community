// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspaceModel.storage.*
import com.intellij.platform.workspaceModel.storage.EntitySource
import com.intellij.platform.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspaceModel.storage.MutableEntityStorage
import com.intellij.platform.workspaceModel.storage.WorkspaceEntity
import com.intellij.platform.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls
import com.intellij.platform.workspaceModel.storage.ObjBuilder
import com.intellij.platform.workspaceModel.storage.Type
import com.intellij.platform.workspaceModel.storage.annotations.Child

interface ContentRootEntity : WorkspaceEntity {
    val module: ModuleEntity

    @EqualsBy
    val url: VirtualFileUrl
    val excludedUrls: List<@Child ExcludeUrlEntity>
    val excludedPatterns: List<@NlsSafe String>
    val sourceRoots: List<@Child SourceRootEntity>
    @Child val sourceRootOrder: SourceRootOrderEntity?
    @Child val excludeUrlOrder: ExcludeUrlOrderEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ContentRootEntity, WorkspaceEntity.Builder<ContentRootEntity>, ObjBuilder<ContentRootEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var url: VirtualFileUrl
    override var excludedUrls: List<ExcludeUrlEntity>
    override var excludedPatterns: MutableList<String>
    override var sourceRoots: List<SourceRootEntity>
    override var sourceRootOrder: SourceRootOrderEntity?
    override var excludeUrlOrder: ExcludeUrlOrderEntity?
  }

  companion object : Type<ContentRootEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(url: VirtualFileUrl,
                        excludedPatterns: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ContentRootEntity {
      val builder = builder()
      builder.url = url
      builder.excludedPatterns = excludedPatterns.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ContentRootEntity, modification: ContentRootEntity.Builder.() -> Unit) = modifyEntity(
  ContentRootEntity.Builder::class.java, entity, modification)
//endregion

val ExcludeUrlEntity.contentRoot: ContentRootEntity? by WorkspaceEntity.extension()

interface SourceRootEntity : WorkspaceEntity {
    val contentRoot: ContentRootEntity

    val url: VirtualFileUrl
    val rootType: @NonNls String

    @Child val customSourceRootProperties: CustomSourceRootPropertiesEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SourceRootEntity, WorkspaceEntity.Builder<SourceRootEntity>, ObjBuilder<SourceRootEntity> {
    override var entitySource: EntitySource
    override var contentRoot: ContentRootEntity
    override var url: VirtualFileUrl
    override var rootType: String
    override var customSourceRootProperties: CustomSourceRootPropertiesEntity?
  }

  companion object : Type<SourceRootEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(url: VirtualFileUrl,
                        rootType: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): SourceRootEntity {
      val builder = builder()
      builder.url = url
      builder.rootType = rootType
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SourceRootEntity, modification: SourceRootEntity.Builder.() -> Unit) = modifyEntity(
  SourceRootEntity.Builder::class.java, entity, modification)
//endregion

interface SourceRootOrderEntity : WorkspaceEntity {
    val contentRootEntity: ContentRootEntity

    val orderOfSourceRoots: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SourceRootOrderEntity, WorkspaceEntity.Builder<SourceRootOrderEntity>, ObjBuilder<SourceRootOrderEntity> {
    override var entitySource: EntitySource
    override var contentRootEntity: ContentRootEntity
    override var orderOfSourceRoots: MutableList<VirtualFileUrl>
  }

  companion object : Type<SourceRootOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(orderOfSourceRoots: List<VirtualFileUrl>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): SourceRootOrderEntity {
      val builder = builder()
      builder.orderOfSourceRoots = orderOfSourceRoots.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SourceRootOrderEntity, modification: SourceRootOrderEntity.Builder.() -> Unit) = modifyEntity(
  SourceRootOrderEntity.Builder::class.java, entity, modification)
//endregion

interface CustomSourceRootPropertiesEntity: WorkspaceEntity {
    val sourceRoot: SourceRootEntity

    val propertiesXmlTag: @NonNls String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : CustomSourceRootPropertiesEntity, WorkspaceEntity.Builder<CustomSourceRootPropertiesEntity>, ObjBuilder<CustomSourceRootPropertiesEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var propertiesXmlTag: String
  }

  companion object : Type<CustomSourceRootPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(propertiesXmlTag: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): CustomSourceRootPropertiesEntity {
      val builder = builder()
      builder.propertiesXmlTag = propertiesXmlTag
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: CustomSourceRootPropertiesEntity,
                                      modification: CustomSourceRootPropertiesEntity.Builder.() -> Unit) = modifyEntity(
  CustomSourceRootPropertiesEntity.Builder::class.java, entity, modification)
//endregion
