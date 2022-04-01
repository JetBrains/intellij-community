package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTest.IntellijWsTest



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
    interface Builder: FacetEntity, ObjBuilder<FacetEntity> {
        override var name: String
        override var entitySource: EntitySource
        override var module: ModuleEntity
        override var facetType: String
        override var configurationXmlTag: String?
        override var moduleId: ModuleId
        override var underlyingFacet: FacetEntity?
    }
    
    companion object: ObjType<FacetEntity, Builder>(IntellijWs, 40) {
        val nameField: Field<FacetEntity, String> = Field(this, 0, "name", TString)
        val entitySource: Field<FacetEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val moduleField: Field<FacetEntity, ModuleEntity> = Field(this, 0, "module", TRef("org.jetbrains.deft.IntellijWs", 41))
        val facetType: Field<FacetEntity, String> = Field(this, 0, "facetType", TString)
        val configurationXmlTag: Field<FacetEntity, String?> = Field(this, 0, "configurationXmlTag", TOptional(TString))
        val moduleId: Field<FacetEntity, ModuleId> = Field(this, 0, "moduleId", TBlob("ModuleId"))
        val underlyingFacet: Field<FacetEntity, FacetEntity?> = Field(this, 0, "underlyingFacet", TOptional(TRef("org.jetbrains.deft.IntellijWs", 40, child = true)))
        val persistentId: Field<FacetEntity, FacetId> = Field(this, 0, "persistentId", TBlob("FacetId"))
    }
    //@formatter:on
    //endregion
}