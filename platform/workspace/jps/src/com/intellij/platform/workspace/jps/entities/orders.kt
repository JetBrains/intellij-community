// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * This entity stores order of facets in iml file. This is needed to ensure that facet tags are saved in the same order to avoid
 * unnecessary modifications of iml file.
 */
@Internal
interface FacetsOrderEntity : WorkspaceEntity {
  val orderOfFacets: List<@NlsSafe String>
  @Parent
  val moduleEntity: ModuleEntity

  //region generated code
  @Deprecated(message = "Use FacetsOrderEntityBuilder instead")
  interface Builder : FacetsOrderEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModuleEntity(): ModuleEntity.Builder = moduleEntity as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModuleEntity(value: ModuleEntity.Builder) {
      moduleEntity = value
    }
  }

  companion object : EntityType<FacetsOrderEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      orderOfFacets: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = FacetsOrderEntityType.compatibilityInvoke(orderOfFacets, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
@Internal
fun MutableEntityStorage.modifyFacetsOrderEntity(
  entity: FacetsOrderEntity,
  modification: FacetsOrderEntity.Builder.() -> Unit,
): FacetsOrderEntity {
  return modifyEntity(FacetsOrderEntity.Builder::class.java, entity, modification)
}
//endregion

@get:Internal
val ModuleEntity.facetOrder: FacetsOrderEntity?
    by WorkspaceEntity.extension()

