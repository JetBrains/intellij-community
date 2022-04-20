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









interface ParentSubEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildSubEntity


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ParentSubEntity, ModifiableWorkspaceEntity<ParentSubEntity>, ObjBuilder<ParentSubEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildSubEntity
    }
    
    companion object: Type<ParentSubEntity, Builder>()
    //@formatter:on
    //endregion

}

interface ChildSubEntity : WorkspaceEntity {
    val parentEntity: ParentSubEntity

    @Child
    val child: ChildSubSubEntity


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ChildSubEntity, ModifiableWorkspaceEntity<ChildSubEntity>, ObjBuilder<ChildSubEntity> {
        override var parentEntity: ParentSubEntity
        override var entitySource: EntitySource
        override var child: ChildSubSubEntity
    }
    
    companion object: Type<ChildSubEntity, Builder>()
    //@formatter:on
    //endregion

}
interface ChildSubSubEntity : WorkspaceEntity {
    val parentEntity: ChildSubEntity

    val childData: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ChildSubSubEntity, ModifiableWorkspaceEntity<ChildSubSubEntity>, ObjBuilder<ChildSubSubEntity> {
        override var parentEntity: ChildSubEntity
        override var entitySource: EntitySource
        override var childData: String
    }
    
    companion object: Type<ChildSubSubEntity, Builder>()
    //@formatter:on
    //endregion

}
