// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.annotations.NonNls
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

interface FacetEntity: ModuleSettingsBase {
  val module: ModuleEntity
  val facetType: @NonNls String
  val configurationXmlTag: @NonNls String?

  // underlyingFacet is a parent facet!!
  val underlyingFacet: FacetEntity?
  override val symbolicId: FacetId
    get() = FacetId(name, facetType, moduleId)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : FacetEntity, ModuleSettingsBase.Builder<FacetEntity>, WorkspaceEntity.Builder<FacetEntity>, ObjBuilder<FacetEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var moduleId: ModuleId
    override var module: ModuleEntity
    override var facetType: String
    override var configurationXmlTag: String?
    override var underlyingFacet: FacetEntity?
  }

  companion object : Type<FacetEntity, Builder>(ModuleSettingsBase) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(name: String,
                        moduleId: ModuleId,
                        facetType: String,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): FacetEntity {
      val builder = builder()
      builder.name = name
      builder.moduleId = moduleId
      builder.facetType = facetType
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: FacetEntity, modification: FacetEntity.Builder.() -> Unit) = modifyEntity(
  FacetEntity.Builder::class.java, entity, modification)

var FacetEntity.Builder.childrenFacets: @Child List<FacetEntity>
  by WorkspaceEntity.extension()
//endregion

val FacetEntity.childrenFacets: List<@Child FacetEntity>
    by WorkspaceEntity.extension()
