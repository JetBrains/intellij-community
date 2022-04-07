package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
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
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity





interface ParentEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildEntity

    //region generated code
    //@formatter:off
    interface Builder: ParentEntity, ModifiableWorkspaceEntity<ParentEntity>, ObjBuilder<ParentEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildEntity
    }
    
    companion object: ObjType<ParentEntity, Builder>(IntellijWs, 33) {
        val parentData: Field<ParentEntity, String> = Field(this, 0, "parentData", TString)
        val entitySource: Field<ParentEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val child: Field<ParentEntity, ChildEntity> = Field(this, 0, "child", TRef("org.jetbrains.deft.IntellijWs", 34, child = true))
    }
    //@formatter:on
    //endregion

}

interface ChildEntity : WorkspaceEntity {
    val childData: String

//    override val parent: ParentEntity
    val parentEntity: ParentEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildEntity, ModifiableWorkspaceEntity<ChildEntity>, ObjBuilder<ChildEntity> {
        override var childData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentEntity
    }
    
    companion object: ObjType<ChildEntity, Builder>(IntellijWs, 34) {
        val childData: Field<ChildEntity, String> = Field(this, 0, "childData", TString)
        val entitySource: Field<ChildEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildEntity, ParentEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWs", 33))
    }
    //@formatter:on
    //endregion

}