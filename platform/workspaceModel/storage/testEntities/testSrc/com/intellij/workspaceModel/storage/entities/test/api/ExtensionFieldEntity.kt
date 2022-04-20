package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx

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

import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion





interface MainEntity : WorkspaceEntity {
    val x: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: MainEntity, ModifiableWorkspaceEntity<MainEntity>, ObjBuilder<MainEntity> {
        override var x: String
        override var entitySource: EntitySource
    }
    
    companion object: Type<MainEntity, Builder>()
    //@formatter:on
    //endregion

}

interface AttachedEntity : WorkspaceEntity {
    val ref: MainEntity
    val data: String


    //region generated code
    //@formatter:off
    @GeneratedCodeApiVersion(0)
    interface Builder: AttachedEntity, ModifiableWorkspaceEntity<AttachedEntity>, ObjBuilder<AttachedEntity> {
        override var ref: MainEntity
        override var entitySource: EntitySource
        override var data: String
    }
    
    companion object: Type<AttachedEntity, Builder>()
    //@formatter:on
    //endregion

}

val MainEntity.child: @Child AttachedEntity?
    get() = referrersx(AttachedEntity::ref).singleOrNull()
