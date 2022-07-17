package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface SimplePersistentIdEntity : WorkspaceEntityWithPersistentId {
  val version: Int
  val name: String

  override val persistentId: SimpleId
    get() = SimpleId(name)
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: SimplePersistentIdEntity, ModifiableWorkspaceEntity<SimplePersistentIdEntity>, ObjBuilder<SimplePersistentIdEntity> {
      override var version: Int
      override var entitySource: EntitySource
      override var name: String
  }

  companion object: Type<SimplePersistentIdEntity, Builder>() {
      operator fun invoke(version: Int, entitySource: EntitySource, name: String, init: (Builder.() -> Unit)? = null): SimplePersistentIdEntity {
          val builder = builder()
          builder.version = version
          builder.entitySource = entitySource
          builder.name = name
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: SimplePersistentIdEntity, modification: SimplePersistentIdEntity.Builder.() -> Unit) = modifyEntity(SimplePersistentIdEntity.Builder::class.java, entity, modification)
//endregion

data class SimpleId(val name: String) : PersistentEntityId<SimplePersistentIdEntity> {
  override val presentableName: String
    get() = name
}