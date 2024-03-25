// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleEntityAndExtensions")

package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.annotations.NonNls

data class ModuleTypeId(val name: @NonNls String)

/**
 * Describes configuration of a [Module][com.intellij.openapi.module.Module].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface ModuleEntity : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String

  val type: ModuleTypeId?
  val dependencies: List<ModuleDependencyItem>

  val contentRoots: List<@Child ContentRootEntity>
  val facets: List<@Child FacetEntity>

  override val symbolicId: ModuleId
    get() = ModuleId(name)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ModuleEntity, WorkspaceEntity.Builder<ModuleEntity> {
    override var entitySource: EntitySource
    override var name: String
    override var type: ModuleTypeId?
    override var dependencies: MutableList<ModuleDependencyItem>
    override var contentRoots: List<ContentRootEntity>
    override var facets: List<FacetEntity>
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
    ): ModuleEntity {
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
fun MutableEntityStorage.modifyEntity(
  entity: ModuleEntity,
  modification: ModuleEntity.Builder.() -> Unit,
): ModuleEntity {
  return modifyEntity(ModuleEntity.Builder::class.java, entity, modification)
}

var ModuleEntity.Builder.customImlData: @Child ModuleCustomImlDataEntity?
  by WorkspaceEntity.extension()
var ModuleEntity.Builder.exModuleOptions: @Child ExternalSystemModuleOptionsEntity?
  by WorkspaceEntity.extension()
var ModuleEntity.Builder.facetOrder: @Child FacetsOrderEntity?
  by WorkspaceEntity.extension()
var ModuleEntity.Builder.groupPath: @Child ModuleGroupPathEntity?
  by WorkspaceEntity.extension()
var ModuleEntity.Builder.sourceRoots: List<SourceRootEntity>
  by WorkspaceEntity.extension()
var ModuleEntity.Builder.testProperties: @Child TestModulePropertiesEntity?
  by WorkspaceEntity.extension()
//endregion
