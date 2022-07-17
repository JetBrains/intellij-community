package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String
  val isSimple: Boolean
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: SimpleEntity, ModifiableWorkspaceEntity<SimpleEntity>, ObjBuilder<SimpleEntity> {
      override var version: Int
      override var entitySource: EntitySource
      override var name: String
      override var isSimple: Boolean
  }

  companion object: Type<SimpleEntity, Builder>() {
      operator fun invoke(version: Int, entitySource: EntitySource, name: String, isSimple: Boolean, init: (Builder.() -> Unit)? = null): SimpleEntity {
          val builder = builder()
          builder.version = version
          builder.entitySource = entitySource
          builder.name = name
          builder.isSimple = isSimple
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: SimpleEntity, modification: SimpleEntity.Builder.() -> Unit) = modifyEntity(SimpleEntity.Builder::class.java, entity, modification)
//endregion