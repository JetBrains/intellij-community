// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.ObjBuilder
import com.intellij.platform.workspace.storage.Type
import com.intellij.platform.workspace.storage.annotations.Child

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
