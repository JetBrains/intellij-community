package com.intellij.workspaceModel.storage.entities.api

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






interface ParentChainEntity : WorkspaceEntity {
    val root: @Child CompositeAbstractEntity


    //region generated code
    //@formatter:off
    interface Builder: ParentChainEntity, ModifiableWorkspaceEntity<ParentChainEntity>, ObjBuilder<ParentChainEntity> {
        override var root: CompositeAbstractEntity
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<ParentChainEntity, Builder>(IntellijWs, 41) {
        val root: Field<ParentChainEntity, CompositeAbstractEntity> = Field(this, 0, "root", TRef("org.jetbrains.deft.IntellijWs", 43, child = true))
        val entitySource: Field<ParentChainEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
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
    
    companion object: ObjType<SimpleAbstractEntity, Builder>(IntellijWs, 42) {
        val parentInList: Field<SimpleAbstractEntity, CompositeAbstractEntity> = Field(this, 0, "parentInList", TRef("org.jetbrains.deft.IntellijWs", 43))
        val entitySource: Field<SimpleAbstractEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
    }
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
    
    companion object: ObjType<CompositeAbstractEntity, Builder>(IntellijWs, 43, SimpleAbstractEntity) {
        val children: Field<CompositeAbstractEntity, List<SimpleAbstractEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWs", 42, child = true)))
        val entitySource: Field<CompositeAbstractEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<CompositeAbstractEntity, ParentChainEntity?> = Field(this, 0, "parentEntity", TOptional(TRef("org.jetbrains.deft.IntellijWs", 41)))
    }
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
    
    companion object: ObjType<CompositeChildAbstractEntity, Builder>(IntellijWs, 44, CompositeAbstractEntity) {
    }
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
    
    companion object: ObjType<SimpleChildAbstractEntity, Builder>(IntellijWs, 45, SimpleAbstractEntity) {
    }
    //@formatter:on
    //endregion

}