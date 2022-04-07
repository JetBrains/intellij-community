package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity







interface ParentAbEntity : WorkspaceEntity {
    val children: List<@Child ChildAbstractBaseEntity>


    //region generated code
    //@formatter:off
    interface Builder: ParentAbEntity, ModifiableWorkspaceEntity<ParentAbEntity>, ObjBuilder<ParentAbEntity> {
        override var children: List<ChildAbstractBaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ParentAbEntity, Builder>(IntellijWs, 46) {
        val children: Field<ParentAbEntity, List<ChildAbstractBaseEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWs", 47, child = true)))
        val entitySource: Field<ParentAbEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

@Abstract
interface ChildAbstractBaseEntity : WorkspaceEntity {
    val commonData: String

    val parentEntity: ParentAbEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildAbstractBaseEntity, ModifiableWorkspaceEntity<ChildAbstractBaseEntity>, ObjBuilder<ChildAbstractBaseEntity> {
        override var commonData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentAbEntity
    }
    
    companion object: ObjType<ChildAbstractBaseEntity, Builder>(IntellijWs, 47) {
        val commonData: Field<ChildAbstractBaseEntity, String> = Field(this, 0, "commonData", TString)
        val entitySource: Field<ChildAbstractBaseEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildAbstractBaseEntity, ParentAbEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWs", 46))
    }
    //@formatter:on
    //endregion

}

interface ChildFirstEntity : ChildAbstractBaseEntity {
    val firstData: String


    //region generated code
    //@formatter:off
    interface Builder: ChildFirstEntity, ModifiableWorkspaceEntity<ChildFirstEntity>, ObjBuilder<ChildFirstEntity> {
        override var commonData: String
        override var parentEntity: ParentAbEntity
        override var firstData: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ChildFirstEntity, Builder>(IntellijWs, 48, ChildAbstractBaseEntity) {
        val firstData: Field<ChildFirstEntity, String> = Field(this, 0, "firstData", TString)
        val entitySource: Field<ChildFirstEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

interface ChildSecondEntity : ChildAbstractBaseEntity {

    // TODO doesn't work at the moment
//    override val commonData: String

    val secondData: String


    //region generated code
    //@formatter:off
    interface Builder: ChildSecondEntity, ModifiableWorkspaceEntity<ChildSecondEntity>, ObjBuilder<ChildSecondEntity> {
        override var commonData: String
        override var parentEntity: ParentAbEntity
        override var secondData: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ChildSecondEntity, Builder>(IntellijWs, 49, ChildAbstractBaseEntity) {
        val secondData: Field<ChildSecondEntity, String> = Field(this, 0, "secondData", TString)
        val entitySource: Field<ChildSecondEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}
