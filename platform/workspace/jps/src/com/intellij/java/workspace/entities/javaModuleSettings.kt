// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
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
  @Deprecated(message = "Use JavaModuleSettingsEntityBuilder instead")
  interface Builder : JavaModuleSettingsEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModule(value: ModuleEntity.Builder) {
      module = value
    }
  }

  companion object : EntityType<JavaModuleSettingsEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      inheritedCompilerOutput: Boolean,
      excludeOutput: Boolean,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = JavaModuleSettingsEntityType.compatibilityInvoke(inheritedCompilerOutput, excludeOutput, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyJavaModuleSettingsEntity(
  entity: JavaModuleSettingsEntity,
  modification: JavaModuleSettingsEntity.Builder.() -> Unit,
): JavaModuleSettingsEntity {
  return modifyEntity(JavaModuleSettingsEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.javaSettings: JavaModuleSettingsEntity.Builder?
  get() = (this as ModuleEntityBuilder).javaSettings as JavaModuleSettingsEntity.Builder?
  set(value) {
    (this as ModuleEntityBuilder).javaSettings = value
  }
//endregion

val ModuleEntity.javaSettings: JavaModuleSettingsEntity?
  by WorkspaceEntity.extension()
