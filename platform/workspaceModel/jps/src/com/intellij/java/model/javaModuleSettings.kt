// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.model

import com.intellij.platform.workspaceModel.storage.*
import com.intellij.platform.workspaceModel.storage.annotations.Child
import com.intellij.platform.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.platform.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

interface JavaModuleSettingsEntity: WorkspaceEntity {
  val module: ModuleEntity

  val inheritedCompilerOutput: Boolean
  val excludeOutput: Boolean
  val compilerOutput: VirtualFileUrl?
  val compilerOutputForTests: VirtualFileUrl?
  val languageLevelId: @NonNls String?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : JavaModuleSettingsEntity, WorkspaceEntity.Builder<JavaModuleSettingsEntity>, ObjBuilder<JavaModuleSettingsEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var inheritedCompilerOutput: Boolean
    override var excludeOutput: Boolean
    override var compilerOutput: VirtualFileUrl?
    override var compilerOutputForTests: VirtualFileUrl?
    override var languageLevelId: String?
  }

  companion object : Type<JavaModuleSettingsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(inheritedCompilerOutput: Boolean,
                        excludeOutput: Boolean,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): JavaModuleSettingsEntity {
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

val ModuleEntity.javaSettings: @Child JavaModuleSettingsEntity?
  by WorkspaceEntity.extension()

//region generated code
var ModuleEntity.Builder.javaSettings: @Child JavaModuleSettingsEntity?
  by WorkspaceEntity.extension()

fun MutableEntityStorage.modifyEntity(entity: JavaModuleSettingsEntity,
                                      modification: JavaModuleSettingsEntity.Builder.() -> Unit) = modifyEntity(
  JavaModuleSettingsEntity.Builder::class.java, entity, modification)
//endregion
