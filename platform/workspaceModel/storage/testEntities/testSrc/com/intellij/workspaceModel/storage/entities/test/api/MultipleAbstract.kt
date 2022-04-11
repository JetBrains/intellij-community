package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type









interface ParentAbEntity : WorkspaceEntity {
    val children: List<@Child ChildAbstractBaseEntity>


    //region generated code
    //@formatter:off
    interface Builder: ParentAbEntity, ModifiableWorkspaceEntity<ParentAbEntity>, ObjBuilder<ParentAbEntity> {
        override var children: List<ChildAbstractBaseEntity>
        override var entitySource: EntitySource
    }
    
    companion object: Type<ParentAbEntity, Builder>(78)
    //@formatter:on
    //endregion

}

@Abstract
interface ChildAbstractBaseEntity : WorkspaceEntity {
    val commonData: String

    val parentEntity: ParentAbEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildAbstractBaseEntity, ModifiableWorkspaceEntity<ChildAbstractBaseEntity>, ObjBuilder<ChildAbstractBaseEntity> {
        override var commonData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentAbEntity
    }
    
    companion object: Type<ChildAbstractBaseEntity, Builder>(79)
    //@formatter:on
    //endregion

}

interface ChildFirstEntity : ChildAbstractBaseEntity {
    val firstData: String


    //region generated code
    //@formatter:off
    interface Builder: ChildFirstEntity, ModifiableWorkspaceEntity<ChildFirstEntity>, ObjBuilder<ChildFirstEntity> {
        override var commonData: String
        override var parentEntity: ParentAbEntity
        override var firstData: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ChildFirstEntity, Builder>(80, ChildAbstractBaseEntity)
    //@formatter:on
    //endregion

}

interface ChildSecondEntity : ChildAbstractBaseEntity {

    // TODO doesn't work at the moment
//    override val commonData: String

    val secondData: String


    //region generated code
    //@formatter:off
    interface Builder: ChildSecondEntity, ModifiableWorkspaceEntity<ChildSecondEntity>, ObjBuilder<ChildSecondEntity> {
        override var commonData: String
        override var parentEntity: ParentAbEntity
        override var secondData: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ChildSecondEntity, Builder>(81, ChildAbstractBaseEntity)
    //@formatter:on
    //endregion

}
