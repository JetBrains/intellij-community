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
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion









interface ParentChainEntity : WorkspaceEntity {
    val root: @Child CompositeAbstractEntity


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: ParentChainEntity, ModifiableWorkspaceEntity<ParentChainEntity>, ObjBuilder<ParentChainEntity> {
        override var root: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<ParentChainEntity, Builder>()
    //@formatter:on
    //endregion

}

@Abstract
interface SimpleAbstractEntity : WorkspaceEntity {

    val parentInList: CompositeAbstractEntity


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder<T: SimpleAbstractEntity>: SimpleAbstractEntity, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var parentInList: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<SimpleAbstractEntity, Builder<SimpleAbstractEntity>>()
    //@formatter:on
    //endregion

}

@Abstract
interface CompositeAbstractEntity : SimpleAbstractEntity {
    val children: List<@Child SimpleAbstractEntity>

    val parentEntity: ParentChainEntity?


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder<T: CompositeAbstractEntity>: CompositeAbstractEntity, SimpleAbstractEntity.Builder<T>, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var parentInList: CompositeAbstractEntity
        override var children: List<SimpleAbstractEntity>
        override var entitySource: EntitySource
        override var parentEntity: ParentChainEntity?
    }
    
    companion object: Type<CompositeAbstractEntity, Builder<CompositeAbstractEntity>>(SimpleAbstractEntity)
    //@formatter:on
    //endregion

}

interface CompositeChildAbstractEntity : CompositeAbstractEntity {

    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: CompositeChildAbstractEntity, CompositeAbstractEntity.Builder<CompositeChildAbstractEntity>, ModifiableWorkspaceEntity<CompositeChildAbstractEntity>, ObjBuilder<CompositeChildAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var children: List<SimpleAbstractEntity>
        override var entitySource: EntitySource
        override var parentEntity: ParentChainEntity?
    }
    
    companion object: Type<CompositeChildAbstractEntity, Builder>(CompositeAbstractEntity)
    //@formatter:on
    //endregion

}

interface SimpleChildAbstractEntity : SimpleAbstractEntity {

    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: SimpleChildAbstractEntity, SimpleAbstractEntity.Builder<SimpleChildAbstractEntity>, ModifiableWorkspaceEntity<SimpleChildAbstractEntity>, ObjBuilder<SimpleChildAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<SimpleChildAbstractEntity, Builder>(SimpleAbstractEntity)
    //@formatter:on
    //endregion

}