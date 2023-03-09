package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.MutableEntityStorage



interface SampleEntity2 : WorkspaceEntity {
  val data: String
  val boolData: Boolean
  val optionalData: String?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SampleEntity2, WorkspaceEntity.Builder<SampleEntity2>, ObjBuilder<SampleEntity2> {
    override var entitySource: EntitySource
    override var data: String
    override var boolData: Boolean
    override var optionalData: String?
  }

  companion object : Type<SampleEntity2, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, boolData: Boolean, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SampleEntity2 {
      val builder = builder()
      builder.data = data
      builder.boolData = boolData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: SampleEntity2, modification: SampleEntity2.Builder.() -> Unit) = modifyEntity(
  SampleEntity2.Builder::class.java, entity, modification)
//endregion

interface VFUEntity2 : WorkspaceEntity {
  val data: String
  val filePath: VirtualFileUrl?
  val directoryPath: VirtualFileUrl
  val notNullRoots: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : VFUEntity2, WorkspaceEntity.Builder<VFUEntity2>, ObjBuilder<VFUEntity2> {
    override var entitySource: EntitySource
    override var data: String
    override var filePath: VirtualFileUrl?
    override var directoryPath: VirtualFileUrl
    override var notNullRoots: MutableList<VirtualFileUrl>
  }

  companion object : Type<VFUEntity2, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String,
                        directoryPath: VirtualFileUrl,
                        notNullRoots: List<VirtualFileUrl>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): VFUEntity2 {
      val builder = builder()
      builder.data = data
      builder.directoryPath = directoryPath
      builder.notNullRoots = notNullRoots.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: VFUEntity2, modification: VFUEntity2.Builder.() -> Unit) = modifyEntity(
  VFUEntity2.Builder::class.java, entity, modification)
//endregion
