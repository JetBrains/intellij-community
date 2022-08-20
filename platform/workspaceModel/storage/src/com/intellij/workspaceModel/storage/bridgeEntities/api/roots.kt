// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child





interface ContentRootEntity : WorkspaceEntity {
    val module: ModuleEntity

    @EqualsBy
    val url: VirtualFileUrl
    val excludedUrls: List<VirtualFileUrl>
    val excludedPatterns: List<String>
    val sourceRoots: List<@Child SourceRootEntity>
    @Child val sourceRootOrder: SourceRootOrderEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ContentRootEntity, ModifiableWorkspaceEntity<ContentRootEntity>, ObjBuilder<ContentRootEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var url: VirtualFileUrl
    override var excludedUrls: MutableList<VirtualFileUrl>
    override var excludedPatterns: MutableList<String>
    override var sourceRoots: List<SourceRootEntity>
    override var sourceRootOrder: SourceRootOrderEntity?
  }

  companion object : Type<ContentRootEntity, Builder>() {
    operator fun invoke(url: VirtualFileUrl,
                        excludedUrls: List<VirtualFileUrl>,
                        excludedPatterns: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ContentRootEntity {
      val builder = builder()
      builder.url = url
      builder.excludedUrls = excludedUrls.toMutableWorkspaceList()
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

interface SourceRootEntity : WorkspaceEntity {
    val contentRoot: ContentRootEntity

    val url: VirtualFileUrl
    val rootType: String

    @Child val customSourceRootProperties: CustomSourceRootPropertiesEntity?
    val javaSourceRoots: List<@Child JavaSourceRootEntity>
    val javaResourceRoots: List<@Child JavaResourceRootEntity>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SourceRootEntity, ModifiableWorkspaceEntity<SourceRootEntity>, ObjBuilder<SourceRootEntity> {
    override var entitySource: EntitySource
    override var contentRoot: ContentRootEntity
    override var url: VirtualFileUrl
    override var rootType: String
    override var customSourceRootProperties: CustomSourceRootPropertiesEntity?
    override var javaSourceRoots: List<JavaSourceRootEntity>
    override var javaResourceRoots: List<JavaResourceRootEntity>
  }

  companion object : Type<SourceRootEntity, Builder>() {
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
  interface Builder : SourceRootOrderEntity, ModifiableWorkspaceEntity<SourceRootOrderEntity>, ObjBuilder<SourceRootOrderEntity> {
    override var entitySource: EntitySource
    override var contentRootEntity: ContentRootEntity
    override var orderOfSourceRoots: MutableList<VirtualFileUrl>
  }

  companion object : Type<SourceRootOrderEntity, Builder>() {
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

    val propertiesXmlTag: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : CustomSourceRootPropertiesEntity, ModifiableWorkspaceEntity<CustomSourceRootPropertiesEntity>, ObjBuilder<CustomSourceRootPropertiesEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var propertiesXmlTag: String
  }

  companion object : Type<CustomSourceRootPropertiesEntity, Builder>() {
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

interface JavaSourceRootEntity : WorkspaceEntity {
    val sourceRoot: SourceRootEntity

    val generated: Boolean
    val packagePrefix: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : JavaSourceRootEntity, ModifiableWorkspaceEntity<JavaSourceRootEntity>, ObjBuilder<JavaSourceRootEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var generated: Boolean
    override var packagePrefix: String
  }

  companion object : Type<JavaSourceRootEntity, Builder>() {
    operator fun invoke(generated: Boolean,
                        packagePrefix: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): JavaSourceRootEntity {
      val builder = builder()
      builder.generated = generated
      builder.packagePrefix = packagePrefix
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: JavaSourceRootEntity, modification: JavaSourceRootEntity.Builder.() -> Unit) = modifyEntity(
  JavaSourceRootEntity.Builder::class.java, entity, modification)
//endregion

interface JavaResourceRootEntity: WorkspaceEntity {
    val sourceRoot: SourceRootEntity

    val generated: Boolean
    val relativeOutputPath: String

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : JavaResourceRootEntity, ModifiableWorkspaceEntity<JavaResourceRootEntity>, ObjBuilder<JavaResourceRootEntity> {
    override var entitySource: EntitySource
    override var sourceRoot: SourceRootEntity
    override var generated: Boolean
    override var relativeOutputPath: String
  }

  companion object : Type<JavaResourceRootEntity, Builder>() {
    operator fun invoke(generated: Boolean,
                        relativeOutputPath: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): JavaResourceRootEntity {
      val builder = builder()
      builder.generated = generated
      builder.relativeOutputPath = relativeOutputPath
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: JavaResourceRootEntity,
                                      modification: JavaResourceRootEntity.Builder.() -> Unit) = modifyEntity(
  JavaResourceRootEntity.Builder::class.java, entity, modification)
//endregion
