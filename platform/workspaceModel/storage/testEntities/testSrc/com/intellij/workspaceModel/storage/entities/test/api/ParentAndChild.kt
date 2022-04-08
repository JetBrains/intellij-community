package com.intellij.workspaceModel.storage.entities.test.api

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
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.TestEntities.TestEntities






interface ParentEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildEntity

    //region generated code
    //@formatter:off
    interface Builder: ParentEntity, ModifiableWorkspaceEntity<ParentEntity>, ObjBuilder<ParentEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildEntity
    }
    
    companion object: ObjType<ParentEntity, Builder>(TestEntities, 51)
    //@formatter:on
    //endregion

}

interface ChildEntity : WorkspaceEntity {
    val childData: String

//    override val parent: ParentEntity
    val parentEntity: ParentEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildEntity, ModifiableWorkspaceEntity<ChildEntity>, ObjBuilder<ChildEntity> {
        override var childData: String
        override var entitySource: EntitySource
        override var parentEntity: ParentEntity
    }
    
    companion object: ObjType<ChildEntity, Builder>(TestEntities, 52)
    //@formatter:on
    //endregion

}