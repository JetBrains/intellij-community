// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.config

import com.intellij.platform.workspace.jps.JpsFileDependentEntitySource
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity


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
  @GeneratedCodeApiVersion(2)
  interface Builder : EclipseProjectPropertiesEntity, WorkspaceEntity.Builder<EclipseProjectPropertiesEntity> {
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

  companion object : EntityType<EclipseProjectPropertiesEntity, Builder>() {
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

  internal val projectLocation: JpsProjectConfigLocation
    get() = (internalSource as JpsProjectFileEntitySource).projectLocation
}


fun EclipseProjectPropertiesEntity.Builder.setVariable(kind: String, name: String, path: String) {
  variablePaths = variablePaths.toMutableMap().also { it[kind + path] = name }
}

fun EclipseProjectPropertiesEntity.getVariable(kind: String, path: String): String? = variablePaths[kind + path]