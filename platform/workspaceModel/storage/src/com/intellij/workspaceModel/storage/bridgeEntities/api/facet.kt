// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion




interface FacetEntity: WorkspaceEntityWithPersistentId {
    val name: String
    val module: ModuleEntity
    val facetType: String
    val configurationXmlTag: String?
    val moduleId: ModuleId

    // underlyingFacet is a parent facet!!
    val underlyingFacet: FacetEntity?
    override val persistentId: FacetId
        get() = FacetId(name, facetType, moduleId)


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: FacetEntity, ModifiableWorkspaceEntity<FacetEntity>, ObjBuilder<FacetEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var module: ModuleEntity
        override var facetType: String
        override var configurationXmlTag: String?
        override var moduleId: ModuleId
        override var underlyingFacet: FacetEntity?
    }
    
    companion object: Type<FacetEntity, Builder>() {
        operator fun invoke(name: String, entitySource: EntitySource, facetType: String, moduleId: ModuleId, init: Builder.() -> Unit): FacetEntity {
            val builder = builder(init)
            builder.name = name
            builder.entitySource = entitySource
            builder.facetType = facetType
            builder.moduleId = moduleId
            return builder
        }
    }
    //@formatter:on
    //endregion

}

val FacetEntity.childrenFacets: List<@Child FacetEntity>
  get() = referrersx(FacetEntity::underlyingFacet)
