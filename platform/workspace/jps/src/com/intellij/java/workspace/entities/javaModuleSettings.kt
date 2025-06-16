// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

interface JavaModuleSettingsEntity: WorkspaceEntity {
  @Parent
  val module: ModuleEntity

  val inheritedCompilerOutput: Boolean
  val excludeOutput: Boolean
  val compilerOutput: VirtualFileUrl?
  val compilerOutputForTests: VirtualFileUrl?
  val languageLevelId: @NonNls String?
  val manifestAttributes: Map<String, String>
  @Default get() = emptyMap()

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<JavaModuleSettingsEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var inheritedCompilerOutput: Boolean
    var excludeOutput: Boolean
    var compilerOutput: VirtualFileUrl?
    var compilerOutputForTests: VirtualFileUrl?
    var languageLevelId: String?
    var manifestAttributes: Map<String, String>
  }

  companion object : EntityType<JavaModuleSettingsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      inheritedCompilerOutput: Boolean,
      excludeOutput: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.inheritedCompilerOutput = inheritedCompilerOutput
      builder.excludeOutput = excludeOutput
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyJavaModuleSettingsEntity(
  entity: JavaModuleSettingsEntity,
  modification: JavaModuleSettingsEntity.Builder.() -> Unit,
): JavaModuleSettingsEntity {
  return modifyEntity(JavaModuleSettingsEntity.Builder::class.java, entity, modification)
}

var ModuleEntity.Builder.javaSettings: JavaModuleSettingsEntity.Builder?
  by WorkspaceEntity.extensionBuilder(JavaModuleSettingsEntity::class.java)
//endregion

val ModuleEntity.javaSettings: JavaModuleSettingsEntity?
  by WorkspaceEntity.extension()
