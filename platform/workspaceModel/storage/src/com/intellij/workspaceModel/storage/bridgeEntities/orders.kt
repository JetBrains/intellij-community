// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

/**
 * This entity stores order of facets in iml file. This is needed to ensure that facet tags are saved in the same order to avoid
 * unnecessary modifications of iml file.
 */
interface FacetsOrderEntity : WorkspaceEntity {
  val orderOfFacets: List<@NlsSafe String>
  val moduleEntity: ModuleEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : FacetsOrderEntity, WorkspaceEntity.Builder<FacetsOrderEntity>, ObjBuilder<FacetsOrderEntity> {
    override var entitySource: EntitySource
    override var orderOfFacets: MutableList<String>
    override var moduleEntity: ModuleEntity
  }

  companion object : Type<FacetsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(orderOfFacets: List<String>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FacetsOrderEntity {
      val builder = builder()
      builder.orderOfFacets = orderOfFacets.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: FacetsOrderEntity, modification: FacetsOrderEntity.Builder.() -> Unit) = modifyEntity(
  FacetsOrderEntity.Builder::class.java, entity, modification)
//endregion

val ModuleEntity.facetOrder: @Child FacetsOrderEntity?
    by WorkspaceEntity.extension()

/**
 * This entity stores order of artifacts in ipr file. This is needed to ensure that artifact tags are saved in the same order to avoid
 * unnecessary modifications of ipr file.
 */
interface ArtifactsOrderEntity : WorkspaceEntity {
  val orderOfArtifacts: List<@NlsSafe String>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ArtifactsOrderEntity, WorkspaceEntity.Builder<ArtifactsOrderEntity>, ObjBuilder<ArtifactsOrderEntity> {
    override var entitySource: EntitySource
    override var orderOfArtifacts: MutableList<String>
  }

  companion object : Type<ArtifactsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(orderOfArtifacts: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ArtifactsOrderEntity {
      val builder = builder()
      builder.orderOfArtifacts = orderOfArtifacts.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactsOrderEntity, modification: ArtifactsOrderEntity.Builder.() -> Unit) = modifyEntity(
  ArtifactsOrderEntity.Builder::class.java, entity, modification)
//endregion

interface ExcludeUrlOrderEntity : WorkspaceEntity {
  val order: List<VirtualFileUrl>

  val contentRoot: ContentRootEntity

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ExcludeUrlOrderEntity, WorkspaceEntity.Builder<ExcludeUrlOrderEntity>, ObjBuilder<ExcludeUrlOrderEntity> {
    override var entitySource: EntitySource
    override var order: MutableList<VirtualFileUrl>
    override var contentRoot: ContentRootEntity
  }

  companion object : Type<ExcludeUrlOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(order: List<VirtualFileUrl>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ExcludeUrlOrderEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ExcludeUrlOrderEntity, modification: ExcludeUrlOrderEntity.Builder.() -> Unit) = modifyEntity(
  ExcludeUrlOrderEntity.Builder::class.java, entity, modification)
//endregion
