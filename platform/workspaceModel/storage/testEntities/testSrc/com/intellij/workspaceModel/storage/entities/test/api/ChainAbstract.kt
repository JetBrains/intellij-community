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








interface ParentChainEntity : WorkspaceEntity {
    val root: @Child CompositeAbstractEntity


    //region generated code
    //@formatter:off
    interface Builder: ParentChainEntity, ModifiableWorkspaceEntity<ParentChainEntity>, ObjBuilder<ParentChainEntity> {
        override var root: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<ParentChainEntity, Builder>(69)
    //@formatter:on
    //endregion

}

@Abstract
interface SimpleAbstractEntity : WorkspaceEntity {

    val parentInList: CompositeAbstractEntity


    //region generated code
    //@formatter:off
    interface Builder<T: SimpleAbstractEntity>: SimpleAbstractEntity, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var parentInList: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<SimpleAbstractEntity, Builder<SimpleAbstractEntity>>(70)
    //@formatter:on
    //endregion

}

@Abstract
interface CompositeAbstractEntity : SimpleAbstractEntity {
    val children: List<@Child SimpleAbstractEntity>

    val parentEntity: ParentChainEntity?


    //region generated code
    //@formatter:off
    interface Builder<T: CompositeAbstractEntity>: CompositeAbstractEntity, SimpleAbstractEntity.Builder<T>, ModifiableWorkspaceEntity<T>, ObjBuilder<T> {
        override var parentInList: CompositeAbstractEntity
        override var children: List<SimpleAbstractEntity>
        override var entitySource: EntitySource
        override var parentEntity: ParentChainEntity?
    }
    
    companion object: Type<CompositeAbstractEntity, Builder<CompositeAbstractEntity>>(71, SimpleAbstractEntity)
    //@formatter:on
    //endregion

}

interface CompositeChildAbstractEntity : CompositeAbstractEntity {

    //region generated code
    //@formatter:off
    interface Builder: CompositeChildAbstractEntity, CompositeAbstractEntity.Builder<CompositeChildAbstractEntity>, ModifiableWorkspaceEntity<CompositeChildAbstractEntity>, ObjBuilder<CompositeChildAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var children: List<SimpleAbstractEntity>
        override var entitySource: EntitySource
        override var parentEntity: ParentChainEntity?
    }
    
    companion object: Type<CompositeChildAbstractEntity, Builder>(72, CompositeAbstractEntity)
    //@formatter:on
    //endregion

}

interface SimpleChildAbstractEntity : SimpleAbstractEntity {

    //region generated code
    //@formatter:off
    interface Builder: SimpleChildAbstractEntity, SimpleAbstractEntity.Builder<SimpleChildAbstractEntity>, ModifiableWorkspaceEntity<SimpleChildAbstractEntity>, ObjBuilder<SimpleChildAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<SimpleChildAbstractEntity, Builder>(73, SimpleAbstractEntity)
    //@formatter:on
    //endregion

}