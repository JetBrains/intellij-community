package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.referrersx
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase





interface MainEntityList : WorkspaceEntity {
  val x: String

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: MainEntityList, ModifiableWorkspaceEntity<MainEntityList>, ObjBuilder<MainEntityList> {
      override var x: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<MainEntityList, Builder>() {
      operator fun invoke(x: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): MainEntityList {
          val builder = builder()
          builder.x = x
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion
}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: MainEntityList, modification: MainEntityList.Builder.() -> Unit) = modifyEntity(MainEntityList.Builder::class.java, entity, modification)
var MainEntityList.Builder.child: @Child List<AttachedEntityList>
    get() {
        return referrersx(AttachedEntityList::ref)
    }
    set(value) {
        val diff = (this as ModifiableWorkspaceEntityBase<*>).diff
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

//endregion

interface AttachedEntityList : WorkspaceEntity {
  val ref: MainEntityList?
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AttachedEntityList, ModifiableWorkspaceEntity<AttachedEntityList>, ObjBuilder<AttachedEntityList> {
      override var ref: MainEntityList?
      override var entitySource: EntitySource
      override var data: String
  }
  
  companion object: Type<AttachedEntityList, Builder>() {
      operator fun invoke(entitySource: EntitySource, data: String, init: (Builder.() -> Unit)? = null): AttachedEntityList {
          val builder = builder()
          builder.entitySource = entitySource
          builder.data = data
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: AttachedEntityList, modification: AttachedEntityList.Builder.() -> Unit) = modifyEntity(AttachedEntityList.Builder::class.java, entity, modification)
//endregion

val MainEntityList.child: List<@Child AttachedEntityList>
  get() = referrersx(AttachedEntityList::ref)
