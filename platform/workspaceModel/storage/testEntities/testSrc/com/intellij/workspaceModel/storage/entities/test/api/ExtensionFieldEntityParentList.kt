package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersy

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.*
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity

import org.jetbrains.deft.Type









interface MainEntityParentList : WorkspaceEntity {
    val x: String
    val children: List<@Child AttachedEntityParentList>


    //region generated code
    //@formatter:off
    interface Builder: MainEntityParentList, ModifiableWorkspaceEntity<MainEntityParentList>, ObjBuilder<MainEntityParentList> {
        override var x: String
        override var entitySource: EntitySource
        override var children: List<AttachedEntityParentList>
    }
    
    companion object: Type<MainEntityParentList, Builder>(29)
    //@formatter:on
    //endregion

}

interface AttachedEntityParentList : WorkspaceEntity {
    val data: String


    //region generated code
    //@formatter:off
    interface Builder: AttachedEntityParentList, ModifiableWorkspaceEntity<AttachedEntityParentList>, ObjBuilder<AttachedEntityParentList> {
        override var data: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<AttachedEntityParentList, Builder>(30)
    //@formatter:on
    //endregion

}

val AttachedEntityParentList.ref: MainEntityParentList?
    get() =  referrersy(MainEntityParentList::children).singleOrNull()
