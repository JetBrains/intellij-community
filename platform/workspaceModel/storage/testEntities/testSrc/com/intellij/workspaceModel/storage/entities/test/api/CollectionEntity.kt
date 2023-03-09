// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet



interface CollectionFieldEntity : WorkspaceEntity {
  val versions: Set<Int>
  val names: List<String>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : CollectionFieldEntity, WorkspaceEntity.Builder<CollectionFieldEntity>, ObjBuilder<CollectionFieldEntity> {
    override var entitySource: EntitySource
    override var versions: MutableSet<Int>
    override var names: MutableList<String>
  }

  companion object : Type<CollectionFieldEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(versions: Set<Int>,
                        names: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): CollectionFieldEntity {
      val builder = builder()
      builder.versions = versions.toMutableWorkspaceSet()
      builder.names = names.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: CollectionFieldEntity, modification: CollectionFieldEntity.Builder.() -> Unit) = modifyEntity(
  CollectionFieldEntity.Builder::class.java, entity, modification)
//endregion
