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







interface ParentMultipleEntity : WorkspaceEntity {
    val parentData: String
    val children: List<@Child ChildMultipleEntity>


    //region generated code
    //@formatter:off
    interface Builder: ParentMultipleEntity, ModifiableWorkspaceEntity<ParentMultipleEntity>, ObjBuilder<ParentMultipleEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var children: List<ChildMultipleEntity>
    }
    
    companion object: Type<ParentMultipleEntity, Builder>(91)
    //@formatter:on
    //endregion

}

interface ChildMultipleEntity : WorkspaceEntity {
    val childData: String

    val parentEntity: ParentMultipleEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildMultipleEntity, ModifiableWorkspaceEntity<ChildMultipleEntity>, ObjBuilder<ChildMultipleEntity> {
        override var childData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentMultipleEntity
    }
    
    companion object: Type<ChildMultipleEntity, Builder>(92)
    //@formatter:on
    //endregion

}