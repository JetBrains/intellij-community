package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.IntellijWs.IntellijWs
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.TestEntities.TestEntities







interface MainEntityToParent : WorkspaceEntity {
    val child: @Child AttachedEntityToParent?
    val x: String


    //region generated code
    //@formatter:off
    interface Builder: MainEntityToParent, ModifiableWorkspaceEntity<MainEntityToParent>, ObjBuilder<MainEntityToParent> {
        override var child: AttachedEntityToParent?
        override var entitySource: EntitySource
        override var x: String
    }
    
    companion object: ObjType<MainEntityToParent, Builder>(TestEntities, 29)
    //@formatter:on
    //endregion

}

interface AttachedEntityToParent : WorkspaceEntity {
    val data: String


    //region generated code
    //@formatter:off
    interface Builder: AttachedEntityToParent, ModifiableWorkspaceEntity<AttachedEntityToParent>, ObjBuilder<AttachedEntityToParent> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: ObjType<AttachedEntityToParent, Builder>(TestEntities, 30)
    //@formatter:on
    //endregion

}

val AttachedEntityToParent.ref: MainEntityToParent
    get() = referrersx(MainEntityToParent::child).single()
