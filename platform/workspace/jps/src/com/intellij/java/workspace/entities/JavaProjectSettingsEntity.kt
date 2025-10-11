// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface JavaProjectSettingsEntity : WorkspaceEntity {
  @Parent
  val projectSettings: ProjectSettingsEntity

  val compilerOutput: VirtualFileUrl?
  val languageLevelId: String?
  val languageLevelDefault: Boolean?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<JavaProjectSettingsEntity> {
    override var entitySource: EntitySource
    var projectSettings: ProjectSettingsEntity.Builder
    var compilerOutput: VirtualFileUrl?
    var languageLevelId: String?
    var languageLevelDefault: Boolean?
  }

  companion object : EntityType<JavaProjectSettingsEntity, Builder>() {
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
fun MutableEntityStorage.modifyJavaProjectSettingsEntity(
  entity: JavaProjectSettingsEntity,
  modification: JavaProjectSettingsEntity.Builder.() -> Unit,
): JavaProjectSettingsEntity = modifyEntity(JavaProjectSettingsEntity.Builder::class.java, entity, modification)

var ProjectSettingsEntity.Builder.javaProjectSettings: JavaProjectSettingsEntity.Builder?
  by WorkspaceEntity.extensionBuilder(JavaProjectSettingsEntity::class.java)
//endregion

val ProjectSettingsEntity.javaProjectSettings: JavaProjectSettingsEntity?
  by WorkspaceEntity.extension()