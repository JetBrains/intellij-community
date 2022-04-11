package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type








interface MainEntityList : WorkspaceEntity {
    val x: String

    //region generated code
    //@formatter:off
    interface Builder: MainEntityList, ModifiableWorkspaceEntity<MainEntityList>, ObjBuilder<MainEntityList> {
        override var x: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<MainEntityList, Builder>(63)
    //@formatter:on
    //endregion
}

interface AttachedEntityList : WorkspaceEntity {
    val ref: MainEntityList?
    val data: String


    //region generated code
    //@formatter:off
    interface Builder: AttachedEntityList, ModifiableWorkspaceEntity<AttachedEntityList>, ObjBuilder<AttachedEntityList> {
        override var ref: MainEntityList?
        override var entitySource: EntitySource
        override var data: String
    }
    
    companion object: Type<AttachedEntityList, Builder>(64)
    //@formatter:on
    //endregion

}

val MainEntityList.child: List<@Child AttachedEntityList>
    get() = referrersx(AttachedEntityList::ref)
