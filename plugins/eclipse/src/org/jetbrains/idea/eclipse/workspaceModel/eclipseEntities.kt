// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.config

import com.intellij.workspaceModel.ide.JpsFileDependentEntitySource
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
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
  @GeneratedCodeApiVersion(1)
  interface Builder : EclipseProjectPropertiesEntity, WorkspaceEntity.Builder<EclipseProjectPropertiesEntity>, ObjBuilder<EclipseProjectPropertiesEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var variablePaths: Map<String, String>
    override var eclipseUrls: MutableList<VirtualFileUrl>
    override var unknownCons: MutableList<String>
    override var knownCons: MutableList<String>
    override var forceConfigureJdk: Boolean
    override var expectedModuleSourcePlace: Int
    override var srcPlace: Map<String, Int>
  }

  companion object : Type<EclipseProjectPropertiesEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(variablePaths: Map<String, String>,
                        eclipseUrls: List<VirtualFileUrl>,
                        unknownCons: List<String>,
                        knownCons: List<String>,
                        forceConfigureJdk: Boolean,
                        expectedModuleSourcePlace: Int,
                        srcPlace: Map<String, Int>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): EclipseProjectPropertiesEntity {
      val builder = builder()
      builder.variablePaths = variablePaths
      builder.eclipseUrls = eclipseUrls.toMutableWorkspaceList()
      builder.unknownCons = unknownCons.toMutableWorkspaceList()
      builder.knownCons = knownCons.toMutableWorkspaceList()
      builder.forceConfigureJdk = forceConfigureJdk
      builder.expectedModuleSourcePlace = expectedModuleSourcePlace
      builder.srcPlace = srcPlace
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: EclipseProjectPropertiesEntity,
                                      modification: EclipseProjectPropertiesEntity.Builder.() -> Unit) = modifyEntity(
  EclipseProjectPropertiesEntity.Builder::class.java, entity, modification)

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
  val entity = EclipseProjectPropertiesEntity(LinkedHashMap(), ArrayList(), ArrayList(), ArrayList(), false, 0, LinkedHashMap(), source) {
    this.module = module
}
  this.addEntity(entity)
  return entity
}


fun EclipseProjectPropertiesEntity.Builder.setVariable(kind: String, name: String, path: String) {
  variablePaths = variablePaths.toMutableMap().also { it[kind + path] = name }
}

fun EclipseProjectPropertiesEntity.getVariable(kind: String, path: String): String? = variablePaths[kind + path]