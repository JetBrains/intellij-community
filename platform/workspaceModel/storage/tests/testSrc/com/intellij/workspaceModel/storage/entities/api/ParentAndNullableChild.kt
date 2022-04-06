package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*




interface ParentNullableEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildNullableEntity?


    //region generated code
    //@formatter:off
    interface Builder: ParentNullableEntity, ObjBuilder<ParentNullableEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildNullableEntity?
    }
    
    companion object: ObjType<ParentNullableEntity, Builder>(IntellijWs, 35) {
        val parentData: Field<ParentNullableEntity, String> = Field(this, 0, "parentData", TString)
        val entitySource: Field<ParentNullableEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val child: Field<ParentNullableEntity, ChildNullableEntity?> = Field(this, 0, "child", TOptional(TRef("org.jetbrains.deft.IntellijWs", 36, child = true)))
    }
    //@formatter:on
    //endregion

}

interface ChildNullableEntity : WorkspaceEntity {
    val childData: String

    val parentEntity: ParentNullableEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildNullableEntity, ObjBuilder<ChildNullableEntity> {
        override var childData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentNullableEntity
    }
    
    companion object: ObjType<ChildNullableEntity, Builder>(IntellijWs, 36) {
        val childData: Field<ChildNullableEntity, String> = Field(this, 0, "childData", TString)
        val entitySource: Field<ChildNullableEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildNullableEntity, ParentNullableEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWs", 35))
    }
    //@formatter:on
    //endregion

}
