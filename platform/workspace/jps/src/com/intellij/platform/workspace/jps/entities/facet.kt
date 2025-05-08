// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import org.jetbrains.annotations.NonNls

data class FacetEntityTypeId(val name: @NonNls String)

/**
 * Describes a [Facet][com.intellij.facet.Facet].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface FacetEntity : ModuleSettingsFacetBridgeEntity {
  override val symbolicId: FacetId
    get() = FacetId(name, typeId, moduleId)
  val typeId: FacetEntityTypeId
  val configurationXmlTag: @NonNls String?

  val module: ModuleEntity

  // underlyingFacet is a parent facet!!
  val underlyingFacet: FacetEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<FacetEntity>, ModuleSettingsFacetBridgeEntity.Builder<FacetEntity> {
    override var entitySource: EntitySource
    override var moduleId: ModuleId
    override var name: String
    var typeId: FacetEntityTypeId
    var configurationXmlTag: String?
    var module: ModuleEntity.Builder
    var underlyingFacet: FacetEntity.Builder?
  }

  companion object : EntityType<FacetEntity, Builder>(ModuleSettingsFacetBridgeEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      moduleId: ModuleId,
      name: String,
      typeId: FacetEntityTypeId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.moduleId = moduleId
      builder.name = name
      builder.typeId = typeId
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyFacetEntity(
  entity: FacetEntity,
  modification: FacetEntity.Builder.() -> Unit,
): FacetEntity {
  return modifyEntity(FacetEntity.Builder::class.java, entity, modification)
}

var FacetEntity.Builder.childrenFacets: @Child List<FacetEntity.Builder>
  by WorkspaceEntity.extensionBuilder(FacetEntity::class.java)
//endregion

val FacetEntity.childrenFacets: List<@Child FacetEntity>
    by WorkspaceEntity.extension()