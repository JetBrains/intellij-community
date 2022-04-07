package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity






interface MainEntityList : WorkspaceEntity {
    val x: String

    //region generated code
    //@formatter:off
    interface Builder: MainEntityList, ModifiableWorkspaceEntity<MainEntityList>, ObjBuilder<MainEntityList> {
        override var x: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<MainEntityList, Builder>(IntellijWs, 34) {
        val x: Field<MainEntityList, String> = Field(this, 0, "x", TString)
        val entitySource: Field<MainEntityList, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

interface AttachedEntityList : WorkspaceEntity {
    val ref: MainEntityList?
    val data: String


    //region generated code
    //@formatter:off
    interface Builder: AttachedEntityList, ModifiableWorkspaceEntity<AttachedEntityList>, ObjBuilder<AttachedEntityList> {
        override var ref: MainEntityList?
        override var entitySource: EntitySource
        override var data: String
    }
    
    companion object: ObjType<AttachedEntityList, Builder>(IntellijWs, 35) {
        val ref: Field<AttachedEntityList, MainEntityList?> = Field(this, 0, "ref", TOptional(TRef("org.jetbrains.deft.IntellijWs", 34)))
        val entitySource: Field<AttachedEntityList, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val data: Field<AttachedEntityList, String> = Field(this, 0, "data", TString)
    }
    //@formatter:on
    //endregion

}

val MainEntityList.child: List<@Child AttachedEntityList>
    get() = referrersx(AttachedEntityList::ref)
