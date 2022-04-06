package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.TBlob
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.TString
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*




interface MainEntity : WorkspaceEntity {
    val x: String


    //region generated code
    //@formatter:off
    interface Builder: MainEntity, ObjBuilder<MainEntity> {
        override var x: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<MainEntity, Builder>(IntellijWs, 37) {
        val x: Field<MainEntity, String> = Field(this, 0, "x", TString)
        val entitySource: Field<MainEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
    //@formatter:on
    //endregion

}

interface AttachedEntity : WorkspaceEntity {
    val ref: MainEntity
    val data: String


    //region generated code
    //@formatter:off
    interface Builder: AttachedEntity, ObjBuilder<AttachedEntity> {
        override var ref: MainEntity
        override var entitySource: EntitySource
        override var data: String
    }
    
    companion object: ObjType<AttachedEntity, Builder>(IntellijWs, 38) {
        val ref: Field<AttachedEntity, MainEntity> = Field(this, 0, "ref", TRef("org.jetbrains.deft.IntellijWs", 37))
        val entitySource: Field<AttachedEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val data: Field<AttachedEntity, String> = Field(this, 0, "data", TString)
    }
    //@formatter:on
    //endregion

}

val MainEntity.child: @Child AttachedEntity?
    get() = referrersx(AttachedEntity::ref).singleOrNull()
