// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleEntityAndExtensions")

package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
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

  val contentRoots: List<ContentRootEntity>
  val facets: List<FacetEntity>

  //region generated code
  @Deprecated(message = "Use ModifiableModuleEntity instead")
  interface Builder : ModifiableModuleEntity
  companion object : EntityType<ModuleEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      dependencies: List<ModuleDependencyItem>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ModuleEntityType.compatibilityInvoke(name, dependencies, entitySource, init)
  }
  //endregion

}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyModuleEntity(
  entity: ModuleEntity,
  modification: ModuleEntity.Builder.() -> Unit,
): ModuleEntity {
  return modifyEntity(ModuleEntity.Builder::class.java, entity, modification)
}

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.customImlData: ModuleCustomImlDataEntity.Builder?
  get() = (this as ModifiableModuleEntity).customImlData as ModuleCustomImlDataEntity.Builder?
  set(value) {
    (this as ModifiableModuleEntity).customImlData = value
  }

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.exModuleOptions: ExternalSystemModuleOptionsEntity.Builder?
  get() = (this as ModifiableModuleEntity).exModuleOptions as ExternalSystemModuleOptionsEntity.Builder?
  set(value) {
    (this as ModifiableModuleEntity).exModuleOptions = value
  }

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.facetOrder: FacetsOrderEntity.Builder?
  get() = (this as ModifiableModuleEntity).facetOrder as FacetsOrderEntity.Builder?
  set(value) {
    (this as ModifiableModuleEntity).facetOrder = value
  }

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.groupPath: ModuleGroupPathEntity.Builder?
  get() = (this as ModifiableModuleEntity).groupPath as ModuleGroupPathEntity.Builder?
  set(value) {
    (this as ModifiableModuleEntity).groupPath = value
  }

@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.sourceRoots: List<SourceRootEntity.Builder>
  get() = (this as ModifiableModuleEntity).sourceRoots as List<SourceRootEntity.Builder>
  set(value) {
    (this as ModifiableModuleEntity).sourceRoots = value
  }

@get:Internal
@set:Internal
@Deprecated(message = "Use new API instead")
var ModuleEntity.Builder.testProperties: TestModulePropertiesEntity.Builder?
  get() = (this as ModifiableModuleEntity).testProperties as TestModulePropertiesEntity.Builder?
  set(value) {
    (this as ModifiableModuleEntity).testProperties = value
  }
//endregion
