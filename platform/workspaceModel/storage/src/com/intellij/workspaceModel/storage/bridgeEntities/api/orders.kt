// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage



/**
 * This entity stores order of facets in iml file. This is needed to ensure that facet tags are saved in the same order to avoid
 * unnecessary modifications of iml file.
 */
interface FacetsOrderEntity : WorkspaceEntity {
  val orderOfFacets: List<String>
  val moduleEntity: ModuleEntity

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: FacetsOrderEntity, ModifiableWorkspaceEntity<FacetsOrderEntity>, ObjBuilder<FacetsOrderEntity> {
      override var orderOfFacets: List<String>
      override var entitySource: EntitySource
      override var moduleEntity: ModuleEntity
  }
  
  companion object: Type<FacetsOrderEntity, Builder>() {
      operator fun invoke(orderOfFacets: List<String>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FacetsOrderEntity {
          val builder = builder()
          builder.orderOfFacets = orderOfFacets
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: FacetsOrderEntity, modification: FacetsOrderEntity.Builder.() -> Unit) = modifyEntity(FacetsOrderEntity.Builder::class.java, entity, modification)
//endregion

val ModuleEntity.facetOrder: @Child FacetsOrderEntity?
    by WorkspaceEntity.extension()

/**
 * This property indicates that external-system-id attribute should be stored in facet configuration to avoid unnecessary modifications
 */
interface FacetExternalSystemIdEntity : WorkspaceEntity {
  val externalSystemId: String
  val facet: FacetEntity

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: FacetExternalSystemIdEntity, ModifiableWorkspaceEntity<FacetExternalSystemIdEntity>, ObjBuilder<FacetExternalSystemIdEntity> {
      override var externalSystemId: String
      override var entitySource: EntitySource
      override var facet: FacetEntity
  }
  
  companion object: Type<FacetExternalSystemIdEntity, Builder>() {
      operator fun invoke(externalSystemId: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): FacetExternalSystemIdEntity {
          val builder = builder()
          builder.externalSystemId = externalSystemId
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: FacetExternalSystemIdEntity, modification: FacetExternalSystemIdEntity.Builder.() -> Unit) = modifyEntity(FacetExternalSystemIdEntity.Builder::class.java, entity, modification)
//endregion

val FacetEntity.facetExternalSystemIdEntity: @Child FacetExternalSystemIdEntity?
    by WorkspaceEntity.extension()

/**
 * This property indicates that external-system-id attribute should be stored in artifact configuration file to avoid unnecessary modifications
 */
interface ArtifactExternalSystemIdEntity : WorkspaceEntity {
  val externalSystemId: String
  val artifactEntity: ArtifactEntity
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: ArtifactExternalSystemIdEntity, ModifiableWorkspaceEntity<ArtifactExternalSystemIdEntity>, ObjBuilder<ArtifactExternalSystemIdEntity> {
      override var externalSystemId: String
      override var entitySource: EntitySource
      override var artifactEntity: ArtifactEntity
  }
  
  companion object: Type<ArtifactExternalSystemIdEntity, Builder>() {
      operator fun invoke(externalSystemId: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ArtifactExternalSystemIdEntity {
          val builder = builder()
          builder.externalSystemId = externalSystemId
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactExternalSystemIdEntity, modification: ArtifactExternalSystemIdEntity.Builder.() -> Unit) = modifyEntity(ArtifactExternalSystemIdEntity.Builder::class.java, entity, modification)
//endregion

val ArtifactEntity.artifactExternalSystemIdEntity: @Child ArtifactExternalSystemIdEntity?
    by WorkspaceEntity.extension()

/**
 * This property indicates that external-system-id attribute should be stored in library configuration file to avoid unnecessary modifications
 */
interface LibraryExternalSystemIdEntity: WorkspaceEntity {
  val externalSystemId: String
  val library: LibraryEntity
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: LibraryExternalSystemIdEntity, ModifiableWorkspaceEntity<LibraryExternalSystemIdEntity>, ObjBuilder<LibraryExternalSystemIdEntity> {
      override var externalSystemId: String
      override var entitySource: EntitySource
      override var library: LibraryEntity
  }
  
  companion object: Type<LibraryExternalSystemIdEntity, Builder>() {
      operator fun invoke(externalSystemId: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): LibraryExternalSystemIdEntity {
          val builder = builder()
          builder.externalSystemId = externalSystemId
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: LibraryExternalSystemIdEntity, modification: LibraryExternalSystemIdEntity.Builder.() -> Unit) = modifyEntity(LibraryExternalSystemIdEntity.Builder::class.java, entity, modification)
//endregion

val LibraryEntity.externalSystemId: @Child LibraryExternalSystemIdEntity?
    by WorkspaceEntity.extension()

/**
 * This entity stores order of artifacts in ipr file. This is needed to ensure that artifact tags are saved in the same order to avoid
 * unnecessary modifications of ipr file.
 */
interface ArtifactsOrderEntity : WorkspaceEntity {
  val orderOfArtifacts: List<String>
  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: ArtifactsOrderEntity, ModifiableWorkspaceEntity<ArtifactsOrderEntity>, ObjBuilder<ArtifactsOrderEntity> {
      override var orderOfArtifacts: List<String>
      override var entitySource: EntitySource
  }
  
  companion object: Type<ArtifactsOrderEntity, Builder>() {
      operator fun invoke(orderOfArtifacts: List<String>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ArtifactsOrderEntity {
          val builder = builder()
          builder.orderOfArtifacts = orderOfArtifacts
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: ArtifactsOrderEntity, modification: ArtifactsOrderEntity.Builder.() -> Unit) = modifyEntity(ArtifactsOrderEntity.Builder::class.java, entity, modification)
//endregion
