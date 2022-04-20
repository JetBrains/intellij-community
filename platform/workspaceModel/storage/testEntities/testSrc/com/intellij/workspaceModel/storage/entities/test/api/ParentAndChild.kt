package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity

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

import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion








interface ParentEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildEntity

    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ParentEntity, ModifiableWorkspaceEntity<ParentEntity>, ObjBuilder<ParentEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildEntity
    }
    
    companion object: Type<ParentEntity, Builder>()
    //@formatter:on
    //endregion

}

interface ChildEntity : WorkspaceEntity {
    val childData: String

//    override val parent: ParentEntity
    val parentEntity: ParentEntity


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ChildEntity, ModifiableWorkspaceEntity<ChildEntity>, ObjBuilder<ChildEntity> {
        override var childData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentEntity
    }
    
    companion object: Type<ChildEntity, Builder>()
    //@formatter:on
    //endregion

}