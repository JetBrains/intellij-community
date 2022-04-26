package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity



interface FirstEntityWithPId : WorkspaceEntityWithPersistentId {
  val data: String
  override val persistentId: FirstPId
    get() {
      return FirstPId(data)
    }


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: FirstEntityWithPId, ModifiableWorkspaceEntity<FirstEntityWithPId>, ObjBuilder<FirstEntityWithPId> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<FirstEntityWithPId, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FirstEntityWithPId {
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

data class FirstPId(override val presentableName: String) : PersistentEntityId<FirstEntityWithPId>

interface SecondEntityWithPId : WorkspaceEntityWithPersistentId {
  val data: String
  override val persistentId: SecondPId
    get() = SecondPId(data)


  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: SecondEntityWithPId, ModifiableWorkspaceEntity<SecondEntityWithPId>, ObjBuilder<SecondEntityWithPId> {
      override var data: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<SecondEntityWithPId, Builder>() {
      operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SecondEntityWithPId {
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

data class SecondPId(override val presentableName: String) : PersistentEntityId<SecondEntityWithPId>
data class TestPId(var presentableName: String, val aaa: Int?, var angry: Boolean)