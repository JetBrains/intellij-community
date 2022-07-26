package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

interface SimplePersistentIdEntity : WorkspaceEntityWithPersistentId {
  val version: Int
  val name: String
  val related: SimpleId
  val sealedClassWithLinks: SealedClassWithLinks

  override val persistentId: SimpleId
    get() = SimpleId(name)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SimplePersistentIdEntity, ModifiableWorkspaceEntity<SimplePersistentIdEntity>, ObjBuilder<SimplePersistentIdEntity> {
    override var version: Int
    override var entitySource: EntitySource
    override var name: String
    override var related: SimpleId
    override var sealedClassWithLinks: SealedClassWithLinks
  }

  companion object : Type<SimplePersistentIdEntity, Builder>() {
    operator fun invoke(version: Int,
                        name: String,
                        related: SimpleId,
                        sealedClassWithLinks: SealedClassWithLinks,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): SimplePersistentIdEntity {
      val builder = builder()
      builder.version = version
      builder.entitySource = entitySource
      builder.name = name
      builder.related = related
      builder.sealedClassWithLinks = sealedClassWithLinks
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SimplePersistentIdEntity,
                                      modification: SimplePersistentIdEntity.Builder.() -> Unit) = modifyEntity(
  SimplePersistentIdEntity.Builder::class.java, entity, modification)
//endregion

data class SimpleId(val name: String) : PersistentEntityId<SimplePersistentIdEntity> {
  override val presentableName: String
    get() = name
}

sealed class SealedClassWithLinks {
  object Nothing : SealedClassWithLinks()
  data class Single(val id: SimpleId) : SealedClassWithLinks()

  sealed class Many() : SealedClassWithLinks() {
    data class Ordered(val list: List<SimpleId>) : Many()
    data class Unordered(val set: List<SimpleId>) : Many()
  }

}