// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.config

import com.intellij.workspaceModel.ide.JpsFileDependentEntitySource
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity


/**
 * Stores data from [EclipseModuleManagerImpl] in workspace model
 */
interface EclipseProjectPropertiesEntity : WorkspaceEntity {
  val module: ModuleEntity

  val variablePaths: Map<String, String>

  // This should be a set
  val eclipseUrls: List<VirtualFileUrl>

  // This should be a set
  val unknownCons: List<String>

  // This should be a set
  val knownCons: List<String>
  val forceConfigureJdk: Boolean
  val expectedModuleSourcePlace: Int
  val srcPlace: Map<String, Int>

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(1)
  interface Builder: EclipseProjectPropertiesEntity, ModifiableWorkspaceEntity<EclipseProjectPropertiesEntity>, ObjBuilder<EclipseProjectPropertiesEntity> {
      override var module: ModuleEntity
      override var entitySource: EntitySource
      override var variablePaths: Map<String, String>
      override var eclipseUrls: List<VirtualFileUrl>
      override var unknownCons: List<String>
      override var knownCons: List<String>
      override var forceConfigureJdk: Boolean
      override var expectedModuleSourcePlace: Int
      override var srcPlace: Map<String, Int>
  }
  
  companion object: Type<EclipseProjectPropertiesEntity, Builder>() {
      operator fun invoke(entitySource: EntitySource, variablePaths: Map<String, String>, eclipseUrls: List<VirtualFileUrl>, unknownCons: List<String>, knownCons: List<String>, forceConfigureJdk: Boolean, expectedModuleSourcePlace: Int, srcPlace: Map<String, Int>, init: (Builder.() -> Unit)? = null): EclipseProjectPropertiesEntity {
          val builder = builder()
          builder.entitySource = entitySource
          builder.variablePaths = variablePaths
          builder.eclipseUrls = eclipseUrls
          builder.unknownCons = unknownCons
          builder.knownCons = knownCons
          builder.forceConfigureJdk = forceConfigureJdk
          builder.expectedModuleSourcePlace = expectedModuleSourcePlace
          builder.srcPlace = srcPlace
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: EclipseProjectPropertiesEntity, modification: EclipseProjectPropertiesEntity.Builder.() -> Unit) = modifyEntity(EclipseProjectPropertiesEntity.Builder::class.java, entity, modification)
var ModuleEntity.Builder.eclipseProperties: @Child EclipseProjectPropertiesEntity?
    by WorkspaceEntity.extension()

//endregion

val ModuleEntity.eclipseProperties: @Child EclipseProjectPropertiesEntity?
    by WorkspaceEntity.extension()

data class EclipseProjectFile(
  val classpathFile: VirtualFileUrl,
  val internalSource: JpsFileEntitySource
) : EntitySource, JpsFileDependentEntitySource {
  override val originalSource: JpsFileEntitySource
    get() = internalSource
}

fun MutableEntityStorage.addEclipseProjectPropertiesEntity(module: ModuleEntity, source: EntitySource): EclipseProjectPropertiesEntity {
  val entity = EclipseProjectPropertiesEntity(source, LinkedHashMap(), ArrayList(), ArrayList(), ArrayList(), false, 0, LinkedHashMap()) {
    this.module = module
}
  this.addEntity(entity)
  return entity
}


fun EclipseProjectPropertiesEntity.Builder.setVariable(kind: String, name: String, path: String) {
  variablePaths = variablePaths.toMutableMap().also { it[kind + path] = name }
}

fun EclipseProjectPropertiesEntity.getVariable(kind: String, path: String): String? = variablePaths[kind + path]