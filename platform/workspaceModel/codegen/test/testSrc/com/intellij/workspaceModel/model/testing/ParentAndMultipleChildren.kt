package com.intellij.workspace.model.testing

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import org.jetbrains.deft.IntellijWsTest.IntellijWsTest



interface ParentMultipleEntity : WorkspaceEntity {
    val parentData: String
    val children: List<@Child ChildMultipleEntity>

    //region generated code
    //@formatter:off
    interface Builder: ParentMultipleEntity, ObjBuilder<ParentMultipleEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var children: List<ChildMultipleEntity>
    }
    
    companion object: ObjType<ParentMultipleEntity, Builder>(IntellijWsTest, 39) {
        val parentData: Field<ParentMultipleEntity, String> = Field(this, 0, "parentData", TString)
        val entitySource: Field<ParentMultipleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val children: Field<ParentMultipleEntity, List<ChildMultipleEntity>> = Field(this, 0, "children", TList(TRef("org.jetbrains.deft.IntellijWsTest", 40, child = true)))
    }
    //@formatter:on
    //endregion
}

interface ChildMultipleEntity : WorkspaceEntity {
    val childData: String

    val parentEntity: ParentMultipleEntity

    //region generated code
    //@formatter:off
    interface Builder: ChildMultipleEntity, ObjBuilder<ChildMultipleEntity> {
        override var childData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentMultipleEntity
    }
    
    companion object: ObjType<ChildMultipleEntity, Builder>(IntellijWsTest, 40) {
        val childData: Field<ChildMultipleEntity, String> = Field(this, 0, "childData", TString)
        val entitySource: Field<ChildMultipleEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val parentEntity: Field<ChildMultipleEntity, ParentMultipleEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWsTest", 39))
    }
    //@formatter:on
    //endregion
}