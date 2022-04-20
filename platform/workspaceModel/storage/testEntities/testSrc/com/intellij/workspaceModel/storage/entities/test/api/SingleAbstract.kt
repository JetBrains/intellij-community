package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
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








interface ParentSingleAbEntity : WorkspaceEntity {
    val child: @Child ChildSingleAbstractBaseEntity


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ParentSingleAbEntity, ModifiableWorkspaceEntity<ParentSingleAbEntity>, ObjBuilder<ParentSingleAbEntity> {
        override var child: ChildSingleAbstractBaseEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<ParentSingleAbEntity, Builder>()
    //@formatter:on
    //endregion

}

@Abstract
interface ChildSingleAbstractBaseEntity : WorkspaceEntity {
    val commonData: String

    val parentEntity: ParentSingleAbEntity


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder<T: ChildSingleAbstractBaseEntity>: ChildSingleAbstractBaseEntity, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var commonData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentSingleAbEntity
    }
    
    companion object: Type<ChildSingleAbstractBaseEntity, Builder<ChildSingleAbstractBaseEntity>>()
    //@formatter:on
    //endregion

}

interface ChildSingleFirstEntity : ChildSingleAbstractBaseEntity {
    val firstData: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ChildSingleFirstEntity, ChildSingleAbstractBaseEntity.Builder<ChildSingleFirstEntity>, ModifiableWorkspaceEntity<ChildSingleFirstEntity>, ObjBuilder<ChildSingleFirstEntity> {
        override var commonData: String
        override var parentEntity: ParentSingleAbEntity
        override var firstData: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ChildSingleFirstEntity, Builder>(ChildSingleAbstractBaseEntity)
    //@formatter:on
    //endregion

}

interface ChildSingleSecondEntity : ChildSingleAbstractBaseEntity {
    val secondData: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ChildSingleSecondEntity, ChildSingleAbstractBaseEntity.Builder<ChildSingleSecondEntity>, ModifiableWorkspaceEntity<ChildSingleSecondEntity>, ObjBuilder<ChildSingleSecondEntity> {
        override var commonData: String
        override var parentEntity: ParentSingleAbEntity
        override var secondData: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<ChildSingleSecondEntity, Builder>(ChildSingleAbstractBaseEntity)
    //@formatter:on
    //endregion

}
