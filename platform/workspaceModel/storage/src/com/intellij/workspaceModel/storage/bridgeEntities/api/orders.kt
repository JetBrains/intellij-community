// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.annotations.Child
import org.jetbrains.deft.Type


/**
 * This entity stores order of facets in iml file. This is needed to ensure that facet tags are saved in the same order to avoid
 * unnecessary modifications of iml file.
 */
interface FacetsOrderEntity : WorkspaceEntity {
  val orderOfFacets: List<String>
  val moduleEntity: ModuleEntity

  //region generated code
  //@formatter:off
  interface Builder: FacetsOrderEntity, ModifiableWorkspaceEntity<FacetsOrderEntity>, ObjBuilder<FacetsOrderEntity> {
      override var orderOfFacets: List<String>
      override var entitySource: EntitySource
      override var moduleEntity: ModuleEntity
  }
  
  companion object: Type<FacetsOrderEntity, Builder>(53)
  //@formatter:on
  //endregion

}

val ModuleEntity.facetOrder: @Child FacetsOrderEntity?
  get() = referrersx(FacetsOrderEntity::moduleEntity).singleOrNull()

/**
 * This property indicates that external-system-id attribute should be stored in facet configuration to avoid unnecessary modifications
 */
interface FacetExternalSystemIdEntity : WorkspaceEntity {
  val externalSystemId: String
  val facet: FacetEntity

  //region generated code
  //@formatter:off
  interface Builder: FacetExternalSystemIdEntity, ModifiableWorkspaceEntity<FacetExternalSystemIdEntity>, ObjBuilder<FacetExternalSystemIdEntity> {
      override var externalSystemId: String
      override var entitySource: EntitySource
      override var facet: FacetEntity
  }
  
  companion object: Type<FacetExternalSystemIdEntity, Builder>(54)
  //@formatter:on
  //endregion

}

val FacetEntity.facetExternalSystemIdEntity: @Child FacetExternalSystemIdEntity?
  get() = referrersx(FacetExternalSystemIdEntity::facet).singleOrNull()

/**
 * This property indicates that external-system-id attribute should be stored in artifact configuration file to avoid unnecessary modifications
 */
interface ArtifactExternalSystemIdEntity : WorkspaceEntity {
  val externalSystemId: String
  val artifactEntity: ArtifactEntity
  //region generated code
  //@formatter:off
  interface Builder: ArtifactExternalSystemIdEntity, ModifiableWorkspaceEntity<ArtifactExternalSystemIdEntity>, ObjBuilder<ArtifactExternalSystemIdEntity> {
      override var externalSystemId: String
      override var entitySource: EntitySource
      override var artifactEntity: ArtifactEntity
  }
  
  companion object: Type<ArtifactExternalSystemIdEntity, Builder>(55)
  //@formatter:on
  //endregion

}

val ArtifactEntity.artifactExternalSystemIdEntity: @Child ArtifactExternalSystemIdEntity?
  get() = referrersx(ArtifactExternalSystemIdEntity::artifactEntity).singleOrNull()

/**
 * This property indicates that external-system-id attribute should be stored in library configuration file to avoid unnecessary modifications
 */
interface LibraryExternalSystemIdEntity: WorkspaceEntity {
  val externalSystemId: String
  val library: LibraryEntity
  //region generated code
  //@formatter:off
  interface Builder: LibraryExternalSystemIdEntity, ModifiableWorkspaceEntity<LibraryExternalSystemIdEntity>, ObjBuilder<LibraryExternalSystemIdEntity> {
      override var externalSystemId: String
      override var entitySource: EntitySource
      override var library: LibraryEntity
  }
  
  companion object: Type<LibraryExternalSystemIdEntity, Builder>(56)
  //@formatter:on
  //endregion

}

val LibraryEntity.externalSystemId: @Child LibraryExternalSystemIdEntity?
  get() = referrersx(LibraryExternalSystemIdEntity::library).singleOrNull()
