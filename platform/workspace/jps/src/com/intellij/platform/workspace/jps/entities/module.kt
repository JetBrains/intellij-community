// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleEntityAndExtensions")

package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

data class ModuleTypeId(val name: @NonNls String)

/**
 * Describes configuration of a [Module][com.intellij.openapi.module.Module].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface ModuleEntity : WorkspaceEntityWithSymbolicId {
  override val symbolicId: ModuleId
    get() = ModuleId(name)

  val name: @NlsSafe String
  val type: ModuleTypeId?
  val dependencies: List<ModuleDependencyItem>

  val contentRoots: List<@Child ContentRootEntity>
  val facets: List<@Child FacetEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleEntity> {
    override var entitySource: EntitySource
    var name: String
    var type: ModuleTypeId?
    var dependencies: MutableList<ModuleDependencyItem>
    var contentRoots: List<ContentRootEntity.Builder>
    var facets: List<FacetEntity.Builder>
  }

  companion object : EntityType<ModuleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      dependencies: List<ModuleDependencyItem>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.dependencies = dependencies.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyModuleEntity(
  entity: ModuleEntity,
  modification: ModuleEntity.Builder.() -> Unit,
): ModuleEntity {
  return modifyEntity(ModuleEntity.Builder::class.java, entity, modification)
}

@get:Internal
@set:Internal
var ModuleEntity.Builder.customImlData: @Child ModuleCustomImlDataEntity.Builder?
  by WorkspaceEntity.extensionBuilder(ModuleCustomImlDataEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntity.Builder.exModuleOptions: @Child ExternalSystemModuleOptionsEntity.Builder?
  by WorkspaceEntity.extensionBuilder(ExternalSystemModuleOptionsEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntity.Builder.facetOrder: @Child FacetsOrderEntity.Builder?
  by WorkspaceEntity.extensionBuilder(FacetsOrderEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntity.Builder.groupPath: @Child ModuleGroupPathEntity.Builder?
  by WorkspaceEntity.extensionBuilder(ModuleGroupPathEntity::class.java)
var ModuleEntity.Builder.sourceRoots: List<SourceRootEntity.Builder>
  by WorkspaceEntity.extensionBuilder(SourceRootEntity::class.java)

@get:Internal
@set:Internal
var ModuleEntity.Builder.testProperties: @Child TestModulePropertiesEntity.Builder?
  by WorkspaceEntity.extensionBuilder(TestModulePropertiesEntity::class.java)
//endregion
