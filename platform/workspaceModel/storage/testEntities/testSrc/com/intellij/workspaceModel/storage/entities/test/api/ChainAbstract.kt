package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.TestEntities.TestEntities
import org.jetbrains.deft.Type








interface ParentChainEntity : WorkspaceEntity {
    val root: @Child CompositeAbstractEntity


    //region generated code
    //@formatter:off
    interface Builder: ParentChainEntity, ModifiableWorkspaceEntity<ParentChainEntity>, ObjBuilder<ParentChainEntity> {
        override var root: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<ParentChainEntity, Builder>(61)
    //@formatter:on
    //endregion

}

@Abstract
interface SimpleAbstractEntity : WorkspaceEntity {

    val parentInList: CompositeAbstractEntity


    //region generated code
    //@formatter:off
    interface Builder: SimpleAbstractEntity, ModifiableWorkspaceEntity<SimpleAbstractEntity>, ObjBuilder<SimpleAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<SimpleAbstractEntity, Builder>(62)
    //@formatter:on
    //endregion

}

@Abstract
interface CompositeAbstractEntity : SimpleAbstractEntity {
    val children: List<@Child SimpleAbstractEntity>

    val parentEntity: ParentChainEntity?


    //region generated code
    //@formatter:off
    interface Builder: CompositeAbstractEntity, ModifiableWorkspaceEntity<CompositeAbstractEntity>, ObjBuilder<CompositeAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var children: List<SimpleAbstractEntity>
        override var entitySource: EntitySource
        override var parentEntity: ParentChainEntity?
    }
    
    companion object: Type<CompositeAbstractEntity, Builder>(63, SimpleAbstractEntity)
    //@formatter:on
    //endregion

}

interface CompositeChildAbstractEntity : CompositeAbstractEntity {

    //region generated code
    //@formatter:off
    interface Builder: CompositeChildAbstractEntity, ModifiableWorkspaceEntity<CompositeChildAbstractEntity>, ObjBuilder<CompositeChildAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var children: List<SimpleAbstractEntity>
        override var entitySource: EntitySource
        override var parentEntity: ParentChainEntity?
    }
    
    companion object: Type<CompositeChildAbstractEntity, Builder>(64, CompositeAbstractEntity)
    //@formatter:on
    //endregion

}

interface SimpleChildAbstractEntity : SimpleAbstractEntity {

    //region generated code
    //@formatter:off
    interface Builder: SimpleChildAbstractEntity, ModifiableWorkspaceEntity<SimpleChildAbstractEntity>, ObjBuilder<SimpleChildAbstractEntity> {
        override var parentInList: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: Type<SimpleChildAbstractEntity, Builder>(65, SimpleAbstractEntity)
    //@formatter:on
    //endregion

}