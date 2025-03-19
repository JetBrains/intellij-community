// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RootsExtensions")

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

/**
 * Stores order of excluded roots in iml file.
 * This is needed to ensure that corresponding tags are saved in the same order to avoid unnecessary modifications of iml file.
 */
@Internal
interface SourceRootOrderEntity : WorkspaceEntity {
  val orderOfSourceRoots: List<VirtualFileUrl>

  val contentRootEntity: ContentRootEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SourceRootOrderEntity> {
    override var entitySource: EntitySource
    var orderOfSourceRoots: MutableList<VirtualFileUrl>
    var contentRootEntity: ContentRootEntity.Builder
  }

  companion object : EntityType<SourceRootOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      orderOfSourceRoots: List<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
@Internal
fun MutableEntityStorage.modifySourceRootOrderEntity(
  entity: SourceRootOrderEntity,
  modification: SourceRootOrderEntity.Builder.() -> Unit,
): SourceRootOrderEntity {
  return modifyEntity(SourceRootOrderEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ContentRootEntity.sourceRootOrder: @Child SourceRootOrderEntity?
  by WorkspaceEntity.extension()


/**
 * Describes custom properties of [SourceFolder][com.intellij.openapi.roots.SourceFolder].
 */
@Internal
interface CustomSourceRootPropertiesEntity : WorkspaceEntity {
  val propertiesXmlTag: @NonNls String

  val sourceRoot: SourceRootEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<CustomSourceRootPropertiesEntity> {
    override var entitySource: EntitySource
    var propertiesXmlTag: String
    var sourceRoot: SourceRootEntity.Builder
  }

  companion object : EntityType<CustomSourceRootPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      propertiesXmlTag: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
@Internal
fun MutableEntityStorage.modifyCustomSourceRootPropertiesEntity(
  entity: CustomSourceRootPropertiesEntity,
  modification: CustomSourceRootPropertiesEntity.Builder.() -> Unit,
): CustomSourceRootPropertiesEntity {
  return modifyEntity(CustomSourceRootPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val SourceRootEntity.customSourceRootProperties: @Child CustomSourceRootPropertiesEntity?
  by WorkspaceEntity.extension()

/**
 * Stores order of excluded roots in iml file.
 * This is needed to ensure that corresponding tags are saved in the same order to avoid unnecessary modifications of iml file.
 */
@Internal
interface ExcludeUrlOrderEntity : WorkspaceEntity {
  val order: List<VirtualFileUrl>

  val contentRoot: ContentRootEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ExcludeUrlOrderEntity> {
    override var entitySource: EntitySource
    var order: MutableList<VirtualFileUrl>
    var contentRoot: ContentRootEntity.Builder
  }

  companion object : EntityType<ExcludeUrlOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      order: List<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.order = order.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
@Internal
fun MutableEntityStorage.modifyExcludeUrlOrderEntity(
  entity: ExcludeUrlOrderEntity,
  modification: ExcludeUrlOrderEntity.Builder.() -> Unit,
): ExcludeUrlOrderEntity {
  return modifyEntity(ExcludeUrlOrderEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ContentRootEntity.excludeUrlOrder: @Child ExcludeUrlOrderEntity?
  by WorkspaceEntity.extension()