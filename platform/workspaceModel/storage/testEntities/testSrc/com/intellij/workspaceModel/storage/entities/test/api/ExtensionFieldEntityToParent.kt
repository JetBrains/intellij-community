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
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.referrersx




interface MainEntityToParent : WorkspaceEntity {
  val child: @Child AttachedEntityToParent?
  val x: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: MainEntityToParent, ModifiableWorkspaceEntity<MainEntityToParent>, ObjBuilder<MainEntityToParent> {
      override var child: AttachedEntityToParent?
      override var entitySource: EntitySource
      override var x: String
  }
  
  companion object: Type<MainEntityToParent, Builder>() {
      operator fun invoke(entitySource: EntitySource, x: String, init: (Builder.() -> Unit)? = null): MainEntityToParent {
          val builder = builder()
          builder.entitySource = entitySource
          builder.x = x
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: MainEntityToParent, modification: MainEntityToParent.Builder.() -> Unit) = modifyEntity(MainEntityToParent.Builder::class.java, entity, modification)
//endregion

interface AttachedEntityToParent : WorkspaceEntity {
  val data: String


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: AttachedEntityToParent, ModifiableWorkspaceEntity<AttachedEntityToParent>, ObjBuilder<AttachedEntityToParent> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<AttachedEntityToParent, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): AttachedEntityToParent {
          val builder = builder()
          builder.data = data
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: AttachedEntityToParent, modification: AttachedEntityToParent.Builder.() -> Unit) = modifyEntity(AttachedEntityToParent.Builder::class.java, entity, modification)
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

//endregion

val AttachedEntityToParent.ref: MainEntityToParent
  get() = referrersx(MainEntityToParent::child).single()
