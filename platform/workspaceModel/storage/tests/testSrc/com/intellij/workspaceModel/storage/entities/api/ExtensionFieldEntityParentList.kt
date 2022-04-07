package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersy
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity







interface MainEntityParentList : WorkspaceEntity {
    val x: String
    val children: List<@Child AttachedEntityParentList>


    //region generated code
    //@formatter:off
    interface Builder: MainEntityParentList, ModifiableWorkspaceEntity<MainEntityParentList>, ObjBuilder<MainEntityParentList> {
        override var x: String
        override var entitySource: EntitySource
        override var children: List<AttachedEntityParentList>
    }
    
    companion object: ObjType<MainEntityParentList, Builder>(IntellijWs, 16) {
        val x: Field<MainEntityParentList, String> = Field(this, 0, "x", TString)
        val entitySource: Field<MainEntityParentList, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val children: Field<MainEntityParentList, List<AttachedEntityParentList>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWs", 17, child = true)))
    }
    //@formatter:on
    //endregion

}

interface AttachedEntityParentList : WorkspaceEntity {
    val data: String


    //region generated code
    //@formatter:off
    interface Builder: AttachedEntityParentList, ModifiableWorkspaceEntity<AttachedEntityParentList>, ObjBuilder<AttachedEntityParentList> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<AttachedEntityParentList, Builder>(IntellijWs, 17) {
        val data: Field<AttachedEntityParentList, String> = Field(this, 0, "data", TString)
        val entitySource: Field<AttachedEntityParentList, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

val AttachedEntityParentList.ref: MainEntityParentList?
    get() =  referrersy(MainEntityParentList::children).singleOrNull()
