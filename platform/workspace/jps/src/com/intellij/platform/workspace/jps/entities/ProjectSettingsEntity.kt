// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

/**
 * **Do not add new fields to this entity.** New fields are not serialized to the .iml file and will be
 * lost when the project is reopened. To store additional data, declare a new entity with a
 * [@Parent][com.intellij.platform.workspace.storage.annotations.Parent] reference to this one.
 */
interface ProjectSettingsEntity : WorkspaceEntity {
  val projectSdk: SdkId?

  //region generated code
  @Deprecated(message = "Use ProjectSettingsEntityBuilder instead")
  interface Builder : ProjectSettingsEntityBuilder
  companion object : EntityType<ProjectSettingsEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ProjectSettingsEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyProjectSettingsEntity(
  entity: ProjectSettingsEntity,
  modification: ProjectSettingsEntity.Builder.() -> Unit,
): ProjectSettingsEntity {
  return modifyEntity(ProjectSettingsEntity.Builder::class.java, entity, modification)
}
//endregion
