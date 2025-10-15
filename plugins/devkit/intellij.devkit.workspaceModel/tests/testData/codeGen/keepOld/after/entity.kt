package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface SimpleEntity : WorkspaceEntity {
  val version: Int
  val name: String

  //region generated code
  @Deprecated(message = "Use ModifiableSimpleEntity instead")
  interface Builder : ModifiableSimpleEntity
  companion object : EntityType<SimpleEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = SimpleEntityType.compatibilityInvoke(version, name, entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifySimpleEntity(
  entity: SimpleEntity,
  modification: SimpleEntity.Builder.() -> Unit,
): SimpleEntity {
  return modifyEntity(SimpleEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
@Parent
var SimpleEntity.Builder.simpleParent: SimpleParentByExtension.Builder
  get() = (this as ModifiableSimpleEntity).simpleParent as SimpleParentByExtension.Builder
  set(value) {
    (this as ModifiableSimpleEntity).simpleParent = value
  }
//endregion

interface SimpleParentByExtension : WorkspaceEntity {
  val simpleName: String
  val simpleChild: SimpleEntity?

  //region generated code
  @Deprecated(message = "Use ModifiableSimpleParentByExtension instead")
  interface Builder : ModifiableSimpleParentByExtension {
    @Deprecated(message = "Use new API instead")
    fun getSimpleChild(): SimpleEntity.Builder? = simpleChild as SimpleEntity.Builder?

    @Deprecated(message = "Use new API instead")
    fun setSimpleChild(value: SimpleEntity.Builder?) {
      simpleChild = value
    }
  }

  companion object : EntityType<SimpleParentByExtension, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      simpleName: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = SimpleParentByExtensionType.compatibilityInvoke(simpleName, entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifySimpleParentByExtension(
  entity: SimpleParentByExtension,
  modification: SimpleParentByExtension.Builder.() -> Unit,
): SimpleParentByExtension {
  return modifyEntity(SimpleParentByExtension.Builder::class.java, entity, modification)
}
//endregion

@Parent val SimpleEntity.simpleParent: SimpleParentByExtension
  by WorkspaceEntity.extension()