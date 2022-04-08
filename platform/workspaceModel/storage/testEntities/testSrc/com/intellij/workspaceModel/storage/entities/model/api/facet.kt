package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs





interface FacetEntity: WorkspaceEntityWithPersistentId {
    override val name: String
    val module: ModuleEntity
    val facetType: String
    val configurationXmlTag: String?
    val moduleId: ModuleId

    // TODO:: Fix me
    @Child
    val underlyingFacet: FacetEntity?
    override val persistentId: FacetId
        get() = FacetId(name, facetType, moduleId)
    //region generated code
    //@formatter:off
    interface Builder: FacetEntity, ModifiableWorkspaceEntity<FacetEntity>, ObjBuilder<FacetEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var module: ModuleEntity
        override var facetType: String
        override var configurationXmlTag: String?
        override var moduleId: ModuleId
        override var underlyingFacet: FacetEntity?
    }
    
    companion object: ObjType<FacetEntity, Builder>(IntellijWs, 40)
    //@formatter:on
    //endregion

}