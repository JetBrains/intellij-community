package com.intellij.workspace.model.testing

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.IntellijWs.IntellijWs
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
import org.jetbrains.deft.IntellijWsTest.IntellijWsTest




interface ParentSubEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildSubEntity

    //region generated code
    //@formatter:off
    interface Builder: ParentSubEntity, ObjBuilder<ParentSubEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildSubEntity
    }
    
    companion object: ObjType<ParentSubEntity, Builder>(IntellijWsTest, 32) {
        val parentData: Field<ParentSubEntity, String> = Field(this, 0, "parentData", TString)
        val entitySource: Field<ParentSubEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val child: Field<ParentSubEntity, ChildSubEntity> = Field(this, 0, "child", TRef("org.jetbrains.deft.IntellijWsTest", 33, child = true))
    }
    //@formatter:on
    //endregion
}

interface ChildSubEntity : WorkspaceEntity {
    val parentEntity: ParentSubEntity

    @Child
    val child: ChildSubSubEntity

    //region generated code
    //@formatter:off
    interface Builder: ChildSubEntity, ObjBuilder<ChildSubEntity> {
        override var parentEntity: ParentSubEntity
        override var entitySource: EntitySource
        override var child: ChildSubSubEntity
    }
    
    companion object: ObjType<ChildSubEntity, Builder>(IntellijWsTest, 33) {
        val parentEntity: Field<ChildSubEntity, ParentSubEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWsTest", 32))
        val entitySource: Field<ChildSubEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val child: Field<ChildSubEntity, ChildSubSubEntity> = Field(this, 0, "child", TRef("org.jetbrains.deft.IntellijWsTest", 34, child = true))
    }
    //@formatter:on
    //endregion
}
interface ChildSubSubEntity : WorkspaceEntity {
    val parentEntity: ChildSubEntity

    val childData: String

    //region generated code
    //@formatter:off
    interface Builder: ChildSubSubEntity, ObjBuilder<ChildSubSubEntity> {
        override var parentEntity: ChildSubEntity
        override var entitySource: EntitySource
        override var childData: String
    }
    
    companion object: ObjType<ChildSubSubEntity, Builder>(IntellijWsTest, 34) {
        val parentEntity: Field<ChildSubSubEntity, ChildSubEntity> = Field(this, 0, "parentEntity", TRef("org.jetbrains.deft.IntellijWsTest", 33))
        val entitySource: Field<ChildSubSubEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
        val childData: Field<ChildSubSubEntity, String> = Field(this, 0, "childData", TString)
    }
    //@formatter:on
    //endregion
}
