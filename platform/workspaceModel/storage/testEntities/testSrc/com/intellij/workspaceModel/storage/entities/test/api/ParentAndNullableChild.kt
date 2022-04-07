package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.TestEntities.TestEntities






interface ParentNullableEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildNullableEntity?


    //region generated code
    //@formatter:off
    interface Builder: ParentNullableEntity, ModifiableWorkspaceEntity<ParentNullableEntity>, ObjBuilder<ParentNullableEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildNullableEntity?
    }
    
    companion object: ObjType<ParentNullableEntity, Builder>(TestEntities, 53) {
        val parentData: Field<ParentNullableEntity, String> = Field(this, 0, "parentData", TString)
        val entitySource: Field<ParentNullableEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val child: Field<ParentNullableEntity, ChildNullableEntity?> = Field(this, 0, "child", TOptional(TRef("org.jetbrains.deft.TestEntities", 54, child = true)))
    }
    //@formatter:on
    //endregion

}

interface ChildNullableEntity : WorkspaceEntity {
    val childData: String

    val parentEntity: ParentNullableEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildNullableEntity, ModifiableWorkspaceEntity<ChildNullableEntity>, ObjBuilder<ChildNullableEntity> {
        override var childData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentNullableEntity
    }
    
    companion object: ObjType<ChildNullableEntity, Builder>(TestEntities, 54) {
        val childData: Field<ChildNullableEntity, String> = Field(this, 0, "childData", TString)
        val entitySource: Field<ChildNullableEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildNullableEntity, ParentNullableEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.TestEntities", 53))
    }
    //@formatter:on
    //endregion

}
