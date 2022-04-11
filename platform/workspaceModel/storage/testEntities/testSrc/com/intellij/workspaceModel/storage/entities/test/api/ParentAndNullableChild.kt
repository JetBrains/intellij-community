package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type







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
    
    companion object: Type<ParentNullableEntity, Builder>(61)
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
    
    companion object: Type<ChildNullableEntity, Builder>(62)
    //@formatter:on
    //endregion

}
