// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface JavaProjectSettingsEntity : WorkspaceEntity {
  @Parent
  val projectSettings: ProjectSettingsEntity

  val compilerOutput: VirtualFileUrl?
  val languageLevelId: String?
  val languageLevelDefault: Boolean?

  //region generated code
  @Deprecated(message = "Use JavaProjectSettingsEntityBuilder instead")
  interface Builder : JavaProjectSettingsEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getProjectSettings(): ProjectSettingsEntity.Builder = projectSettings as ProjectSettingsEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setProjectSettings(value: ProjectSettingsEntity.Builder) {
      projectSettings = value
    }
  }

  companion object : EntityType<JavaProjectSettingsEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = JavaProjectSettingsEntityType.compatibilityInvoke(entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyJavaProjectSettingsEntity(
  entity: JavaProjectSettingsEntity,
  modification: JavaProjectSettingsEntity.Builder.() -> Unit,
): JavaProjectSettingsEntity {
  return modifyEntity(JavaProjectSettingsEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
var ProjectSettingsEntity.Builder.javaProjectSettings: JavaProjectSettingsEntity.Builder?
  get() = (this as ProjectSettingsEntityBuilder).javaProjectSettings as JavaProjectSettingsEntity.Builder?
  set(value) {
    (this as ProjectSettingsEntityBuilder).javaProjectSettings = value
  }
//endregion

val ProjectSettingsEntity.javaProjectSettings: JavaProjectSettingsEntity?
  by WorkspaceEntity.extension()