// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

interface ProjectSettingsEntity : WorkspaceEntity {
  val projectSdk: SdkId?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ProjectSettingsEntity> {
    override var entitySource: EntitySource
    var projectSdk: SdkId?
  }

  companion object : EntityType<ProjectSettingsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyProjectSettingsEntity(
  entity: ProjectSettingsEntity,
  modification: ProjectSettingsEntity.Builder.() -> Unit,
): ProjectSettingsEntity = modifyEntity(ProjectSettingsEntity.Builder::class.java, entity, modification)
//endregion
