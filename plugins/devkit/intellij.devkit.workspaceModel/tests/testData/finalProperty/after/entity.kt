package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface FinalFieldsEntity : WorkspaceEntity {
  val descriptor: AnotherDataClass

  val version: Int get() = descriptor.version
  val source: Boolean get() = descriptor.source
  val displayName: String? get() = descriptor.displayName
  val gitUrl: String? get() = descriptor.url
  val gitRevision: String? get() = descriptor.revision

  fun isEditable(): Boolean {
    return descriptor.source && displayName != null
  }

  fun isReadOnly(): Boolean {
    return !isEditable() && descriptor.url != null
  }

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : FinalFieldsEntity, WorkspaceEntity.Builder<FinalFieldsEntity>, ObjBuilder<FinalFieldsEntity> {
    override var entitySource: EntitySource
    override var descriptor: AnotherDataClass
  }

  companion object : Type<FinalFieldsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(descriptor: AnotherDataClass, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FinalFieldsEntity {
      val builder = builder()
      builder.descriptor = descriptor
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: FinalFieldsEntity, modification: FinalFieldsEntity.Builder.() -> Unit) = modifyEntity(
  FinalFieldsEntity.Builder::class.java, entity, modification)
//endregion

data class AnotherDataClass(val name: String,
                            val version: Int,
                            val source: Boolean,
                            val displayName: String? = null,
                            val url: String? = null,
                            val revision: String? = null)