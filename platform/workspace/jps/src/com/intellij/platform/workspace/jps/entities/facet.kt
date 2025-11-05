// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.NonNls

data class FacetEntityTypeId(val name: @NonNls String)

/**
 * Describes a [Facet][com.intellij.facet.Facet].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface FacetEntity : ModuleSettingsFacetBridgeEntity {
  override val symbolicId: FacetId
    get() = FacetId(name, typeId, moduleId)
  val typeId: FacetEntityTypeId
  val configurationXmlTag: @NonNls String?

  @Parent
  val module: ModuleEntity

  // underlyingFacet is a parent facet!!
  @Parent
  val underlyingFacet: FacetEntity?

  //region generated code
  @Deprecated(message = "Use FacetEntityBuilder instead")
  interface Builder : FacetEntityBuilder {
    @Deprecated(message = "Use new API instead")
    fun getModule(): ModuleEntity.Builder = module as ModuleEntity.Builder

    @Deprecated(message = "Use new API instead")
    fun setModule(value: ModuleEntity.Builder) {
      module = value
    }

    @Deprecated(message = "Use new API instead")
    fun getUnderlyingFacet(): FacetEntity.Builder? = underlyingFacet as FacetEntity.Builder?

    @Deprecated(message = "Use new API instead")
    fun setUnderlyingFacet(value: FacetEntity.Builder?) {
      underlyingFacet = value
    }
  }

  companion object : EntityType<FacetEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      moduleId: ModuleId,
      name: String,
      typeId: FacetEntityTypeId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = FacetEntityType.compatibilityInvoke(moduleId, name, typeId, entitySource, init)

    //region compatibility generated code
    @Deprecated(
      message = "This method is deprecated and will be removed in next major release",
      replaceWith = ReplaceWith("invoke(moduleId, name, typeId, entitySource, init)"),
    )
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    fun create(
      name: String,
      moduleId: ModuleId,
      typeId: FacetEntityTypeId,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = invoke(moduleId, name, typeId, entitySource, init)
    //endregion compatibility generated code
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyFacetEntity(
  entity: FacetEntity,
  modification: FacetEntity.Builder.() -> Unit,
): FacetEntity {
  return modifyEntity(FacetEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
var FacetEntity.Builder.childrenFacets: List<FacetEntity.Builder>
  get() = (this as FacetEntityBuilder).childrenFacets as List<FacetEntity.Builder>
  set(value) {
    (this as FacetEntityBuilder).childrenFacets = value
  }
//endregion

val FacetEntity.childrenFacets: List<FacetEntity>
    by WorkspaceEntity.extension()