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







interface ParentSubEntity : WorkspaceEntity {
    val parentData: String

    @Child
    val child: ChildSubEntity


    //region generated code
    //@formatter:off
    interface Builder: ParentSubEntity, ModifiableWorkspaceEntity<ParentSubEntity>, ObjBuilder<ParentSubEntity> {
        override var parentData: String
        override var entitySource: EntitySource
        override var child: ChildSubEntity
    }
    
    companion object: ObjType<ParentSubEntity, Builder>(TestEntities, 75)
    //@formatter:on
    //endregion

}

interface ChildSubEntity : WorkspaceEntity {
    val parentEntity: ParentSubEntity

    @Child
    val child: ChildSubSubEntity


    //region generated code
    //@formatter:off
    interface Builder: ChildSubEntity, ModifiableWorkspaceEntity<ChildSubEntity>, ObjBuilder<ChildSubEntity> {
        override var parentEntity: ParentSubEntity
        override var entitySource: EntitySource
        override var child: ChildSubSubEntity
    }
    
    companion object: ObjType<ChildSubEntity, Builder>(TestEntities, 76)
    //@formatter:on
    //endregion

}
interface ChildSubSubEntity : WorkspaceEntity {
    val parentEntity: ChildSubEntity

    val childData: String


    //region generated code
    //@formatter:off
    interface Builder: ChildSubSubEntity, ModifiableWorkspaceEntity<ChildSubSubEntity>, ObjBuilder<ChildSubSubEntity> {
        override var parentEntity: ChildSubEntity
        override var entitySource: EntitySource
        override var childData: String
    }
    
    companion object: ObjType<ChildSubSubEntity, Builder>(TestEntities, 77)
    //@formatter:on
    //endregion

}
