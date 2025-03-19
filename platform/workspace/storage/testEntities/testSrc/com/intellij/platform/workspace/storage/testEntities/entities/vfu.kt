// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface VFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<VFUEntity> {
    override var entitySource: EntitySource
    var data: String
    var fileProperty: VirtualFileUrl
  }

  companion object : EntityType<VFUEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      fileProperty: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.fileProperty = fileProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyVFUEntity(
  entity: VFUEntity,
  modification: VFUEntity.Builder.() -> Unit,
): VFUEntity {
  return modifyEntity(VFUEntity.Builder::class.java, entity, modification)
}
//endregion

interface VFUWithTwoPropertiesEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl
  val secondFileProperty: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<VFUWithTwoPropertiesEntity> {
    override var entitySource: EntitySource
    var data: String
    var fileProperty: VirtualFileUrl
    var secondFileProperty: VirtualFileUrl
  }

  companion object : EntityType<VFUWithTwoPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      fileProperty: VirtualFileUrl,
      secondFileProperty: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.fileProperty = fileProperty
      builder.secondFileProperty = secondFileProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyVFUWithTwoPropertiesEntity(
  entity: VFUWithTwoPropertiesEntity,
  modification: VFUWithTwoPropertiesEntity.Builder.() -> Unit,
): VFUWithTwoPropertiesEntity {
  return modifyEntity(VFUWithTwoPropertiesEntity.Builder::class.java, entity, modification)
}
//endregion

interface NullableVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: VirtualFileUrl?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<NullableVFUEntity> {
    override var entitySource: EntitySource
    var data: String
    var fileProperty: VirtualFileUrl?
  }

  companion object : EntityType<NullableVFUEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyNullableVFUEntity(
  entity: NullableVFUEntity,
  modification: NullableVFUEntity.Builder.() -> Unit,
): NullableVFUEntity {
  return modifyEntity(NullableVFUEntity.Builder::class.java, entity, modification)
}
//endregion

interface ListVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ListVFUEntity> {
    override var entitySource: EntitySource
    var data: String
    var fileProperty: MutableList<VirtualFileUrl>
  }

  companion object : EntityType<ListVFUEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      fileProperty: List<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.fileProperty = fileProperty.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyListVFUEntity(
  entity: ListVFUEntity,
  modification: ListVFUEntity.Builder.() -> Unit,
): ListVFUEntity {
  return modifyEntity(ListVFUEntity.Builder::class.java, entity, modification)
}
//endregion

interface SetVFUEntity : WorkspaceEntity {
  val data: String
  val fileProperty: Set<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SetVFUEntity> {
    override var entitySource: EntitySource
    var data: String
    var fileProperty: MutableSet<VirtualFileUrl>
  }

  companion object : EntityType<SetVFUEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      fileProperty: Set<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.fileProperty = fileProperty.toMutableWorkspaceSet()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifySetVFUEntity(
  entity: SetVFUEntity,
  modification: SetVFUEntity.Builder.() -> Unit,
): SetVFUEntity {
  return modifyEntity(SetVFUEntity.Builder::class.java, entity, modification)
}
//endregion
