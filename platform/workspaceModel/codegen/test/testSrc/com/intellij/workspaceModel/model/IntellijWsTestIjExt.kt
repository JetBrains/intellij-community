package org.jetbrains.deft.IntellijWsTestIjExt

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.referrersx
import com.intellij.workspaceModel.storage.referrersy
import model.ext.AttachedEntity
import model.ext.AttachedEntityImpl
import model.ext.AttachedEntityList
import model.ext.AttachedEntityListImpl
import model.ext.AttachedEntityParentList
import model.ext.AttachedEntityParentListImpl
import model.ext.AttachedEntityToParent
import model.ext.AttachedEntityToParentImpl
import model.ext.MainEntity
import model.ext.MainEntityImpl
import model.ext.MainEntityList
import model.ext.MainEntityListImpl
import model.ext.MainEntityParentList
import model.ext.MainEntityParentListImpl
import model.ext.MainEntityToParent
import model.ext.MainEntityToParentImpl
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.impl.ObjModule

import org.jetbrains.deft.impl.* 
                        
object IntellijWsTestIjExt: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWsTestIjExt")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(8)
        add(MainEntityParentList)
        add(AttachedEntityParentList)
        add(MainEntityToParent)
        add(AttachedEntityToParent)
        add(MainEntity)
        add(AttachedEntity)
        add(MainEntityList)
        add(AttachedEntityList)
    }
}

var AttachedEntityParentList.Builder.ref: MainEntityParentList?
    get() {
        return referrersy(MainEntityParentList::children).singleOrNull()
    }
    set(value) {
        val diff = (this as AttachedEntityParentListImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as MainEntityParentListImpl.Builder).diff == null) {
                    value._children = (value._children ?: emptyList()) + this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneParentOfChild(MainEntityParentListImpl.CHILDREN_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("MainEntityParentList", "children", false, MainEntityParentListImpl.CHILDREN_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as MainEntityParentListImpl.Builder)._children = ((value as MainEntityParentListImpl.Builder)._children ?: emptyList()) + this
            }
        }
    }

var AttachedEntityToParent.Builder.ref: MainEntityToParent
    get() {
        return referrersx(MainEntityToParent::child).single()
    }
    set(value) {
        val diff = (this as AttachedEntityToParentImpl.Builder).diff
        if (diff != null) {
            if ((value as MainEntityToParentImpl.Builder).diff == null) {
                value._child = this
                diff.addEntity(value)
            }
            diff.updateOneToOneParentOfChild(MainEntityToParentImpl.CHILD_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("MainEntityToParent", "child", false, MainEntityToParentImpl.CHILD_CONNECTION_ID)
            this.extReferences[key] = value
            
            (value as MainEntityToParentImpl.Builder)._child = this
        }
    }

var MainEntity.Builder.child: @Child AttachedEntity?
    get() {
        return referrersx(AttachedEntity::ref).singleOrNull()
    }
    set(value) {
        val diff = (this as MainEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as AttachedEntityImpl.Builder).diff == null) {
                    value._ref = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(AttachedEntityImpl.REF_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("AttachedEntity", "ref", true, AttachedEntityImpl.REF_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as AttachedEntityImpl.Builder)._ref = this
            }
        }
    }

var MainEntityList.Builder.child: @Child List<AttachedEntityList>
    get() {
        return referrersx(AttachedEntityList::ref)
    }
    set(value) {
        val diff = (this as MainEntityListImpl.Builder).diff
        if (diff != null) {
            for (item in value) {
                if ((item as AttachedEntityListImpl.Builder).diff == null) {
                    item._ref = this
                    diff.addEntity(item)
                }
            }
            diff.updateOneToManyChildrenOfParent(AttachedEntityListImpl.REF_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("AttachedEntityList", "ref", true, AttachedEntityListImpl.REF_CONNECTION_ID)
            this.extReferences[key] = value
            
            for (item in value) {
                (item as AttachedEntityListImpl.Builder)._ref = this
            }
        }
    }


fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityParentList, modification: MainEntityParentList.Builder.() -> Unit) = modifyEntity(MainEntityParentListImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityParentList, modification: AttachedEntityParentList.Builder.() -> Unit) = modifyEntity(AttachedEntityParentListImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityToParent, modification: MainEntityToParent.Builder.() -> Unit) = modifyEntity(MainEntityToParentImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityToParent, modification: AttachedEntityToParent.Builder.() -> Unit) = modifyEntity(AttachedEntityToParentImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntity, modification: MainEntity.Builder.() -> Unit) = modifyEntity(MainEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntity, modification: AttachedEntity.Builder.() -> Unit) = modifyEntity(AttachedEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: MainEntityList, modification: MainEntityList.Builder.() -> Unit) = modifyEntity(MainEntityListImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: AttachedEntityList, modification: AttachedEntityList.Builder.() -> Unit) = modifyEntity(AttachedEntityListImpl.Builder::class.java, entity, modification)
